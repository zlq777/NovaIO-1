package cn.nova.client;

import cn.nova.AsyncFuture;
import cn.nova.ByteBufMessage;
import cn.nova.DynamicCounter;
import cn.nova.client.result.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.nova.CommonUtils.*;

/**
 * {@link NovaIOClient}的默认实现类
 *
 * @author RealDragonking
 */
final class NovaIOClientImpl implements NovaIOClient {

    private static final Logger log = LogManager.getLogger(NovaIOClientImpl.class);
    private static final int MAX_FRAME_LENGTH = 65535;
    private final Map<String, RaftClusterClient> dataNodeClientMap;
    private final Map<Long, AsyncFuture<?>> sessionMap;
    private final RaftClusterClient viewNodeClient;
    private final ResponseHandler responseHandler;
    private final EventLoopGroup ioThreadGroup;
    private final Bootstrap bootstrap;
    private final Timer timer;
    private final int timeout;
    private final int updateInterval;
    private final int reconnectInterval;

    NovaIOClientImpl(Collection<InetSocketAddress> addresses,
                     int timeout,
                     int ioThreadNumber,
                     int updateInterval,
                     int reconnectInterval) {

        ThreadFactory timerThreadFactory = getThreadFactory("NovaIO-Timer", false);
        ThreadFactory ioThreadFactory = getThreadFactory("NovaIO-io", true);

        this.ioThreadGroup = new NioEventLoopGroup(ioThreadNumber, ioThreadFactory);
        this.timer = new HashedWheelTimer(timerThreadFactory);
        this.dataNodeClientMap = new ConcurrentHashMap<>();
        this.sessionMap = new ConcurrentHashMap<>();
        this.reconnectInterval = reconnectInterval;
        this.updateInterval = updateInterval;
        this.timeout = timeout;

        this.responseHandler = new ResponseHandler(sessionMap);
        this.bootstrap = new Bootstrap()
                .group(ioThreadGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4))
                                .addLast(responseHandler);
                    }
                });

        this.viewNodeClient = initViewNodeClient(addresses);
    }

    /**
     * 初始化一个连接到ViewNode节点集群的{@link RaftClusterClient}
     *
     * @param addresses ViewNode节点的连接地址列表
     * @return {@link RaftClusterClient}
     */
    private RaftClusterClient initViewNodeClient(Collection<InetSocketAddress> addresses) {
        RaftClusterClient client = new RaftClusterClient("ViewNode", addresses,
                new Runnable() {
                    @Override
                    public void run() {
                        AsyncFuture<QueryDataNodeInfoResult> asyncFuture = AsyncFuture.of(QueryDataNodeInfoResult.class);
                        long sessionId = asyncFuture.getSessionId();

                        ByteBufMessage message = ByteBufMessage.build("/query-datanode-info")
                                .doWrite(msg -> msg.writeLong(sessionId));

                        viewNodeClient.sendMessage(message.create(), asyncFuture);

                        asyncFuture.addListener(result -> {
                            if (result != null) {
                                updateDataNodeInfo(result.getDataNodeInfoMap());
                            }
                        });

                        timer.newTimeout(t -> run(), updateInterval, TimeUnit.MILLISECONDS);
                    }
                });

        client.loopConnect();
        client.updateLeader();
        return client;
    }

    /**
     * 根据给定记录有DataNode集群信息的{@link Map}，比较并更新{@link #dataNodeClientMap}
     *
     * @param infoMap 记录有DataNode集群信息的{@link Map}
     */
    private void updateDataNodeInfo(Map<String, Set<InetSocketAddress>> infoMap) {
        synchronized (dataNodeClientMap) {
            for (String clusterName : dataNodeClientMap.keySet()) {
                if (infoMap.containsKey(clusterName)) {
                    infoMap.remove(clusterName);
                } else {
                    dataNodeClientMap.remove(clusterName).close();
                    log.info("感知到DataNode集群配置变动, 集群 {} 已经下线", clusterName);
                }
            }

            for (Map.Entry<String, Set<InetSocketAddress>> entry : infoMap.entrySet()) {
                Set<InetSocketAddress> addrSet = entry.getValue();
                String clusterName = entry.getKey();

                RaftClusterClient client = new RaftClusterClient(clusterName, addrSet, null);
                dataNodeClientMap.put(clusterName, client);
                log.info("感知到DataNode集群配置变动, 发现新集群 {}", clusterName);

                client.loopConnect();
                client.updateLeader();
            }
        }
    }

    /**
     * 新增一个DataNode节点集群，如果已经存在则创建失败
     *
     * @param clusterName 集群名称
     * @param addresses 所有节点的{@link InetSocketAddress}列表
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<ChangeDataNodeInfoResult> addNewDataNodeCluster(String clusterName, InetSocketAddress[] addresses) {
        AsyncFuture<ChangeDataNodeInfoResult> asyncFuture = AsyncFuture.of(ChangeDataNodeInfoResult.class);
        long sessionId = asyncFuture.getSessionId();

        ByteBufMessage message = ByteBufMessage.build("/add-datanode-cluster").doWrite(msg -> {
            msg.writeLong(sessionId);
            writeString(msg, clusterName);
            msg.writeInt(addresses.length);
        });

        for (InetSocketAddress address : addresses) {
            message.doWrite(msg -> {
                String ipAddress = address.getAddress().getHostAddress();
                int port = address.getPort();

                writeString(msg, ipAddress);
                msg.writeInt(port);
            });
        }

        viewNodeClient.sendMessage(message.create(), asyncFuture);
        return asyncFuture;
    }

    /**
     * 删除一个DataNode节点集群，如果不存在则删除失败
     *
     * @param clusterName 集群名称
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<ChangeDataNodeInfoResult> removeDataNodeCluster(String clusterName) {
        AsyncFuture<ChangeDataNodeInfoResult> asyncFuture = AsyncFuture.of(ChangeDataNodeInfoResult.class);
        long sessionId = asyncFuture.getSessionId();

        ByteBufMessage message = ByteBufMessage.build("/remove-datanode-cluster").doWrite(msg -> {
            msg.writeLong(sessionId);
            writeString(msg, clusterName);
        });

        viewNodeClient.sendMessage(message.create(), asyncFuture);
        return asyncFuture;
    }

    /**
     * 安全且优雅地关闭客户端
     */
    @Override
    public void close() {
        timer.stop();
        viewNodeClient.close();
        ioThreadGroup.shutdownGracefully();

        for (RaftClusterClient client : dataNodeClientMap.values()) {
            client.close();
        }
    }

    /**
     * {@link RaftClusterClient}实现了一个能够和raft集群进行通信的、通用可靠的客户端。
     * <p>我们只会和leader节点进行读写操作，当leader节点响应超时，我们会通过{@link #updateLeader()}启动对其它节点的leader身份探测。
     * 这种探测可能会面临以下两种情况：
     * <ul>
     *     <li>
     *         有节点表示自己是新任leader。这里可能会遇到任期不同的leader身份宣布，我们只信任任期最大的那个
     *     </li>
     *     <li>
     *         所有节点都不知道leader是谁，那么间隔一段时间后重试
     *     </li>
     * </ul>
     * 在这过程中，可能会有节点响应超时，我们只需要删除对应的{@link Channel}通信信道，等待自动重连就行。
     * </p>
     *
     * @author RealDragonking
     */
    private class RaftClusterClient {

        private final Queue<PendingMessage> pendingMessageQueue;
        private final AtomicBoolean updateLeaderState;
        private final Runnable leaderFirstUpdateTask;
        private final RaftClusterNode[] clusterNodes;
        private volatile boolean leaderFirstUpdate;
        private volatile long leaderTerm;
        private Channel leaderChannel;

        private RaftClusterClient(String clusterName,
                                  Collection<InetSocketAddress> addresses,
                                  Runnable leaderFirstUpdateTask) {

            this.updateLeaderState = new AtomicBoolean(true);
            this.pendingMessageQueue = new ConcurrentLinkedQueue<>();

            this.clusterNodes = new RaftClusterNode[addresses.size()];
            this.leaderFirstUpdateTask = leaderFirstUpdateTask;
            this.leaderFirstUpdate = true;
            this.leaderTerm = -1L;

            int i = 0;
            for (InetSocketAddress address : addresses) {
                clusterNodes[i ++] = new RaftClusterNode(clusterName, address);
            }
        }

        /**
         * 尝试获取到控制消息发送的标志位（锁），如果标志位修改失败，则加入{@link #pendingMessageQueue}等待延迟发送。
         * 请确保{@link ByteBuf}已经完成写入头部的长度字段和路径字段、sessionId
         *
         * @param byteBuf {@link ByteBuf}字节缓冲区
         * @param asyncFuture {@link AsyncFuture}
         */
        private void sendMessage(ByteBuf byteBuf, AsyncFuture<?> asyncFuture) {
            synchronized (this) {
                if (updateLeaderState.compareAndSet(true, false)) {
                    sendMessage0(byteBuf, asyncFuture);
                    updateLeaderState.set(true);
                } else {
                    PendingMessage pending = new PendingMessage(byteBuf, asyncFuture);
                    pendingMessageQueue.offer(pending);
                }
            }
        }

        /**
         * 如果{@link #pendingMessageQueue}不为空的话，从中取出一条消息调用{@link #sendMessage0(ByteBuf, AsyncFuture)}进行发送
         */
        private void flushPendingMessage() {
            if (! pendingMessageQueue.isEmpty()) {
                PendingMessage pending;
                while ((pending = pendingMessageQueue.poll()) != null) {
                    sendMessage(pending.byteBuf, pending.asyncFuture);
                }
            }
        }

        /**
         * 尝试向leader节点发送一个完整的{@link ByteBuf}消息，并启动超时计时。一旦消息响应超时，
         * 那么启动{@link #updateLeader()}进程
         *
         * @param byteBuf {@link ByteBuf}字节缓冲区
         * @param asyncFuture {@link AsyncFuture}
         */
        private void sendMessage0(ByteBuf byteBuf, AsyncFuture<?> asyncFuture) {
            long sessionId = asyncFuture.getSessionId();
            sessionMap.put(sessionId, asyncFuture);

            leaderChannel.writeAndFlush(byteBuf);

            asyncFuture.addListener(result -> {
                if (result == null) {
                    updateLeader();
                }
            });

            timer.newTimeout(t -> {
                sessionMap.remove(sessionId);
                asyncFuture.notifyResult(null);
            }, timeout, TimeUnit.MILLISECONDS);
        }

        /**
         * 循环检测和集群节点的{@link Channel}通信信道是否活跃，并执行重连操作
         */
        private void loopConnect() {
            for (RaftClusterNode node : clusterNodes) {
                if (node.channel == null && node.connectMonitor.compareAndSet(false, true)) {

                    ChannelFuture future = bootstrap.connect(node.address);
                    Channel channel = future.channel();

                    future.addListener(f -> {
                        node.setChannel(channel, f.isSuccess());
                        node.connectMonitor.set(false);
                    });
                }
            }
            timer.newTimeout(t -> loopConnect(), reconnectInterval, TimeUnit.MILLISECONDS);
        }

        /**
         * 尝试抢占{@link #updateLeaderState}标志锁，启动leader信息更新的进程
         */
        private void updateLeader() {
            if (updateLeaderState.compareAndSet(true, false)) {
                if (leaderFirstUpdate) {
                    log.info("正在初始化更新leader节点位置");
                } else {
                    log.info("当前leader节点响应超时，正在进行位置更新操作...");
                }
                leaderChannel = null;
                updateLeader0();
            }
        }

        /**
         * 具体更新leader信息。向所有{@link Channel}通信信道可用的节点，通过{@link #queryLeader(RaftClusterNode, Channel, DynamicCounter)}
         * 发送leader探测消息，当收到leader声明响应时，更新本地的leader节点信息
         */
        private void updateLeader0() {
            timer.newTimeout(t -> {
                DynamicCounter counter = new DynamicCounter() {
                    @Override
                    public void onAchieveTarget() {
                        if (leaderChannel == null) {
                            log.info("leader节点位置更新失败，准备稍后重试...");
                            updateLeader0();
                        } else {
                            log.info("成功更新leader节点位置，位于 {}", leaderChannel.remoteAddress());
                            checkLeaderFirstUpdate();
                            updateLeaderState.set(true);
                            flushPendingMessage();
                        }
                    }
                };

                for (RaftClusterNode node : clusterNodes) {
                    queryLeader(node, node.channel, counter);
                }

                counter.setTarget();

            }, reconnectInterval, TimeUnit.MILLISECONDS);
        }

        /**
         * 向目标节点发送leader探测消息，并修改对应当前探测活动的{@link DynamicCounter}的计数信息
         *
         * @param node {@link RaftClusterClient}
         * @param channel {@link Channel}
         * @param counter {@link DynamicCounter}
         */
        private void queryLeader(RaftClusterNode node, Channel channel, DynamicCounter counter) {
            if (channel != null) {
                AsyncFuture<QueryLeaderResult> asyncFuture = AsyncFuture.of(QueryLeaderResult.class);
                long sessionId = asyncFuture.getSessionId();

                sessionMap.put(sessionId, asyncFuture);

                ByteBufMessage message = ByteBufMessage
                        .build("/query-leader")
                        .doWrite(byteBuf -> byteBuf.writeLong(sessionId));

                channel.writeAndFlush(message.create());
                counter.addTarget();

                asyncFuture.addListener(result -> {
                    if (result == null) {
                        node.compareAndDisconnect(channel);
                    } else {
                        synchronized (this) {
                            if (result.isLeader() && result.getTerm() >= leaderTerm) {
                                leaderTerm = result.getTerm();
                                leaderChannel = channel;
                            }
                        }
                    }
                    counter.addCount();
                });

                timer.newTimeout(t -> {
                    sessionMap.remove(sessionId);
                    asyncFuture.notifyResult(null);
                }, timeout, TimeUnit.MILLISECONDS);
            }
        }

        /**
         * 检查是否是第一次成功更新leader节点的信息，如果是的话执行{@link #leaderFirstUpdateTask}
         */
        private void checkLeaderFirstUpdate() {
            if (leaderFirstUpdate) {
                if (leaderFirstUpdateTask != null) {
                    leaderFirstUpdateTask.run();
                }
                leaderFirstUpdate = false;
            }
        }

        /**
         * 安全且优雅地关闭此{@link RaftClusterClient}
         */
        private void close() {
            for (RaftClusterNode node : clusterNodes) {
                node.isClosed = true;
                node.disconnect();
            }
        }

        /**
         * {@link RaftClusterNode}描述了一个raft集群节点的基本信息和连接状态，并提供了一些线程安全的方法
         * 方便我们进行状态的切换和完成一些特定操作
         *
         * @author RealDragonking
         */
        private class RaftClusterNode {

            private final AtomicBoolean connectMonitor;
            private final InetSocketAddress address;
            private final String clusterName;
            private volatile boolean isClosed;
            private Channel channel;

            private RaftClusterNode(String clusterName, InetSocketAddress address) {
                this.connectMonitor = new AtomicBoolean();
                this.clusterName = clusterName;
                this.address = address;
                this.isClosed = false;
            }

            /**
             * 在线程安全的前提下，检查{@link #isClosed}标志位并替换当前的{@link #channel}通信信道
             *
             * @param channel {@link Channel}通信信道
             * @param isSuccess {@link Channel}是否有效（即连接创建操作是否成功）
             */
            private void setChannel(Channel channel, boolean isSuccess) {
                synchronized (this) {
                    if (isSuccess) {
                        if (isClosed) {
                            channel.close();
                        } else {
                            this.channel = channel;
                            log.info("成功连接到 {} 集群中的 {} 节点", clusterName, address);
                        }
                    } else {
                        if (! isClosed) {
                            log.info("无法连接到 {} 集群中的 {} 节点，准备稍后重试...", clusterName, address);
                        }
                    }
                }
            }

            /**
             * 在线程安全的前提下执行通过{@link Channel#close()}完成连接断开操作
             */
            private void disconnect() {
                synchronized (this) {
                    if (channel != null) {
                        channel.close();
                        channel = null;
                        log.info("客户端关闭，与 {} 集群中的 {} 节点连接自动断开", clusterName, address);
                    }
                }
            }

            /**
             * 进行比较并断开操作，如果{@link #channel}不为null，并且拿来比较的{@link Channel}
             * 和{@link #channel}内存地址相同的话，我们将通过{@link Channel#close()}完成连接断开操作
             *
             * @param channel {@link Channel}通信信道
             */
            private void compareAndDisconnect(Channel channel) {
                synchronized (this) {
                    if (this.channel != null && channel == this.channel) {
                        this.channel.close();
                        this.channel = null;
                        log.info("响应超时，与 {} 集群中的 {} 节点连接自动断开", clusterName, address);
                    }
                }
            }

        }

    }

    /**
     * {@link PendingMessage}描述了正在等待从客户端发送到NovaIO服务节点的消息
     *
     * @author RealDragonking
     */
    private static class PendingMessage {
        private final ByteBuf byteBuf;
        private final AsyncFuture<?> asyncFuture;
        private PendingMessage(ByteBuf byteBuf, AsyncFuture<?> asyncFuture) {
            this.byteBuf = byteBuf;
            this.asyncFuture = asyncFuture;
        }
    }

}
