package cn.nova;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.ThreadLocalRandom;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.concurrent.ThreadFactory;

/**
 * 提供了一些全局通用的方法
 *
 * @author RealDragonking
 */
public final class CommonUtils {

    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

    private CommonUtils() {}

    /**
     * 获取一个指定范围内的随机数
     *
     * @param startIdx 最小位置
     * @param endIdx 最大位置
     * @return 范围内随机数
     */
    public static int randomInRange(int startIdx, int endIdx) {
        return RANDOM.nextInt(startIdx, endIdx);
    }

    /**
     * 把字符串的长度和内容写入此{@link ByteBuf}
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     * @param string {@link String}
     */
    public static void writeString(ByteBuf byteBuf, String string) {
        int writerIdx = byteBuf.writerIndex() + 4;
        int pathLen = byteBuf.writerIndex(writerIdx).writeCharSequence(string, StandardCharsets.UTF_8);
        byteBuf.writerIndex(writerIdx - 4)
                .writeInt(pathLen)
                .writerIndex(writerIdx + pathLen);
    }

    /**
     * 从{@link ByteBuf}中读取字符串的长度，随后读取尾随的字符串
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     * @return {@link String}
     */
    public static String readString(ByteBuf byteBuf) {
        int len = byteBuf.readInt();
        return (String) byteBuf.readCharSequence(len, StandardCharsets.UTF_8);
    }

    /**
     * 提供一个自带计数功能的{@link ThreadFactory}
     *
     * @param prefixName 前缀名称
     * @param needCount 是否需要对创建的线程进行计数
     * @return {@link ThreadFactory}
     */
    public static ThreadFactory getThreadFactory(String prefixName, boolean needCount) {
        return new ThreadFactory() {
            private int cnt = 0;
            @Override
            public Thread newThread(Runnable r) {
                return needCount ? new Thread(r, prefixName + "-" + cnt++) : new Thread(r, prefixName);
            }
        };
    }

    /**
     * 提供一个能够获取byte[]的{@link FastThreadLocal}
     *
     * @param size byte[]的大小
     * @return {@link FastThreadLocal}
     */
    public static FastThreadLocal<byte[]> getByteBucket(int size) {
        return new FastThreadLocal<>() {
            @Override
            protected byte[] initialValue() {
                return new byte[size];
            }
        };
    }

    /**
     * 往byte[]中写入long值
     *
     * @param bucket byte[]
     * @param l long
     */
    public static void parseLongToByte(byte[] bucket, long l) {
        bucket[0] = (byte) (l & 0xff);
        bucket[1] = (byte) (l >>> 8 & 0xff);
        bucket[2] = (byte) (l >>> 16 & 0xff);
        bucket[3] = (byte) (l >>> 24 & 0xff);
        bucket[4] = (byte) (l >>> 32 & 0xff);
        bucket[5] = (byte) (l >>> 40 & 0xff);
        bucket[6] = (byte) (l >>> 48 & 0xff);
        bucket[7] = (byte) (l >>> 56 & 0xff);
    }

    /**
     * 从byte[]中读取long
     *
     * @param bucket byte[]
     * @return long
     */
    public static long parseByteToLong(byte[] bucket) {
        return ((long)(bucket[0] & 0xff))
                | ((long)(bucket[1] & 0xff)) << 8
                | ((long)(bucket[2] & 0xff)) << 16
                | ((long)(bucket[3] & 0xff)) << 24
                | ((long)(bucket[4] & 0xff)) << 32
                | ((long)(bucket[5] & 0xff)) << 40
                | ((long)(bucket[6] & 0xff)) << 48
                | ((long)(bucket[7] & 0xff)) << 56;
    }

    /**
     * 创建一个可以比较{@link InetSocketAddress}的{@link Comparator}
     *
     * @return {@link Comparator}
     */
    public static Comparator<InetSocketAddress> createAddressComparator() {
        return (addr1, addr2) -> {
            String ip1 = addr1.getAddress().getHostAddress();
            String ip2 = addr2.getAddress().getHostAddress();
            int res = ip1.compareTo(ip2);
            if (res == 0) {
                return Integer.compare(addr1.getPort(), addr2.getPort());
            } else {
                return res;
            }
        };
    }

}
