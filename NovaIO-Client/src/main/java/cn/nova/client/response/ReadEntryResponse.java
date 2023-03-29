package cn.nova.client.response;

import io.netty.buffer.ByteBuf;

/**
 * {@link ReadEntryResponse}是读取Entry数据的响应结果
 *
 * @author RealDragonking
 */
public class ReadEntryResponse {

    private final ByteBuf entryData;
    
    public ReadEntryResponse(ByteBuf entryData) {
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
