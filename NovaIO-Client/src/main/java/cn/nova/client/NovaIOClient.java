package cn.nova.client;

import cn.nova.AsyncFuture;
import cn.nova.client.result.OperationResult;

import java.net.InetSocketAddress;

/**
 * {@link NovaIOClient}定义了与NovaIO服务节点进行通信的客户端，提供了一系列基本api实现对象数据的读取、集群的动态管理。
 * 所有的api都是异步非阻塞的。
 *
 * @author RealDragonking
 */
public interface NovaIOClient {

    /**
     * 新增一个DataNode节点集群的配置，如果已经存在则新增失败。考虑到不稳定的网络环境和客户端连接池的需要，DataNode配置策略是客户端缓存，
     * 在发生更新时服务节点不会主动进行推送，而是由客户端定时主动轮询。因此我们不能保证更新后的DataNode配置能立刻同步到所有客户端
     *
     * @param clusterName 集群名称
     * @param addresses 所有节点的{@link InetSocketAddress}列表
     * @return {@link AsyncFuture}
     */
    AsyncFuture<OperationResult> addDataNodeCluster(String clusterName, InetSocketAddress[] addresses);

    /**
     * 删除一个DataNode节点集群，如果不存在则删除失败。考虑到不稳定的网络环境和客户端连接池的需要，DataNode配置策略是客户端缓存，
     * 在发生更新时服务节点不会主动进行推送，而是由客户端定时主动轮询。因此我们不能保证更新后的DataNode配置能立刻同步到所有客户端
     *
     * @param clusterName 集群名称
     * @return {@link AsyncFuture}
     */
    AsyncFuture<OperationResult> removeDataNodeCluster(String clusterName);

    /**
     * 安全且优雅地关闭客户端
     */
    void close();

}
