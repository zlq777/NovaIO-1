package cn.nova.client;

import cn.nova.AsyncFuture;
import cn.nova.client.result.ChangeDataNodeInfoResult;

import java.net.InetSocketAddress;

/**
 * {@link NovaIOClient}定义了与NovaIO服务节点进行通信的客户端，提供了一系列基本api实现对象数据的读取、集群的动态管理。
 * 所有的api都是异步非阻塞的。
 *
 * @author RealDragonking
 */
public interface NovaIOClient {

    /**
     * 新增一个DataNode节点集群，如果不存在则成功创建，已经存在则返回失败
     *
     * @param clusterName 集群名称
     * @param addresses 所有节点的{@link InetSocketAddress}列表
     * @return {@link AsyncFuture}
     */
    AsyncFuture<ChangeDataNodeInfoResult> addNewDataNodeCluster(String clusterName, InetSocketAddress[] addresses);

    /**
     * 安全且优雅地关闭客户端
     */
    void close();

}
