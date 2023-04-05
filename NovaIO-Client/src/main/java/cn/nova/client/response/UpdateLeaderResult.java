package cn.nova.client.response;

/**
 * {@link UpdateLeaderResult}是探测并更新ViewNode集群中新任leader节点的响应消息
 *
 * @author RealDragonking
 */
public class UpdateLeaderResult {

    private final boolean isLeader;
    private final long term;

    public UpdateLeaderResult(boolean isLeader, long term) {
        this.isLeader = isLeader;
        this.term = term;
    }

    /**
     * 获取到当前节点是否是新任Leader节点
     *
     * @return 当前节点是否是新任Leader节点
     */
    public boolean isLeader() {
        return this.isLeader;
    }

    /**
     * 获取到当前节点的任期（如果为Leader的话）
     *
     * @return 当前节点的任期
     */
    public long getTerm() {
        return this.term;
    }

}
