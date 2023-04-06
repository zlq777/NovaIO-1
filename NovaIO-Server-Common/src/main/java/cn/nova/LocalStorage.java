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
     * @param key {@link String}类型的键
     * @param defaultVal 默认值
     * @return 值
     */
    long readLong(String key, long defaultVal);

    /**
     * 写入long类型的数据
     *
     * @param key {@link String}类型的键
     * @param value 值
     */
    void writeLong(String key, long value);

    /**
     * 从硬盘中读取字节数据，并写入{@link ByteBuf}字节缓冲区中返回
     *
     * @param key {@link String}类型的键
     * @return {@link ByteBuf}
     */
    ByteBuf readBytes(String key);

    /**
     * 从硬盘中读取字节数据，并写入给定的{@link ByteBuf}字节缓冲区中
     *
     * @param key {@link String}类型的键
     * @param byteBuf {@link ByteBuf}
     */
    boolean readBytes(String key, ByteBuf byteBuf);

    /**
     * 往硬盘中写入{@link ByteBuf}字节缓冲区中的字节数据，这不会增加readerIndex
     *
     * @param key {@link String}类型的键
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    void writeBytes(String key, ByteBuf byteBuf);

    /**
     * 从硬盘中读取字节数据，并写入{@link ByteBuf}字节缓冲区中返回
     *
     * @param key long类型的键
     * @return {@link ByteBuf}
     */
    ByteBuf readBytes(long key);

    /**
     * 从硬盘中读取字节数据，并写入给定的{@link ByteBuf}字节缓冲区中
     *
     * @param key long类型的键
     * @param byteBuf {@link ByteBuf}
     */
    boolean readBytes(long key, ByteBuf byteBuf);

    /**
     * 往硬盘中写入{@link ByteBuf}字节缓冲区中的字节数据，这不会增加readerIndex
     *
     * @param key long类型的键
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    void writeBytes(long key, ByteBuf byteBuf);

    /**
     * 进行关闭，释放占用的资源
     */
    void close();

}
