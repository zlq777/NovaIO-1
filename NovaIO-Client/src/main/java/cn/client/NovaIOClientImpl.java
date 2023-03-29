package cn.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;

import java.util.Map;

/**
 * {@link NovaIOClient}的默认实现类
 *
 * @author RealDragonking
 */
class NovaIOClientImpl implements NovaIOClient {

    private final Map<Long, AsyncFuture<?>> waiterMap;
    private final EventLoopGroup ioThreadGroup;
    private final Channel channel;

    NovaIOClientImpl(EventLoopGroup ioThreadGroup, Channel channel, Map<Long, AsyncFuture<?>> waiterMap) {
        this.ioThreadGroup = ioThreadGroup;
        this.channel = channel;
        this.waiterMap = waiterMap;
    }

    /**
     * 根据给定的Entry序列号，从集群中读取对应的Entry块数据
     *
     * @param entryIndex Entry序列号
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<ReadEntryResult> readEntry(long entryIndex) {
        return null;
    }

    /**
     * 将给定的Entry块数据写入集群
     *
     * @param entryData Entry块数据
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<WriteEntryResult> writeEntry(ByteBuf entryData) {
        return null;
    }

    /**
     * 安全且优雅地关闭客户端
     */
    @Override
    public void close() {
        ioThreadGroup.shutdownGracefully();
    }

}
