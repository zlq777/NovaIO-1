package cn.nova;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
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
    private final FastThreadLocal<byte[]> keyBucket;
    private final FastThreadLocal<byte[]> valueBucket;
    private final ByteBufAllocator alloc;

    public LocalStorageImpl() throws Exception {
        try (Options options = new Options()) {
            options.setCreateIfMissing(true);
            this.rocksDB = RocksDB.open(options, "./data");
        }

        this.keyBucket = getByteBucket(4096);
        this.valueBucket = getByteBucket(32768);
        this.alloc = ByteBufAllocator.DEFAULT;
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
        byte[] keyBytes = key.getBytes();
        byte[] valueBucket = this.valueBucket.get();

        try {
            if (rocksDB.get(keyBytes, 0, keyBytes.length, valueBucket, 0, 8) > -1) {
                return parseByteToLong(valueBucket);
            } else {
                parseLongToByte(valueBucket, defaultVal);
                rocksDB.put(keyBytes, 0, keyBytes.length, valueBucket, 0, 8);
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
        byte[] keyBytes = key.getBytes();
        byte[] valueBucket = this.valueBucket.get();

        try {
            parseLongToByte(valueBucket, value);
            rocksDB.put(keyBytes, 0, keyBytes.length, valueBucket, 0, 8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从硬盘中读取Entry类型的数据，并写入{@link ByteBuf}字节缓冲区中返回
     *
     * @param key {@link String}类型的键
     * @return {@link ByteBuf}
     */
    @Override
    public ByteBuf readBytes(String key) {
        ByteBuf byteBuf = alloc.buffer();
        readBytes(key, byteBuf);
        return byteBuf;
    }

    /**
     * 从硬盘中读取字节数据，并写入给定的{@link ByteBuf}字节缓冲区中
     *
     * @param key     {@link String}类型的键
     * @param byteBuf {@link ByteBuf}
     */
    @Override
    public boolean readBytes(String key, ByteBuf byteBuf) {
        byte[] keyBytes = key.getBytes();
        byte[] valueBytes = this.valueBucket.get();

        try {
            int len = rocksDB.get(keyBytes, 0, keyBytes.length, valueBytes, 0, valueBytes.length);
            if (len > -1) {
                byteBuf.writeBytes(valueBytes, 0, len);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 往硬盘中写入{@link ByteBuf}字节缓冲区中的字节数据，这不会增加readerIndex
     *
     * @param key     {@link String}类型的键
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @Override
    public void writeBytes(String key, ByteBuf byteBuf) {
        byte[] keyBytes = key.getBytes();
        byte[] valueBytes = this.valueBucket.get();

        try {
            int readerIndex = byteBuf.readerIndex();
            int length = byteBuf.readableBytes();
            byteBuf.readBytes(valueBytes, 0, length);
            byteBuf.readerIndex(readerIndex);

            rocksDB.put(keyBytes, 0, 8, valueBytes, 0, length);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从硬盘中读取字节数据，并写入{@link ByteBuf}字节缓冲区中返回
     *
     * @param key long类型的键
     * @return {@link ByteBuf}
     */
    @Override
    public ByteBuf readBytes(long key) {
        throw new UnsupportedOperationException();
    }

    /**
     * 从硬盘中读取字节数据，并写入给定的{@link ByteBuf}字节缓冲区中
     *
     * @param key     long类型的键
     * @param byteBuf {@link ByteBuf}
     */
    @Override
    public boolean readBytes(long key, ByteBuf byteBuf) {
        byte[] keyBytes = this.keyBucket.get();
        byte[] valueBytes = this.valueBucket.get();

        try {
            parseLongToByte(keyBytes, key);
            int len = rocksDB.get(keyBytes, 0, 8, valueBytes, 0, valueBytes.length);
            if (len > -1) {
                byteBuf.writeBytes(valueBytes, 0, len);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 往硬盘中写入{@link ByteBuf}字节缓冲区中的字节数据，这不会增加readerIndex
     *
     * @param key     long类型的键
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @Override
    public void writeBytes(long key, ByteBuf byteBuf) {
        byte[] keyBytes = this.keyBucket.get();
        byte[] valueBytes = this.valueBucket.get();

        try {
            parseLongToByte(keyBytes, key);

            int readerIndex = byteBuf.readerIndex();
            int length = byteBuf.readableBytes();
            byteBuf.readBytes(valueBytes, 0, length);
            byteBuf.readerIndex(readerIndex);

            rocksDB.put(keyBytes, 0, 8, valueBytes, 0, length);
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
