package cn.nova.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static cn.nova.CommonUtils.*;

/**
 * {@link NovaIOClient}的默认实现类
 *
 * @author RealDragonking
 */
class NovaIOClientImpl implements NovaIOClient {

    private static final int MAX_BYTE_SIZE = 32768;
    private final Map<Long, AsyncFuture> futureMap;
    private final EventLoopGroup ioThreadGroup;
    private final AtomicLong sessionIdCreator;
    private final Channel channel;

    NovaIOClientImpl(EventLoopGroup ioThreadGroup, Channel channel, Map<Long, AsyncFuture> futureMap) {
        this.sessionIdCreator = new AtomicLong(-1L);
        this.ioThreadGroup = ioThreadGroup;
        this.futureMap = futureMap;
        this.channel = channel;
    }

    /**
     * 根据给定的Entry序列号，从集群中读取对应的Entry块数据
     *
     * @param entryIndex Entry序列号
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture readEntry(long entryIndex) {
        long sessionId = sessionIdCreator.incrementAndGet();
        AsyncFuture future = new AsyncFutureImpl();

        futureMap.put(sessionId, future);

        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer().writerIndex(4);

        writePath(byteBuf, "/read-entry");
        byteBuf.writeLong(sessionId);

        byteBuf.writeLong(entryIndex);

        int writeIndex = byteBuf.writerIndex();
        byteBuf.writerIndex(0).writeInt(writeIndex - 4).writerIndex(writeIndex);

        channel.writeAndFlush(byteBuf);

        return future;
    }

    /**
     * 将给定的Entry块数据写入集群，将尽最大可能在单次传输中写入更多的字节（上限32kb即32768字节）
     *
     * @param entryData Entry块数据
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture appendNewEntry(ByteBuf entryData) {
        long sessionId = sessionIdCreator.incrementAndGet();
        AsyncFuture future = new AsyncFutureImpl();

        futureMap.put(sessionId, future);

        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer().writerIndex(4);

        writePath(byteBuf, "/append-new-entry");
        byteBuf.writeLong(sessionId);

        int byteSize = Math.min(entryData.readableBytes(), MAX_BYTE_SIZE);
        byteBuf.writeBytes(entryData, byteSize);

        int writeIndex = byteBuf.writerIndex();
        byteBuf.writerIndex(0).writeInt(writeIndex - 4).writerIndex(writeIndex);

        channel.writeAndFlush(byteBuf);

        return future;
    }

    /**
     * 安全且优雅地关闭客户端
     */
    @Override
    public void close() {
        ioThreadGroup.shutdownGracefully();
    }

}
