package dev.ninesliced.webmap;

import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Embedded Netty web server for BetterMap web map assets and API routes.
 */
public class WebServer {
    private static final Logger LOGGER = Logger.getLogger(WebServer.class.getName());

    private final WebMapService service;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public WebServer(WebMapService service) {
        this.service = service;
    }

    public void start(int port) {
        bossGroup = NettyUtil.getEventLoopGroup(1, "bettermap-webmap-boss");
        workerGroup = NettyUtil.getEventLoopGroup(4, "bettermap-webmap-worker");

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NettyUtil.getServerChannel())
                .option(ChannelOption.SO_BACKLOG, 256)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<Channel>() {
                    {
                        Objects.requireNonNull(service);
                    }

                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline()
                            .addLast("codec", new HttpServerCodec())
                            .addLast("aggregator", new HttpObjectAggregator(512 * 1024))
                            .addLast("compressor", new HttpContentCompressor())
                            .addLast("handler", service.createHttpRequestHandler(false));
                    }
                });

            channel = bootstrap.bind(port).sync().channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Interrupted while starting web server: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (channel != null) {
            channel.close();
            channel = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
    }

    public boolean isRunning() {
        return channel != null && channel.isActive();
    }
}
