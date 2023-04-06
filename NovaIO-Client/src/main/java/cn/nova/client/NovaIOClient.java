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
     * 往一个DataNode集群的信息结构体中，加入一个新节点的{@link InetSocketAddress}
     *
     * @param clusterName 集群名称，如果不存在会进行创建
     * @param address {@link InetSocketAddress}
     * @return {@link AsyncFuture}
     */
    AsyncFuture<ChangeDataNodeInfoResult> addNewDataNode(String clusterName, InetSocketAddress address);

    /**
     * 安全且优雅地关闭客户端
     */
    void close();

}
