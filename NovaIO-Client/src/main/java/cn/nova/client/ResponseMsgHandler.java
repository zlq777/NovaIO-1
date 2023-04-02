package cn.nova.client;

import cn.nova.async.AsyncFuture;
import cn.nova.client.response.AppendNewEntryResponse;
import cn.nova.client.response.ReadEntryResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;

import java.util.Map;

/**
 * {@link ResponseMsgHandler}负责处理来自NovaIO服务节点的响应消息
 *
 * @author RealDragonking
 */
public class ResponseMsgHandler extends ChannelInboundHandlerAdapter {

    private final Map<Long, AsyncFuture<?>> futureBindMap;

    public ResponseMsgHandler(Map<Long, AsyncFuture<?>> futureBindMap) {
        this.futureBindMap = futureBindMap;
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelRead(Object)} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Subclasses may override this method to change behavior.
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param msg 消息
     */
    @Override
    @SuppressWarnings("unchecked")
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        long sessionId = byteBuf.readLong();

        AsyncFuture<?> future = futureBindMap.remove(sessionId);

        if (future == null) {
            byteBuf.release();
            return;
        }

        Class<?> typeClass = future.getResultType();

        if (typeClass == ReadEntryResponse.class) {
            ((AsyncFuture<ReadEntryResponse>)future).notifyResult(new ReadEntryResponse(byteBuf));
        } else if (typeClass == AppendNewEntryResponse.class) {
            long entryIndex = byteBuf.readLong();
            ((AsyncFuture<AppendNewEntryResponse>)future).notifyResult(new AppendNewEntryResponse(entryIndex));
        }
    }

    /**
     * Calls {@link ChannelHandlerContext#fireExceptionCaught(Throwable)} to forward
     * to the next {@link ChannelHandler} in the {@link ChannelPipeline}.
     * <p>
     * Subclasses may override this method to change behavior.
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param cause {@link Throwable}异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }

}
