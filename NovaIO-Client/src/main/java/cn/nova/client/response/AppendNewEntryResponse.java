package cn.nova.client.response;

/**
 * {@link AppendNewEntryResponse}是写入Entry数据的响应结果
 *
 * @author RealDragonking
 */
public class AppendNewEntryResponse {

    private final long entryIndex;

    public AppendNewEntryResponse(long entryIndex) {
        this.entryIndex = entryIndex;
    }

    /**
     * 获取到Entry的序列号
     *
     * @return Entry序列号
     */
    public long getEntryIndex() {
        return this.entryIndex;
    }

}
