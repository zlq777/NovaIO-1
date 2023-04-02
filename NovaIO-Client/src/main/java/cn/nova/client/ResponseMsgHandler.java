package cn.nova.client;

import cn.nova.async.AsyncFuture;
import cn.nova.client.response.AppendNewEntryResponse;
import cn.nova.client.response.ReadEntryResponse;
import cn.nova.client.response.ResponseBinder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;

import java.util.Map;

/**
 * {@link ResponseMsgHandler}负责处理来自NovaIO服务节点的响应消息
 *
 * @author RealDragonking
 */
public class ResponseMsgHandler extends ChannelInboundHandlerAdapter {

    private final Map<Long, ResponseBinder> responseBindMap;

    public ResponseMsgHandler(Map<Long, ResponseBinder> responseBindMap) {
        this.responseBindMap = responseBindMap;
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

        ResponseBinder responseBinder = responseBindMap.remove(sessionId);
        AsyncFuture<?> future = responseBinder.getAsyncFuture();
        Class<?> futureType = responseBinder.getFutureType();

        if (future == null || futureType == null) {
            byteBuf.release();
            return;
        }

        if (futureType == ReadEntryResponse.class) {
            ((AsyncFuture<ReadEntryResponse>)future).notifyResponse(new ReadEntryResponse(byteBuf));
        } else if (futureType == AppendNewEntryResponse.class) {
            long entryIndex = byteBuf.readLong();
            ((AsyncFuture<AppendNewEntryResponse>)future).notifyResponse(new AppendNewEntryResponse(entryIndex));
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
