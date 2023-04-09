package cn.nova;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.ThreadLocalRandom;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
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
     * 把{@link String}字符串的长度和内容写入此{@link ByteBuf}
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     * @param value {@link String}
     */
    public static void writeString(ByteBuf byteBuf, String value) {
        int writerIdx = byteBuf.writerIndex() + 4;
        int pathLen = byteBuf.writerIndex(writerIdx).writeCharSequence(value, StandardCharsets.UTF_8);
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
     * 创建一个可以存储{@link InetSocketAddress}的{@link java.util.Set}，默认使用{@link TreeSet}
     *
     * @return {@link java.util.Set}
     */
    public static Set<InetSocketAddress> createAddrSet() {
        return new TreeSet<>(createAddrComparator());
    }

    /**
     * 创建一个可以比较{@link InetSocketAddress}的{@link Comparator}
     *
     * @return {@link Comparator}
     */
    public static Comparator<InetSocketAddress> createAddrComparator() {
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

    /**
     * 尝试将{@link Comparable}强转为任意类型的返回值
     *
     * @param comparable {@link Comparable}
     * @return 任意类型的返回值
     * @param <T> 想要得到的类型
     */
    @SuppressWarnings("unchecked")
    public static <T> T parse(Comparable<?> comparable) {
        return (T) ObjectUtil.checkNotNull(comparable, "此comparable不应为null");
    }

}
