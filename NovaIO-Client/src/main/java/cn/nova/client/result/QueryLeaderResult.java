package cn.nova.client.result;

/**
 * {@link QueryLeaderResult}对应于探测ViewNode节点leader身份的请求，封装了{@link #isLeader}和{@link #term}
 *
 * @author RealDragonking
 */
public class QueryLeaderResult {

    private final boolean isLeader;
    private final long term;

    public QueryLeaderResult(boolean isLeader, long term) {
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
