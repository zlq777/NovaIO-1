package cn.nova;

import io.netty.buffer.ByteBuf;

/**
 * {@link LocalStorage}被定义为本地缓存数据库，提供了KV键值对存储和Entry块数据存储服务
 *
 * @author RealDragonking
 */
public interface LocalStorage {

    /**
     * 读取long类型的数据，如果不存在则写入默认值
     *
     * @param key 键
     * @param defaultVal 默认值
     * @return 值
     */
    long readLong(String key, long defaultVal);

    /**
     * 写入long类型的数据
     *
     * @param key 键
     * @param value 值
     */
    void writeLong(String key, long value);

    /**
     * 从硬盘中读取Entry类型的数据，统一为32KB，并写入{@link ByteBuf}字节缓冲区中
     *
     * @param entryIndex Entry索引
     * @param byteBuf {@link ByteBuf}
     */
    void readEntry(long entryIndex, ByteBuf byteBuf);

    /**
     * 从{@link ByteBuf}字节缓冲区中读取Entry类型的数据,统一为32KB，并写入硬盘中
     *
     * @param entryIndex Entry索引
     * @param byteBuf {@link ByteBuf}
     */
    void writeEntry(long entryIndex, ByteBuf byteBuf);

    /**
     * 使用给定的索引删除对应Entry类型的数据
     *
     * @param entryIndex Entry索引
     */
    void removeEntry(long entryIndex);

    /**
     * 进行关闭，释放占用的资源
     */
    void close();

}
