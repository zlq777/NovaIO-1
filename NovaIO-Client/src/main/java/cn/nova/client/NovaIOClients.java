package cn.nova.client;

import cn.nova.AsyncFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private static final Logger log = LogManager.getLogger(NovaIOClients.class);
    private static final int DEFAULT_RECONNECT_INTERVAL = 1000;
    private static final int DEFAULT_INIT_TIMEOUT = 10000;
    private static final int DEFAULT_IO_THREAD_NUMBER = 2;
    private static final int MAX_FRAME_LENGTH = 65535;
    private static final int DEFAULT_TIMEOUT = 3000;

    private NovaIOClients() {}

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addressList NovaIO视图节点的连接地址列表
     * @return {@link NovaIOClient}
     * @exception Exception 客户端和ViewNode集群的Leader节点连接创建失败引发的异常
     */
    public static NovaIOClient create(List<InetSocketAddress> addressList) throws Exception {
        return create(addressList, DEFAULT_IO_THREAD_NUMBER, DEFAULT_TIMEOUT, DEFAULT_RECONNECT_INTERVAL, DEFAULT_INIT_TIMEOUT);
    }

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addresses NovaIO视图节点的连接地址列表
     * @return {@link NovaIOClient}
     * @exception Exception 客户端和ViewNode集群的Leader节点连接创建失败引发的异常
     */
    public static NovaIOClient create(InetSocketAddress[] addresses) throws Exception{
        return create(addresses, DEFAULT_IO_THREAD_NUMBER, DEFAULT_TIMEOUT, DEFAULT_RECONNECT_INTERVAL,DEFAULT_INIT_TIMEOUT);
    }

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addressList NovaIO视图节点的连接地址列表
     * @param ioThreadNumber io线程数量
     * @param timeout 响应超时时间
     * @param reconnectInterval 重连间隔时间
     * @param initTimeout 初始化超时时间
     * @return {@link NovaIOClient}
     * @exception Exception 客户端和ViewNode集群的Leader节点连接创建失败引发的异常
     */
    public static NovaIOClient create(List<InetSocketAddress> addressList,
                                      int ioThreadNumber, int timeout, int reconnectInterval, int initTimeout)
            throws Exception {

        int nodeNumber = addressList.size();
        InetSocketAddress[] addresses = new InetSocketAddress[nodeNumber];

        for (int i = 0; i < nodeNumber; i++) {
            addresses[i] = addressList.get(i);
        }

        return create(addresses, ioThreadNumber, timeout, reconnectInterval, initTimeout);
    }

    /**
     * 根据给定的{@link InetSocketAddress}列表，创建连接到目标NovaIO视图节点集群的{@link NovaIOClient}
     *
     * @param addresses NovaIO视图节点的连接地址列表
     * @param ioThreadNumber io线程数量
     * @param timeout 响应超时时间
     * @param reconnectInterval 重连间隔时间
     * @param initTimeout 初始化超时时间
     * @return {@link NovaIOClient}
     * @exception Exception 客户端和ViewNode集群的Leader节点连接创建失败引发的异常
     */
    public static NovaIOClient create(InetSocketAddress[] addresses,
                                      int ioThreadNumber, int timeout, int reconnectInterval, int initTimeout)
            throws Exception {

        ThreadFactory threadFactory = getThreadFactory("NovaIO-Client", true);
        int nodeNumber = addresses.length;

        Map<Long, AsyncFuture<?>> sessionMap = new ConcurrentHashMap<>();
        Channel[] channels = new Channel[nodeNumber];

        EventLoopGroup ioThreadGroup = new NioEventLoopGroup(ioThreadNumber, threadFactory);
        Timer timer = new HashedWheelTimer(threadFactory);

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

        TimerTask reconnectViewNodeTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) {
                for (int i = 0; i < nodeNumber; i++) {
                    if (channels[i] == null) {
                        InetSocketAddress address = addresses[i];
                        try {
                            channels[i] = bootstrap.connect(address).sync().channel();
                        } catch (Exception e) {
                            log.info("尝试连接到位于 " + address + " 的ViewNode失败，准备稍后重试...");
                        }
                    }
                }
                timer.newTimeout(this, reconnectInterval, TimeUnit.MILLISECONDS);
            }
        };

        reconnectViewNodeTask.run(null);

        NovaIOClientImpl clientImpl = new NovaIOClientImpl(ioThreadGroup, timer, sessionMap, bootstrap, channels, timeout);

        return clientImpl;
    }

}
