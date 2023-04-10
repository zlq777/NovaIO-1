package cn.nova.client;

import cn.nova.AsyncFuture;
import cn.nova.client.result.OperationResult;
import cn.nova.client.result.QueryDataNodeInfoResult;
import cn.nova.client.result.QueryLeaderResult;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;

import java.net.InetSocketAddress;
import java.util.*;

import static cn.nova.CommonUtils.*;

/**
 * {@link ResponseHandler}负责处理来自NovaIO服务节点的响应消息
 *
 * @author RealDragonking
 */
@ChannelHandler.Sharable
public class ResponseHandler extends ChannelInboundHandlerAdapter {

    private final Map<Long, AsyncFuture<?>> sessionMap;

    public ResponseHandler(Map<Long, AsyncFuture<?>> sessionMap) {
        this.sessionMap = sessionMap;
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

        AsyncFuture<?> future = sessionMap.remove(sessionId);

        if (future == null) {
            byteBuf.release();
            return;
        }

        Class<?> typeClass = future.getResultType();

        if (typeClass == OperationResult.class) {
            makeOperationResult(byteBuf, (AsyncFuture<OperationResult>) future);
        } else if (typeClass == QueryLeaderResult.class) {
            makeQueryLeaderResult(byteBuf, (AsyncFuture<QueryLeaderResult>) future);
        } else if (typeClass == QueryDataNodeInfoResult.class) {
            makeQueryDataNodeInfoResult(byteBuf, (AsyncFuture<QueryDataNodeInfoResult>) future);
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

    /**
     * 封装{@link OperationResult}
     *
     * @param byteBuf {@link ByteBuf}
     * @param asyncFuture {@link AsyncFuture}
     */
    private void makeOperationResult(ByteBuf byteBuf, AsyncFuture<OperationResult> asyncFuture) {
        boolean isSuccess = byteBuf.readBoolean();

        OperationResult result = new OperationResult(isSuccess);
        asyncFuture.notifyResult(result);

        byteBuf.release();
    }

    /**
     * 封装{@link QueryLeaderResult}
     *
     * @param byteBuf {@link ByteBuf}
     * @param asyncFuture {@link AsyncFuture}
     */
    private void makeQueryLeaderResult(ByteBuf byteBuf, AsyncFuture<QueryLeaderResult> asyncFuture) {
        boolean isLeader = byteBuf.readBoolean();
        long term = byteBuf.readLong();

        QueryLeaderResult result = new QueryLeaderResult(isLeader, term);
        asyncFuture.notifyResult(result);

        byteBuf.release();
    }

    /**
     * 封装{@link QueryDataNodeInfoResult}
     *
     * @param byteBuf {@link ByteBuf}
     * @param asyncFuture {@link AsyncFuture}
     */
    private void makeQueryDataNodeInfoResult(ByteBuf byteBuf, AsyncFuture<QueryDataNodeInfoResult> asyncFuture) {
        Map<String, Set<InetSocketAddress>> map = new HashMap<>();

        while (byteBuf.readableBytes() > 0) {

            Set<InetSocketAddress> addrSet = createAddrSet();
            String clusterName = readString(byteBuf);
            int nodeNumber = byteBuf.readInt();

            while (nodeNumber-- > 0){
                String ipAddr = readString(byteBuf);
                int port = byteBuf.readInt();

                addrSet.add(new InetSocketAddress(ipAddr, port));
            }

            map.put(clusterName, addrSet);
        }

        QueryDataNodeInfoResult result = new QueryDataNodeInfoResult(map);
        asyncFuture.notifyResult(result);

        byteBuf.release();
    }

}
