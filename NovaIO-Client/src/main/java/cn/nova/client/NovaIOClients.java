package cn.nova.client;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * {@link NovaIOClients}作为一个工厂类，提供了创建{@link NovaIOClient}具体实现类的api
 *
 * @author RealDragonking
 */
public final class NovaIOClients {

    private static final int DEFAULT_UPDATE_INTERVAL = 10000;
    private static final int DEFAULT_RECONNECT_INTERVAL = 1000;
    private static final int DEFAULT_IO_THREAD_NUMBER = 2;
    private static final int DEFAULT_TIMEOUT = 3000;

    private NovaIOClients() {}

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addressList NovaIO视图节点的连接地址列表
     * @return {@link NovaIOClient}
     */
    public static NovaIOClient create(List<InetSocketAddress> addressList){
        return create(addressList, DEFAULT_TIMEOUT, DEFAULT_IO_THREAD_NUMBER, DEFAULT_UPDATE_INTERVAL, DEFAULT_RECONNECT_INTERVAL);
    }

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addresses NovaIO视图节点的连接地址列表
     * @return {@link NovaIOClient}
     */
    public static NovaIOClient create(InetSocketAddress[] addresses){
        return create(addresses, DEFAULT_TIMEOUT, DEFAULT_IO_THREAD_NUMBER, DEFAULT_UPDATE_INTERVAL, DEFAULT_RECONNECT_INTERVAL);
    }

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addressList NovaIO视图节点的连接地址列表
     * @param timeout 响应超时时间
     * @param ioThreadNumber io线程数量
     * @param updateInterval DataNode集群信息更新时间
     * @param reconnectInterval 重连间隔时间
     * @return {@link NovaIOClient}
     */
    public static NovaIOClient create(List<InetSocketAddress> addressList,
                                      int timeout, int ioThreadNumber, int reconnectInterval, int updateInterval) {

        int nodeNumber = addressList.size();
        InetSocketAddress[] addresses = new InetSocketAddress[nodeNumber];

        for (int i = 0; i < nodeNumber; i++) {
            addresses[i] = addressList.get(i);
        }

        return create(addresses, timeout, ioThreadNumber, reconnectInterval, updateInterval);
    }

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param ioThreadNumber io线程数量
     * @param addresses NovaIO视图节点的连接地址列表
     * @param timeout 响应超时时间
     * @param updateInterval DataNode集群信息更新时间
     * @param reconnectInterval 重连间隔时间
     * @return {@link NovaIOClient}
     */
    public static NovaIOClient create(InetSocketAddress[] addresses,
                                      int timeout, int ioThreadNumber, int updateInterval, int reconnectInterval) {
        return new NovaIOClientImpl(addresses, timeout, ioThreadNumber, updateInterval, reconnectInterval);
    }

}
