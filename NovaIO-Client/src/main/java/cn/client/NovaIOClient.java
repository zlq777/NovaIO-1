package cn.client;

import io.netty.buffer.ByteBuf;

/**
 * {@link NovaIOClient}定义了与NovaIO服务节点进行通信的客户端，提供了一系列基本api实现文件块数据的读写功能
 *
 * @author RealDragonking
 */
public interface NovaIOClient {

    AsyncFuture<ReadEntryResult> readEntry(long entryIndex);

    AsyncFuture<WriteEntryResult> writeEntry(ByteBuf entryData);

    void close();

}
