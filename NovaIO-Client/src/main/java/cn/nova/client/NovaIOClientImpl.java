package cn.nova.client;

import cn.nova.async.AsyncFuture;
import cn.nova.client.response.AppendNewEntryResponse;
import cn.nova.client.response.ReadEntryResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;

import java.util.Map;

/**
 * {@link NovaIOClient}的默认实现类
 *
 * @author RealDragonking
 */
final class NovaIOClientImpl implements NovaIOClient {

    private final Map<Long, AsyncFuture<?>> futureBindMap;
    private final EventLoopGroup ioThreadGroup;
    private final Channel[] viewNodeChannels;
    private final Timer connector;
    private final int timeout;
    private Channel leaderChannel;

    NovaIOClientImpl(EventLoopGroup ioThreadGroup, Timer connector,
                     Map<Long, AsyncFuture<?>> futureBindMap,
                     Channel[] viewNodeChannels,
                     int timeout) {
        this.viewNodeChannels = viewNodeChannels;
        this.futureBindMap = futureBindMap;
        this.ioThreadGroup = ioThreadGroup;
        this.connector = connector;
        this.timeout = timeout;
    }

    /**
     * 根据给定的Entry序列号，从所有集群中读取对应的Entry块数据
     *
     * @param entryIndex Entry序列号
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<ReadEntryResponse> readEntry(long entryIndex) {
        return null;
    }

    /**
     * 将给定的Entry块数据写入所有集群，将尽最大可能在单次传输中写入更多的字节（上限32kb即32768字节）
     *
     * @param entryData Entry块数据
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<AppendNewEntryResponse> appendNewEntry(ByteBuf entryData) {
        return null;
    }

    /**
     * 安全且优雅地关闭客户端
     */
    @Override
    public void close() {
        ioThreadGroup.shutdownGracefully();
        connector.stop();
    }

}
