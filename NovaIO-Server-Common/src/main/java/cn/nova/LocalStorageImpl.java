package cn.nova;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.FastThreadLocal;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import static cn.nova.CommonUtils.*;

/**
 * {@link LocalStorage}的默认实现类
 *
 * @author RealDragonking
 */
public class LocalStorageImpl implements LocalStorage {

    private final RocksDB rocksDB;
    private final FastThreadLocal<byte[]> longBucket;
    private final FastThreadLocal<byte[]> entryBucket;

    public LocalStorageImpl() throws Exception {
        try (Options options = new Options()) {
            options.setCreateIfMissing(true);
            this.rocksDB = RocksDB.open(options, "./data");
        }

        this.longBucket = getByteBucket(8);
        this.entryBucket = getByteBucket(32768);
    }

    /**
     * 读取long类型的数据，如果不存在则写入默认值
     *
     * @param key        键
     * @param defaultVal 默认值
     * @return 值
     */
    @Override
    public long readLong(String key, long defaultVal) {
        byte[] bucket = longBucket.get();

        try {
            if (rocksDB.get(key.getBytes(), bucket) > -1) {
                return parseByteToLong(bucket);
            } else {
                parseLongToByte(bucket, defaultVal);
                rocksDB.put(key.getBytes(), bucket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return defaultVal;
    }

    /**
     * 写入long类型的数据
     *
     * @param key   键
     * @param value 值
     */
    @Override
    public void writeLong(String key, long value) {
        byte[] bucket = longBucket.get();

        try {
            parseLongToByte(bucket, value);
            rocksDB.put(key.getBytes(), bucket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从硬盘中读取Entry类型的数据，上限为32KB，并写入{@link ByteBuf}字节缓冲区中
     *
     * @param entryIndex Entry索引
     * @param byteBuf {@link ByteBuf}
     */
    @Override
    public void readEntry(long entryIndex, ByteBuf byteBuf) {
        byte[] longBucket = this.longBucket.get();
        byte[] entryBucket = this.entryBucket.get();

        try {
            parseLongToByte(longBucket, entryIndex);
            rocksDB.get(longBucket, entryBucket);
            byteBuf.writeBytes(entryBucket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从{@link ByteBuf}字节缓冲区中读取Entry类型的数据,统一为32KB，并写入硬盘中
     *
     * @param entryIndex Entry索引
     * @param byteBuf {@link ByteBuf}
     */
    @Override
    public void writeEntry(long entryIndex, ByteBuf byteBuf) {
        byte[] longBucket = this.longBucket.get();
        byte[] entryBucket = this.entryBucket.get();

        try {
            parseLongToByte(longBucket, entryIndex);
            byteBuf.readBytes(entryBucket);
            rocksDB.put(longBucket, entryBucket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 使用给定的索引删除对应Entry类型的数据
     *
     * @param entryIndex Entry索引
     */
    @Override
    public void removeEntry(long entryIndex) {
        byte[] longBucket = this.longBucket.get();

        try {
            parseLongToByte(longBucket, entryIndex);
            rocksDB.delete(longBucket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 进行关闭，释放占用的资源
     */
    @Override
    public void close() {
        rocksDB.close();
    }

}
