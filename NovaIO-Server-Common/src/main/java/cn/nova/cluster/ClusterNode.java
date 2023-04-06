package cn.nova.cluster;

import java.net.InetSocketAddress;

/**
 * {@link ClusterNode}定义了一个集群节点的抽象视图，提供了节点的物理基本信息
 *
 * @author RealDragonking
 */
public final class ClusterNode {

    private final int index;
    private final InetSocketAddress address;
    private volatile long inSyncEntryIndex;

    public ClusterNode(int index, InetSocketAddress address) {
        this.index = index;
        this.address = address;
    }

    /**
     * 获取到此{@link ClusterNode}的序列号
     *
     * @return 节点的序列号
     */
    public int index() {
        return this.index;
    }

    /**
     * 获取到正在和此节点进行同步的Entry序列号
     *
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
     * 获取到此{@link ClusterNode}是否已经经过Entry一致性检测
     *
     * @return 是否已经经过Entry一致性检测
     */
    public boolean hasChecked() {
        return inSyncEntryIndex > -1L;
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
