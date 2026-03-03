package dev.ninesliced.webmap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;

/**
 * Handles websocket lifecycle and ping/pong frames.
 */
public class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final LiveDataBroadcaster broadcaster;
    private final WebSocketServerHandshaker handshaker;

    public WebSocketHandler(LiveDataBroadcaster broadcaster, WebSocketServerHandshaker handshaker) {
        this.broadcaster = broadcaster;
        this.handshaker = handshaker;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame closeFrame) {
            handshaker.close(ctx.channel(), closeFrame.retain());
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        broadcaster.removeChannel(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
