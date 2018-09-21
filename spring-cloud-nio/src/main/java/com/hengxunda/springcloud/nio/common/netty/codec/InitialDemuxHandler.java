package com.hengxunda.springcloud.nio.common.netty.codec;

import com.hengxunda.springcloud.nio.common.netty.RoomChannelContainer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;

@Slf4j
@NoArgsConstructor
public final class InitialDemuxHandler extends ChannelInboundHandlerAdapter {

    private volatile boolean isInited = false;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf in = (ByteBuf) msg;
            ChannelPipeline pipeline = ctx.channel().pipeline();
            ChannelHandlerContext baseCtx = pipeline.context(JsonBaseCodec.class);
            if (baseCtx == null) {
                throw new IllegalStateException("No JsonBaseDecoder in the pipeline");
            }
            String baseName = baseCtx.name();
            if (isNormalSocketRequest(in)) {
                pipeline.addBefore(baseName, "lengthFieldDecoder", new LengthFieldBasedFrameDecoder(409600000, 0, 4, 0, 4));
                pipeline.addBefore(baseName, "lengthFieldEncoder", new LengthFieldPrepender(4));
                pipeline.addBefore(baseName, "stringDecode", new StringDecoder(Charsets.UTF_8));
                pipeline.addBefore(baseName, "stringEncode", new StringEncoder(Charsets.UTF_8));
                if (pipeline.get(InitialDemuxHandler.class) != null) {
                    pipeline.remove(InitialDemuxHandler.class);
                }
                ctx.fireChannelRead(msg);
            } else {
                if (!isInited) {
                    pipeline.addBefore(baseName, "http-codec", new HttpServerCodec());
                    pipeline.addBefore(baseName, "aggregator", new HttpObjectAggregator(65536));
                    pipeline.addBefore(baseName, "http-chunked", new ChunkedWriteHandler());
                    pipeline.addBefore(baseName, "webSocketServerHandler", new WebSocketServerHandler());
                    pipeline.addBefore(baseName, "WebSocketEncoder", new WebSocketEncoder());
                }
                isInited = true;
                ctx.fireChannelRead(msg);
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state().equals(IdleState.READER_IDLE)) {
                RoomChannelContainer.removeChannel(ctx.channel());
            }
        }
        ctx.fireUserEventTriggered(evt);
    }

    private static boolean isNormalSocketRequest(ByteBuf buffer) {
        boolean result = false;
        try {
            char c5 = (char) buffer.getByte(4);
            char c6 = (char) buffer.getByte(5);
            if (c5 == '1' && (c6 >= '0' && c6 <= '9')) {
                result = true;
            }
        } catch (Exception e) {

        }
        return result;
    }

}
