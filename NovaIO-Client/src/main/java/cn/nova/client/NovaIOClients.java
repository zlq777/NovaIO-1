package cn.nova.client;

import cn.nova.AsyncFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static cn.nova.CommonUtils.getThreadFactory;

/**
 * {@link NovaIOClients}作为一个工厂类，提供了创建{@link NovaIOClient}具体实现类的api
 *
 * @author RealDragonking
 */
public final class NovaIOClients {

    private static final int DEFAULT_UPDATE_INTERVAL = 10000;
    private static final int DEFAULT_RECONNECT_INTERVAL = 1000;
    private static final int DEFAULT_IO_THREAD_NUMBER = 2;
    private static final int MAX_FRAME_LENGTH = 65535;
    private static final int DEFAULT_TIMEOUT = 3000;

    private NovaIOClients() {}

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addressList NovaIO视图节点的连接地址列表
     * @return {@link NovaIOClient}
     */
    public static NovaIOClient create(List<InetSocketAddress> addressList){
        return create(addressList, DEFAULT_IO_THREAD_NUMBER, DEFAULT_TIMEOUT, DEFAULT_UPDATE_INTERVAL, DEFAULT_RECONNECT_INTERVAL);
    }

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addresses NovaIO视图节点的连接地址列表
     * @return {@link NovaIOClient}
     */
    public static NovaIOClient create(InetSocketAddress[] addresses){
        return create(addresses, DEFAULT_IO_THREAD_NUMBER, DEFAULT_TIMEOUT, DEFAULT_UPDATE_INTERVAL, DEFAULT_RECONNECT_INTERVAL);
    }

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addressList NovaIO视图节点的连接地址列表
     * @param ioThreadNumber io线程数量
     * @param timeout 响应超时时间
     * @param updateInterval DataNode集群信息更新时间
     * @param reconnectInterval 重连间隔时间
     * @return {@link NovaIOClient}
     */
    public static NovaIOClient create(List<InetSocketAddress> addressList,
                                      int ioThreadNumber, int timeout, int reconnectInterval, int updateInterval) {

        int nodeNumber = addressList.size();
        InetSocketAddress[] addresses = new InetSocketAddress[nodeNumber];

        for (int i = 0; i < nodeNumber; i++) {
            addresses[i] = addressList.get(i);
        }

        return create(addresses, ioThreadNumber, timeout, reconnectInterval, updateInterval);
    }

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addresses NovaIO视图节点的连接地址列表
     * @param ioThreadNumber io线程数量
     * @param timeout 响应超时时间
     * @param updateInterval DataNode集群信息更新时间
     * @param reconnectInterval 重连间隔时间
     * @return {@link NovaIOClient}
     */
    public static NovaIOClient create(InetSocketAddress[] addresses,
                                      int ioThreadNumber, int timeout, int updateInterval, int reconnectInterval) {

        EventLoopGroup ioThreadGroup = new NioEventLoopGroup(ioThreadNumber, getThreadFactory("io-thread", true));
        Map<Long, AsyncFuture<?>> sessionMap = new ConcurrentHashMap<>();

        Bootstrap bootstrap = new Bootstrap()
                .group(ioThreadGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4))
                                .addLast(new ResponseMsgHandler(sessionMap));
                    }
                });

        return new NovaIOClientImpl(sessionMap, addresses, ioThreadGroup, bootstrap, timeout, updateInterval, reconnectInterval);
    }

}
