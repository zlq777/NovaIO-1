package cn.client;

import io.netty.buffer.ByteBuf;

/**
 * {@link NovaIOClient}的默认实现类
 *
 * @author RealDragonking
 */
public class NovaIOClientImpl implements NovaIOClient {

    public NovaIOClientImpl() {

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

    }

}
