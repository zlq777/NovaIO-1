package cn.nova.cluster;

/**
 * {@link ClusterInfo}是一个中转传递类，聚合了集群的基本信息
 *
 * @author RealDragonking
 */
public class ClusterInfo {

    private final int index;
    private final ClusterNode[] otherNodes;

    public ClusterInfo(int index, ClusterNode[] otherNodes) {
        this.index = index;
        this.otherNodes = otherNodes;
    }

    /**
     * 获取到当前节点的序列号
     *
     * @return 当前节点的序列号
     */
    public int getIndex() {
        return this.index;
    }

    /**
     * 获取到其它节点的抽象视图
     *
     * @return 其它节点的抽象视图
     */
    public ClusterNode[] getOtherNodes() {
        return this.otherNodes;
    }

}
