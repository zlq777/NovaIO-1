package cn.nova.client;

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
    AsyncFuture<ReadEntryResult> readEntry(long entryIndex);

    /**
     * 将给定的Entry块数据写入集群
     *
     * @param entryData Entry块数据
     * @return {@link AsyncFuture}
     */
    AsyncFuture<WriteEntryResult> appendNewEntry(ByteBuf entryData);

    /**
     * 安全且优雅地关闭客户端
     */
    void close();

}
