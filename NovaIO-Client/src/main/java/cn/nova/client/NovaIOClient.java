package cn.nova.client;

import cn.nova.client.result.AppendNewEntryResult;
import cn.nova.client.result.ReadEntryResult;
import io.netty.buffer.ByteBuf;

/**
 * {@link NovaIOClient}定义了与NovaIO服务节点进行通信的客户端，提供了一系列基本api实现Entry块数据的读写功能。
 * 所有的api都是异步非阻塞的。
 *
 * @author RealDragonking
 */
public interface NovaIOClient {

    /**
     * 根据给定的Entry序列号，从集群中读取对应的Entry块数据
     *
     * @param entryIndex Entry序列号
     * @return {@link AsyncFuture}
     */
    AsyncFuture readEntry(long entryIndex);

    /**
     * 将给定的Entry块数据写入集群，将尽最大可能在单次传输中写入更多的字节（上限32kb即32768字节）
     *
     * @param entryData Entry块数据
     * @return {@link AsyncFuture}
     */
    AsyncFuture appendNewEntry(ByteBuf entryData);

    /**
     * 安全且优雅地关闭客户端
     */
    void close();

}
