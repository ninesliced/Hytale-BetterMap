(function () {
	'use strict';

	const CHUNK_SIZE = 32;
	const TILE_SIZE = 256;
	const BLOCK_TO_MAP_SCALE = TILE_SIZE / CHUNK_SIZE;
	const DEFAULT_WORLD = 'world';
	const GLOBAL_TILE_CACHE = new Map();

	L.TileLayer.Batch = L.TileLayer.extend({
		options: {
			batchDelay: 130,
			batchDelayNegative: 340,
			maxBatchSize: 2500,
			batchEndpoint: '/api/tiles/batch',
			memoryCacheMaxEntries: 120000
		},

		initialize: function (urlTemplate, options) {
			L.TileLayer.prototype.initialize.call(this, urlTemplate, options);
			this._pendingTiles = new Map();
			this._queuedWhileSending = new Map();
			this._batchTimer = null;
			this._abortController = null;
			this._isSending = false;
			this._isZooming = false;
			this._worldName = DEFAULT_WORLD;
			this._quality = 'medium';
			this._mode = 'global';
			this._playerUuid = '';
			this._emptyTile = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';
			this._tileMemoryCache = GLOBAL_TILE_CACHE;
		},

		setContext: function (worldName, quality, mode, playerUuid) {
			this._worldName = worldName;
			this._quality = quality;
			this._mode = mode || 'global';
			this._playerUuid = playerUuid || '';
		},

		onAdd: function (map) {
			L.TileLayer.prototype.onAdd.call(this, map);
			map.on('zoomstart', this._onZoomStart, this);
			map.on('zoomend', this._onZoomEnd, this);
		},

		onRemove: function (map) {
			map.off('zoomstart', this._onZoomStart, this);
			map.off('zoomend', this._onZoomEnd, this);
			this._cancelAll();
			L.TileLayer.prototype.onRemove.call(this, map);
		},

		createTile: function (coords, done) {
			const tile = document.createElement('img');
			tile.alt = '';
			tile.setAttribute('role', 'presentation');
			const zoom = Math.min(coords.z, 0);
			const key = `${zoom}/${coords.x}/${coords.y}`;
			const cacheKey = this._cacheKey(zoom, coords.x, coords.y);
			const cached = this._tileMemoryCache.get(cacheKey);
			if (cached) {
				this._tileMemoryCache.delete(cacheKey);
				this._tileMemoryCache.set(cacheKey, cached);
				this._applyTilePayload(tile, done, cached);
				return tile;
			}
			this._queue(key, coords, tile, done);
			return tile;
		},

		_cacheKey: function (z, x, y) {
			const scopedPlayer = this._mode === 'player' ? (this._playerUuid || '') : '';
			return `${this._worldName}|${this._quality}|${this._mode}|${scopedPlayer}|${z}/${x}/${y}`;
		},

		_base64ToObjectUrl: function (base64) {
			const binary = atob(base64);
			const bytes = new Uint8Array(binary.length);
			for (let i = 0; i < binary.length; i++) {
				bytes[i] = binary.charCodeAt(i);
			}
			return URL.createObjectURL(new Blob([bytes], { type: 'image/png' }));
		},

		_bytesToObjectUrl: function (bytes) {
			return URL.createObjectURL(new Blob([bytes], { type: 'image/png' }));
		},

		_applyTilePayload: function (tile, done, payload) {
			const src = payload.empty ? this._emptyTile : payload.src;
			let completed = false;
			const complete = error => {
				if (completed) {
					return;
				}
				completed = true;
				tile.onload = null;
				tile.onerror = null;
				done(error || null, tile);
			};

			tile.onload = () => complete(null);
			tile.onerror = () => complete(new Error('Tile decode failed'));
			tile.src = src;

			// Fast path: blob URLs and data URIs may already be decoded
			if (tile.complete && tile.naturalWidth > 0) {
				complete(null);
			}
		},

		_storeTilePayload: function (z, x, y, payload) {
			const cacheKey = this._cacheKey(z, x, y);
			this._tileMemoryCache.set(cacheKey, payload);
			while (this._tileMemoryCache.size > this.options.memoryCacheMaxEntries) {
				const oldestKey = this._tileMemoryCache.keys().next().value;
				if (!oldestKey) {
					break;
				}
				const evicted = this._tileMemoryCache.get(oldestKey);
				if (evicted && evicted.objectUrl) {
					URL.revokeObjectURL(evicted.src);
				}
				this._tileMemoryCache.delete(oldestKey);
			}
		},

		_queue: function (key, coords, tile, done) {
			const target = this._isSending ? this._queuedWhileSending : this._pendingTiles;
			target.set(key, { coords, tile, done });

			if (this._isZooming) {
				return;
			}
			if (this._batchTimer) {
				clearTimeout(this._batchTimer);
			}
			if (!this._isSending && this._pendingTiles.size >= this.options.maxBatchSize) {
				this._sendBatch();
			} else if (!this._isSending) {
				this._batchTimer = setTimeout(() => this._sendBatch(), this._delay());
			}
		},

		_onZoomStart: function () {
			this._isZooming = true;
			if (this._batchTimer) {
				clearTimeout(this._batchTimer);
				this._batchTimer = null;
			}
			if (this.options.keepOffscreenBatchOnZoom) {
				for (const [key, value] of this._pendingTiles) {
					this._queuedWhileSending.set(key, value);
				}
				this._pendingTiles.clear();
			}
		},

		_onZoomEnd: function () {
			setTimeout(() => {
				this._isZooming = false;

				// Discard queued tiles whose DOM elements were removed during zoom
				for (const [key, request] of this._queuedWhileSending) {
					if (!request.tile.isConnected) {
						this._queuedWhileSending.delete(key);
					}
				}

				// Merge surviving queued tiles back into pending
				if (this._queuedWhileSending.size > 0 && !this._isSending) {
					for (const [key, value] of this._queuedWhileSending) {
						this._pendingTiles.set(key, value);
					}
					this._queuedWhileSending.clear();
				}

				if (this._pendingTiles.size > 0 && !this._isSending) {
					this._batchTimer = setTimeout(() => this._sendBatch(), this._delay());
				}
			}, 80);
		},

		_delay: function () {
			const zoom = this._map ? Math.floor(this._map.getZoom()) : 0;
			return zoom < 0 ? this.options.batchDelayNegative : this.options.batchDelay;
		},

		_cancelAll: function () {
			if (this._abortController) {
				this._abortController.abort();
				this._abortController = null;
			}
			if (this._batchTimer) {
				clearTimeout(this._batchTimer);
				this._batchTimer = null;
			}
			this._pendingTiles.clear();
			this._queuedWhileSending.clear();
			this._isSending = false;
		},

		_sendBatch: function () {
			// Prune tiles whose img elements are no longer in the document
			for (const [key, request] of this._pendingTiles) {
				if (!request.tile.isConnected) {
					this._pendingTiles.delete(key);
				}
			}

			if (this._pendingTiles.size === 0 || this._isZooming) {
				return;
			}

			this._isSending = true;
			this._abortController = new AbortController();
			const signal = this._abortController.signal;

			const toSend = new Map(this._pendingTiles);
			this._pendingTiles.clear();
			this._batchTimer = null;

			const chunks = [];
			let current = new Map();
			for (const [key, value] of toSend) {
				current.set(key, value);
				if (current.size >= 300) {
					chunks.push(current);
					current = new Map();
				}
			}
			if (current.size > 0) {
				chunks.push(current);
			}

			Promise.all(chunks.map(chunk => this._sendChunk(chunk, signal))).finally(() => {
				this._abortController = null;
				this._isSending = false;
				if (this._queuedWhileSending.size > 0) {
					for (const [key, value] of this._queuedWhileSending) {
						this._pendingTiles.set(key, value);
					}
					this._queuedWhileSending.clear();
					if (!this._isZooming) {
						this._batchTimer = setTimeout(() => this._sendBatch(), this._delay());
					}
				}
			});
		},

		_sendChunk: function (chunk, signal) {
			const tiles = [];
			for (const [key, request] of [...chunk]) {
				// Skip tiles no longer attached to the document
				if (!request.tile.isConnected) {
					chunk.delete(key);
					continue;
				}
				const [z, x, y] = key.split('/').map(Number);
				const cacheKey = this._cacheKey(z, x, y);
				const cached = this._tileMemoryCache.get(cacheKey);
				if (cached) {
					this._tileMemoryCache.delete(cacheKey);
					this._tileMemoryCache.set(cacheKey, cached);
					this._applyTilePayload(request.tile, request.done, cached);
					chunk.delete(key);
					continue;
				}
				tiles.push({ z, x, y });
			}

			if (chunk.size === 0) {
				return Promise.resolve();
			}

			return fetch(this.options.batchEndpoint, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					world: this._worldName,
					quality: this._quality,
					mode: this._mode,
					playerUuid: this._playerUuid,
					tiles
				}),
				signal
			})
				.then(res => {
					if (!res.ok) {
						throw new Error(`HTTP ${res.status}`);
					}
					const contentType = (res.headers.get('content-type') || '').toLowerCase();
					if (contentType.includes('application/octet-stream')) {
						return res.arrayBuffer().then(buffer => ({ type: 'binary', buffer }));
					}
					return res.json().then(json => ({ type: 'json', json }));
				})
				.then(result => {
					if (result.type === 'binary') {
						const view = new DataView(result.buffer);
						let offset = 0;
						const version = view.getUint8(offset);
						offset += 1;
						if (version !== 1) {
							throw new Error(`Unsupported batch version ${version}`);
						}
						const tileCount = view.getInt32(offset, false);
						offset += 4;

						for (let i = 0; i < tileCount; i++) {
							const z = view.getInt32(offset, false);
							offset += 4;
							const x = view.getInt32(offset, false);
							offset += 4;
							const y = view.getInt32(offset, false);
							offset += 4;
							const dataLength = view.getInt32(offset, false);
							offset += 4;

							const key = `${z}/${x}/${y}`;
							const request = chunk.get(key);
							if (!request) {
								if (dataLength > 0) {
									offset += dataLength;
								}
								continue;
							}

							if (dataLength <= 0) {
								const payload = { empty: true, src: this._emptyTile };
								this._storeTilePayload(z, x, y, payload);
								this._applyTilePayload(request.tile, request.done, payload);
								chunk.delete(key);
								continue;
							}

							const bytes = new Uint8Array(result.buffer, offset, dataLength);
							offset += dataLength;
							const payload = { empty: false, src: this._bytesToObjectUrl(bytes), objectUrl: true };
							this._storeTilePayload(z, x, y, payload);
							this._applyTilePayload(request.tile, request.done, payload);
							chunk.delete(key);
						}
					} else {
						const data = result.json || {};
						for (const [key, tileData] of Object.entries(data.tiles || {})) {
							const request = chunk.get(key);
							if (!request) {
								continue;
							}
							const [z, x, y] = key.split('/').map(Number);
							if (tileData.empty) {
								const payload = { empty: true, src: this._emptyTile };
								this._storeTilePayload(z, x, y, payload);
								this._applyTilePayload(request.tile, request.done, payload);
								chunk.delete(key);
								continue;
							}
							if (!tileData.data) {
								request.done(new Error('Missing tile payload'), request.tile);
								chunk.delete(key);
								continue;
							}
							const payload = { empty: false, src: this._base64ToObjectUrl(tileData.data), objectUrl: true };
							this._storeTilePayload(z, x, y, payload);
							this._applyTilePayload(request.tile, request.done, payload);
							chunk.delete(key);
						}
					}

					for (const [, request] of chunk) {
						request.done(new Error('Tile payload missing from batch response'), request.tile);
					}
				})
				.catch(error => {
					if (error.name === 'AbortError') {
						return;
					}
					for (const [, request] of chunk) {
						request.done(error, request.tile);
					}
				});
		}
	});

	L.tileLayer.batch = function (urlTemplate, options) {
		return new L.TileLayer.Batch(urlTemplate, options);
	};

	const state = {
		map: null,
		tileLayer: null,
		world: DEFAULT_WORLD,
		quality: 'medium',
		viewMode: 'global',
		selectedPlayerUuid: '',
		worlds: [],
		websocket: null,
		reconnectTimer: null,
		lastSnapshot: null,
		playersByUuid: {},
		markersById: {},
		playerMarkers: {},
		worldMarkers: {}
	};

	function tileQuery() {
		const params = new URLSearchParams();
		params.set('mode', state.viewMode);
		if (state.viewMode === 'player' && state.selectedPlayerUuid) {
			params.set('playerUuid', state.selectedPlayerUuid);
		}
		return params.toString();
	}

	function worldToLatLng(x, z) {
		return L.latLng(-z * BLOCK_TO_MAP_SCALE, x * BLOCK_TO_MAP_SCALE);
	}

	function pickColor(seed) {
		let hash = 0;
		for (let i = 0; i < seed.length; i++) {
			hash = seed.charCodeAt(i) + ((hash << 5) - hash);
		}
		const hue = Math.abs(hash) % 360;
		return `hsl(${hue}, 70%, 55%)`;
	}

	function initials(name) {
		if (!name) {
			return '?';
		}
		return name.trim().charAt(0).toUpperCase();
	}

	function markerGlyph(icon) {
		const key = (icon || '').toLowerCase();
		if (key.includes('house') || key.includes('home')) {
			return '⌂';
		}
		if (key.includes('skull') || key.includes('death')) {
			return '✖';
		}
		if (key.includes('flag')) {
			return '⚑';
		}
		if (key.includes('star')) {
			return '★';
		}
		return '◆';
	}

	function createPlayerIcon(player) {
		const yawDeg = yawToDegrees(player.yaw);
		const color = pickColor(player.uuid || player.name || 'p');
		return L.divIcon({
			className: 'player-marker',
			html: `<div class="player-name">${escapeHtml(player.name || 'Player')}</div><div class="heading" style="border-bottom-color:${color};transform: translate(-50%, -50%) rotate(${yawDeg}deg)"></div>`,
			iconSize: [90, 36],
			iconAnchor: [10, 10]
		});
	}

	function yawToDegrees(yawRadians) {
		return -((yawRadians || 0) * (180 / Math.PI));
	}

	function updatePlayerHeading(marker, yawRadians) {
		const el = marker.getElement();
		if (!el) {
			return;
		}
		const heading = el.querySelector('.heading');
		if (heading) {
			const yawDeg = yawToDegrees(yawRadians);
			heading.style.transform = `translate(-50%, -50%) rotate(${yawDeg}deg)`;
		}
	}

	function createWorldMarkerIcon(marker) {
		const glyph = markerGlyph(marker.icon);
		const iconName = encodeURIComponent(marker.icon || 'UserA.png');
		const iconUrl = `/api/icons/marker/${iconName}`;
		return L.divIcon({
			className: 'map-marker',
			html: `<span style="display:flex;width:100%;height:100%;align-items:center;justify-content:center;background-image:url('${iconUrl}');background-size:cover;background-position:center;border-radius:50%">${glyph}</span>`,
			iconSize: [20, 20],
			iconAnchor: [10, 10]
		});
	}

	function updateTileLayer() {
		if (state.tileLayer) {
			state.map.removeLayer(state.tileLayer);
		}
		const query = tileQuery();
		state.tileLayer = L.tileLayer.batch(`/api/tiles/${state.world}/${state.quality}/{z}/{x}/{y}.png?${query}`, {
			tileSize: TILE_SIZE,
			minNativeZoom: -4,
			maxNativeZoom: 0,
			minZoom: -6,
			maxZoom: 4,
			noWrap: true,
			keepBuffer: 4,
			updateWhenZooming: false,
			bounds: [[-120000, -120000], [120000, 120000]],
			batchEndpoint: '/api/tiles/batch',
			keepOffscreenBatchOnZoom: false
		});
		state.tileLayer.setContext(state.world, state.quality, state.viewMode, state.selectedPlayerUuid);
		state.tileLayer.addTo(state.map);
	}

	function clearAllEntityMarkers() {
		Object.values(state.playerMarkers).forEach(marker => state.map.removeLayer(marker));
		Object.values(state.worldMarkers).forEach(marker => state.map.removeLayer(marker));
		state.playerMarkers = {};
		state.worldMarkers = {};
	}

	function renderPlayers(players) {
		const nextByUuid = {};
		const seen = new Set();

		players.forEach(player => {
			seen.add(player.uuid);
			nextByUuid[player.uuid] = player;

			const pos = worldToLatLng(player.x, player.z);
			let marker = state.playerMarkers[player.uuid];
			if (!marker) {
				marker = L.marker(pos, { icon: createPlayerIcon(player) }).addTo(state.map);
				marker.bindTooltip(player.name || 'Player', { direction: 'top', offset: [0, -14] });
				state.playerMarkers[player.uuid] = marker;
			} else {
				marker.setLatLng(pos);
				updatePlayerHeading(marker, player.yaw);
			}
		});

		Object.keys(state.playerMarkers).forEach(uuid => {
			if (!seen.has(uuid)) {
				state.map.removeLayer(state.playerMarkers[uuid]);
				delete state.playerMarkers[uuid];
			}
		});

		state.playersByUuid = nextByUuid;
		document.getElementById('player-count-display').textContent = `Players: ${players.length}`;
		renderPlayerList();
	}

	function renderMarkers(markers) {
		const nextById = {};
		const seen = new Set();

		markers.forEach(markerData => {
			if (!markerData || !markerData.id) {
				return;
			}
			seen.add(markerData.id);
			nextById[markerData.id] = markerData;

			const pos = worldToLatLng(markerData.x, markerData.z);
			let marker = state.worldMarkers[markerData.id];
			if (!marker) {
				marker = L.marker(pos, { icon: createWorldMarkerIcon(markerData) }).addTo(state.map);
				marker.bindTooltip(markerData.name || markerData.id, { direction: 'top', offset: [0, -12] });
				state.worldMarkers[markerData.id] = marker;
			} else {
				marker.setLatLng(pos);
			}
		});

		Object.keys(state.worldMarkers).forEach(id => {
			if (!seen.has(id)) {
				state.map.removeLayer(state.worldMarkers[id]);
				delete state.worldMarkers[id];
			}
		});

		state.markersById = nextById;
		document.getElementById('marker-count-display').textContent = `Markers: ${markers.length}`;
		renderMarkerList();
	}

	function renderPlayerList() {
		const list = document.getElementById('player-list');
		const players = Object.values(state.playersByUuid).sort((a, b) => (a.name || '').localeCompare(b.name || ''));
		if (players.length === 0) {
			list.innerHTML = '<li class="empty">No players online</li>';
			return;
		}
		list.innerHTML = players.map(player => {
			const color = pickColor(player.uuid || player.name || 'p');
			return `<li data-uuid="${escapeHtml(player.uuid)}"><span class="entity-icon" style="background:${color}">${escapeHtml(initials(player.name))}</span><span class="entity-name">${escapeHtml(player.name || 'Unknown')}</span><span class="entity-meta">${Math.round(player.x)}, ${Math.round(player.z)}</span></li>`;
		}).join('');

		list.querySelectorAll('li[data-uuid]').forEach(item => {
			item.addEventListener('click', () => {
				const player = state.playersByUuid[item.dataset.uuid];
				if (!player) {
					return;
				}
				state.map.setView(worldToLatLng(player.x, player.z), Math.max(0, state.map.getZoom()));
			});
		});
	}

	function renderMarkerList() {
		const list = document.getElementById('marker-list');
		const markers = Object.values(state.markersById).sort((a, b) => (a.name || '').localeCompare(b.name || ''));
		if (markers.length === 0) {
			list.innerHTML = '<li class="empty">No markers in this world</li>';
			return;
		}

		list.innerHTML = markers.map(marker => {
			const glyph = markerGlyph(marker.icon);
			const owner = marker.owner ? ` · ${marker.owner}` : '';
			const type = marker.shared ? 'shared' : 'personal';
			return `<li data-id="${escapeHtml(marker.id)}"><span class="entity-icon" style="background:#f59e0b;color:#111827">${glyph}</span><span class="entity-name">${escapeHtml(marker.name || marker.id)}</span><span class="entity-meta">${type}${escapeHtml(owner)}</span></li>`;
		}).join('');

		list.querySelectorAll('li[data-id]').forEach(item => {
			item.addEventListener('click', () => {
				const marker = state.markersById[item.dataset.id];
				if (!marker) {
					return;
				}
				state.map.setView(worldToLatLng(marker.x, marker.z), Math.max(0, state.map.getZoom()));
			});
		});
	}

	function applySnapshot(snapshot) {
		if (!snapshot) {
			return;
		}
		state.lastSnapshot = snapshot;
		if ((state.viewMode !== 'player' && snapshot.defaultMode === 'player') || (state.viewMode === 'global' && !state.selectedPlayerUuid && snapshot.filterPlayerUuid)) {
			state.viewMode = snapshot.defaultMode === 'player' ? 'player' : state.viewMode;
		}
		refreshPlayerFilterOptions(snapshot.players || []);
		applyFilteredSnapshot();
	}

	function applyFilteredSnapshot() {
		if (!state.lastSnapshot) {
			return;
		}
		const players = getFilteredPlayers(state.lastSnapshot.players || []);
		const markers = getFilteredMarkers(state.lastSnapshot.markers || []);
		renderPlayers(players);
		renderMarkers(markers);
	}

	function getFilteredPlayers(players) {
		if (state.viewMode !== 'player' || !state.selectedPlayerUuid) {
			return players;
		}
		return players.filter(player => player.uuid === state.selectedPlayerUuid);
	}

	function getFilteredMarkers(markers) {
		if (state.viewMode !== 'player' || !state.selectedPlayerUuid) {
			return markers;
		}
		return markers.filter(marker => {
			if (marker.shared) {
				return true;
			}
			return marker.ownerUuid === state.selectedPlayerUuid;
		});
	}

	function refreshPlayerFilterOptions(players) {
		const select = document.getElementById('player-select');
		const prev = state.selectedPlayerUuid;
		const sorted = [...players].sort((a, b) => (a.name || '').localeCompare(b.name || ''));
		select.innerHTML = sorted.map(player => `<option value="${escapeHtml(player.uuid)}">${escapeHtml(player.name || player.uuid)}</option>`).join('');

		const available = new Set(sorted.map(player => player.uuid));
		if (!prev || !available.has(prev)) {
			state.selectedPlayerUuid = sorted.length > 0 ? sorted[0].uuid : '';
		} else {
			state.selectedPlayerUuid = prev;
		}

		if (state.selectedPlayerUuid) {
			select.value = state.selectedPlayerUuid;
		}
		updateViewControls();
	}

	function updateViewControls() {
		const modeSelect = document.getElementById('mode-select');
		const playerGroup = document.getElementById('player-filter-group');
		if (modeSelect.value !== state.viewMode) {
			modeSelect.value = state.viewMode;
		}
		playerGroup.classList.toggle('hidden', state.viewMode !== 'player');
	}

	async function fetchWorlds() {
		const response = await fetch('/api/worlds');
		if (!response.ok) {
			throw new Error('Failed to load worlds');
		}
		return response.json();
	}

	async function fetchSnapshot(worldName) {
		const params = new URLSearchParams();
		params.set('mode', state.viewMode);
		if (state.viewMode === 'player' && state.selectedPlayerUuid) {
			params.set('playerUuid', state.selectedPlayerUuid);
		}
		const response = await fetch(`/api/worlds/${encodeURIComponent(worldName)}/snapshot?${params.toString()}`);
		if (!response.ok) {
			throw new Error('Failed to load world snapshot');
		}
		return response.json();
	}

	async function refreshWorldSelector() {
		const worlds = await fetchWorlds();
		state.worlds = worlds;

		const select = document.getElementById('world-select');
		const names = worlds.map(item => item.name);
		if (names.length > 0 && !names.includes(state.world)) {
			state.world = names[0];
			clearAllEntityMarkers();
			updateTileLayer();
		}

		select.innerHTML = names.map(name => `<option value="${escapeHtml(name)}" ${name === state.world ? 'selected' : ''}>${escapeHtml(name)}</option>`).join('');
	}

	function updateConnectionStatus(label, className) {
		const status = document.getElementById('connection-status');
		status.textContent = label;
		status.className = `badge ${className}`;
	}

	function connectWebSocket() {
		updateConnectionStatus('Connecting', 'connecting');
		const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
		const socket = new WebSocket(`${protocol}//${location.host}/ws`);
		state.websocket = socket;

		socket.onopen = () => {
			updateConnectionStatus('Connected', 'connected');
			if (state.reconnectTimer) {
				clearTimeout(state.reconnectTimer);
				state.reconnectTimer = null;
			}
		};

		socket.onmessage = event => {
			try {
				const message = JSON.parse(event.data);
				if (message.type !== 'world_update' || !message.worlds) {
					return;
				}
				const snapshot = message.worlds[state.world];
				applySnapshot(snapshot);
			} catch (_) {
			}
		};

		socket.onclose = () => {
			updateConnectionStatus('Disconnected', 'disconnected');
			if (!state.reconnectTimer) {
				state.reconnectTimer = setTimeout(connectWebSocket, 3000);
			}
		};

		socket.onerror = () => {
			socket.close();
		};
	}

	function escapeHtml(text) {
		const div = document.createElement('div');
		div.textContent = String(text ?? '');
		return div.innerHTML;
	}

	async function onWorldChanged() {
		const selected = document.getElementById('world-select').value;
		if (!selected || selected === state.world) {
			return;
		}
		state.world = selected;
		clearAllEntityMarkers();
		updateTileLayer();
		applySnapshot(await fetchSnapshot(state.world));
	}

	async function onQualityChanged() {
		const selected = document.getElementById('quality-select').value;
		if (!selected || selected === state.quality) {
			return;
		}
		state.quality = selected;
		updateTileLayer();
	}

	async function onModeChanged() {
		const selected = document.getElementById('mode-select').value;
		if (!selected || selected === state.viewMode) {
			updateViewControls();
			return;
		}
		state.viewMode = selected;
		updateViewControls();
		updateTileLayer();
		if (state.lastSnapshot) {
			applyFilteredSnapshot();
		}
		applySnapshot(await fetchSnapshot(state.world));
	}

	async function onPlayerFilterChanged() {
		const selected = document.getElementById('player-select').value;
		if (!selected || selected === state.selectedPlayerUuid) {
			return;
		}
		state.selectedPlayerUuid = selected;
		updateTileLayer();
		if (state.lastSnapshot) {
			applyFilteredSnapshot();
		}
		applySnapshot(await fetchSnapshot(state.world));
	}

	async function init() {
		state.map = L.map('map', {
			crs: L.CRS.Simple,
			minZoom: -6,
			maxZoom: 4,
			zoomSnap: 0.5,
			zoomDelta: 0.5,
			maxBounds: L.latLngBounds(L.latLng(-120000, -120000), L.latLng(120000, 120000)),
			maxBoundsViscosity: 0.95
		});
		state.map.setView([0, 0], -1);

		state.map.on('mousemove', event => {
			const x = Math.round(event.latlng.lng / BLOCK_TO_MAP_SCALE);
			const z = Math.round(-event.latlng.lat / BLOCK_TO_MAP_SCALE);
			document.getElementById('coords-display').textContent = `X: ${x}, Z: ${z}`;
		});

		document.getElementById('world-select').addEventListener('change', () => {
			onWorldChanged().catch(() => {
			});
		});
		document.getElementById('quality-select').addEventListener('change', () => {
			onQualityChanged().catch(() => {
			});
		});
		document.getElementById('mode-select').addEventListener('change', () => {
			onModeChanged().catch(() => {
			});
		});
		document.getElementById('player-select').addEventListener('change', () => {
			onPlayerFilterChanged().catch(() => {
			});
		});

		await refreshWorldSelector();
		updateViewControls();
		updateTileLayer();
		applySnapshot(await fetchSnapshot(state.world));
		connectWebSocket();

		setInterval(() => {
			refreshWorldSelector().catch(() => {
			});
		}, 30000);
	}

	document.addEventListener('DOMContentLoaded', () => {
		init().catch(() => {
			updateConnectionStatus('Error', 'disconnected');
		});
	});
})();