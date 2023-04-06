package cn.nova.client.result;

/**
 * {@link AppendNewEntryResult}是写入Entry数据的响应响应
 *
 * @author RealDragonking
 */
public class AppendNewEntryResult {

    private final long entryIndex;

    public AppendNewEntryResult(long entryIndex) {
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
