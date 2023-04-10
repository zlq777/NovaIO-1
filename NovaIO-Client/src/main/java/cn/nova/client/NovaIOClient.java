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
     * <p>新增一个DataNode节点集群的配置，如果已经存在则新增失败。</p>
     * <p>考虑到不稳定的网络环境，DataNode集群配置在发生更新时不会由服务节点主动进行消息推送，而是由客户端定时主动轮询并把结果缓存在本地。
     * 因此我们不能保证更新后的DataNode配置能立刻同步到所有客户端。</p>
     *
     * @param clusterName 集群名称
     * @param addresses 所有节点的{@link InetSocketAddress}列表
     * @return {@link AsyncFuture}
     */
    AsyncFuture<OperationResult> addDataNodeCluster(String clusterName, InetSocketAddress[] addresses);

    /**
     * <p>删除一个DataNode节点集群，如果不存在则删除失败。</p>
     * <p>考虑到不稳定的网络环境，DataNode集群配置在发生更新时不会由服务节点主动进行消息推送，而是由客户端定时主动轮询并把结果缓存在本地。
     * 因此我们不能保证更新后的DataNode配置能立刻同步到所有客户端。</p>
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
