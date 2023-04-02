package cn.nova.client.response;

import io.netty.buffer.ByteBuf;

/**
 * {@link ReadEntryResult}是读取Entry数据的响应消息
 *
 * @author RealDragonking
 */
public class ReadEntryResult {

    private final ByteBuf entryData;
    
    public ReadEntryResult(ByteBuf entryData) {
        this.entryData = entryData;
    }

    /**
     * 获取到写入了Entry数据的{@link ByteBuf}字节缓冲区
     * 
     * @return {@link ByteBuf}
     */
    public ByteBuf getEntryData() {
        return this.entryData;
    }
    
}
