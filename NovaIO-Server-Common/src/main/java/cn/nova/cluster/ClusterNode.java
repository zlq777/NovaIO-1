package cn.nova.cluster;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.net.InetSocketAddress;

/**
 * {@link ClusterNode}定义了一个集群节点的抽象视图，提供了节点的物理基本信息
 *
 * @author RealDragonking
 */
public final class ClusterNode {

    private final InetSocketAddress address;
    private final ByteBuf inSyncEntryData;
    private volatile long inSyncEntryIndex;
    private volatile boolean isSendEnable;

    public ClusterNode(InetSocketAddress address) {
        this.inSyncEntryData = ByteBufAllocator.DEFAULT.buffer();
        this.address = address;
    }

    /**
     * @return 正在和此节点进行同步的Entry序列号
     */
    public long inSyncEntryIndex() {
        return this.inSyncEntryIndex;
    }

    /**
     * 设置正在和此节点进行同步的Entry序列号
     *
     * @param entryIndex 正在和此节点进行同步的Entry序列号
     */
    public void setInSyncEntryIndex(long entryIndex) {
        this.inSyncEntryIndex = entryIndex;
    }

    /**
     * 设置是否可以发送Entry数据
     *
     * @param isSendEnable 是否可以发送Entry数据
     */
    public void setSendEnable(boolean isSendEnable) {
        this.isSendEnable = isSendEnable;
    }

    /**
     * @return 是否可以发送Entry数据
     */
    public boolean isSendEnable() {
        return this.isSendEnable;
    }

    /**
     * @return 正在和此节点进行同步的Entry数据
     */
    public ByteBuf inSyncEntryData() {
        return this.inSyncEntryData;
    }

    /**
     * 获取到此{@link ClusterNode}的udp通信地址{@link InetSocketAddress}
     *
     * @return {@link InetSocketAddress}
     */
    public InetSocketAddress address() {
        return this.address;
    }

}
