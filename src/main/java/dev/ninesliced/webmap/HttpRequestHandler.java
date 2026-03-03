package dev.ninesliced.webmap;

import dev.ninesliced.webmap.handlers.BatchTileHandler;
import dev.ninesliced.webmap.handlers.IconHandler;
import dev.ninesliced.webmap.handlers.StaticHandler;
import dev.ninesliced.webmap.handlers.TileHandler;
import dev.ninesliced.webmap.handlers.WorldDataHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;

/**
 * Entry point for HTTP routes and websocket upgrade handling.
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private final TileHandler tileHandler;
	private final BatchTileHandler batchTileHandler;
	private final WorldDataHandler worldDataHandler;
	private final IconHandler iconHandler;
	private final StaticHandler staticHandler;
	private final LiveDataBroadcaster broadcaster;
	private final boolean secure;

	public HttpRequestHandler(TileHandler tileHandler,
							  BatchTileHandler batchTileHandler,
							  WorldDataHandler worldDataHandler,
							  IconHandler iconHandler,
							  LiveDataBroadcaster broadcaster,
							  boolean secure) {
		this.tileHandler = tileHandler;
		this.batchTileHandler = batchTileHandler;
		this.worldDataHandler = worldDataHandler;
		this.iconHandler = iconHandler;
		this.broadcaster = broadcaster;
		this.staticHandler = new StaticHandler();
		this.secure = secure;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
		if (!req.decoderResult().isSuccess()) {
			sendError(ctx, HttpResponseStatus.BAD_REQUEST);
			return;
		}

		String uri = req.uri();
		String pathOnly = uri;
		int queryIndex = uri.indexOf('?');
		if (queryIndex >= 0) {
			pathOnly = uri.substring(0, queryIndex);
		}
		if (uri.equals("/ws") && isWebSocketUpgrade(req)) {
			handleWebSocketUpgrade(ctx, req);
			return;
		}

		if (uri.equals("/api/tiles/batch")) {
			if (req.method() == HttpMethod.OPTIONS) {
				handleCorsPreflight(ctx);
			} else {
				batchTileHandler.handle(ctx, req);
			}
			return;
		}

		if (uri.startsWith("/api/tiles/")) {
			tileHandler.handle(ctx, req);
			return;
		}

		if (pathOnly.equals("/api/worlds")) {
			worldDataHandler.handleWorlds(ctx, req);
			return;
		}

		if (pathOnly.startsWith("/api/worlds/") && pathOnly.endsWith("/snapshot")) {
			worldDataHandler.handleSnapshot(ctx, req);
			return;
		}

		if (uri.startsWith("/api/icons/")) {
			iconHandler.handle(ctx, req);
			return;
		}

		staticHandler.handle(ctx, req);
	}

	private boolean isWebSocketUpgrade(FullHttpRequest req) {
		String upgrade = req.headers().get(HttpHeaderNames.UPGRADE);
		return upgrade != null && upgrade.equalsIgnoreCase("websocket");
	}

	private void handleWebSocketUpgrade(ChannelHandlerContext ctx, FullHttpRequest req) {
		String protocol = secure ? "wss" : "ws";
		String wsUrl = protocol + "://" + req.headers().get(HttpHeaderNames.HOST) + "/ws";
		WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(wsUrl, null, false);
		WebSocketServerHandshaker handshaker = factory.newHandshaker(req);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
			return;
		}

		handshaker.handshake(ctx.channel(), req);
		broadcaster.addChannel(ctx.channel());
		ctx.pipeline().replace(this, "websocket", new WebSocketHandler(broadcaster, handshaker));
	}

	private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private void handleCorsPreflight(ChannelHandlerContext ctx) {
		DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		response.headers()
			.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
			.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "POST, OPTIONS")
			.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type")
			.set(HttpHeaderNames.CONTENT_LENGTH, 0);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		ctx.close();
	}
}