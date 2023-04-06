package cn.nova.client.result;

/**
 * {@link ChangeDataNodeInfoResult}是修改DataNode集群信息结构体的响应结果，涉及到往
 * 一个DataNode节点集群加入新节点和删除旧节点两种操作
 *
 * @author RealDragonking
 */
public class ChangeDataNodeInfoResult {

    private final boolean isSuccess;

    public ChangeDataNodeInfoResult(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    /**
     * 获取到集群信息结构体的修改是否成功，一般加入新节点的操作是肯定会成功的，删除节点的操作可能会因为
     * 节点不存在而删除失败
     *
     * @return 集群信息结构体的修改是否成功
     */
    public boolean isSuccess() {
        return this.isSuccess;
    }

}
