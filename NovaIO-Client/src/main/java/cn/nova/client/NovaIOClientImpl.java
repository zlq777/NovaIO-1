package cn.nova.client;

import cn.nova.AsyncFuture;
import cn.nova.ByteBufMessage;
import cn.nova.DynamicCounter;
import cn.nova.client.result.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
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
    private final Map<String, RaftClusterClient> dataNodeClientMap;
    private final Map<Long, AsyncFuture<?>> sessionMap;
    private final RaftClusterClient viewNodeClient;
    private final EventLoopGroup ioThreadGroup;
    private final Bootstrap bootstrap;
    private final int timeout;
    private final int updateInterval;
    private final int reconnectInterval;

    NovaIOClientImpl(Map<Long, AsyncFuture<?>> sessionMap,
                     InetSocketAddress[] addresses,
                     EventLoopGroup ioThreadGroup,
                     Bootstrap bootstrap,
                     int timeout,
                     int updateInterval,
                     int reconnectInterval) {

        this.dataNodeClientMap = new ConcurrentHashMap<>();
        this.ioThreadGroup = ioThreadGroup;
        this.sessionMap = sessionMap;
        this.bootstrap = bootstrap;
        this.timeout = timeout;
        this.updateInterval = updateInterval;
        this.reconnectInterval = reconnectInterval;

        this.viewNodeClient = initViewNodeClient(addresses);
    }

    /**
     * 初始化一个连接到ViewNode节点集群的{@link RaftClusterClient}
     *
     * @param addresses ViewNode节点的连接地址列表
     * @return {@link RaftClusterClient}
     */
    private RaftClusterClient initViewNodeClient(InetSocketAddress[] addresses) {
        int nodeNumber = addresses.length;
        RaftClusterNode[] clusterNodes = new RaftClusterNode[nodeNumber];

        for (int i = 0; i < nodeNumber; i++) {
            clusterNodes[i] = new RaftClusterNode(addresses[i]);
        }

        RaftClusterClient client = new RaftClusterClient("ViewNode-Cluster", clusterNodes,
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

                        viewNodeClient.timer.newTimeout(t -> run(), updateInterval, TimeUnit.MILLISECONDS);
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

                int nodeNumber = addrSet.size();
                RaftClusterNode[] clusterNodes = new RaftClusterNode[nodeNumber];

                for (InetSocketAddress address : addrSet) {
                    clusterNodes[-- nodeNumber] = new RaftClusterNode(address);
                }

                RaftClusterClient client = new RaftClusterClient("DataNode-" + clusterName, clusterNodes, null);
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
        ioThreadGroup.shutdownGracefully();
        viewNodeClient.close();
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
        private final String clusterName;
        private final Timer timer;
        private volatile boolean leaderFirstUpdate;
        private volatile long leaderTerm;
        private Channel leaderChannel;

        private RaftClusterClient(String clusterName, RaftClusterNode[] clusterNodes, Runnable leaderFirstUpdateTask) {
            ThreadFactory timerThreadFactory = getThreadFactory(clusterName + "-Timer", false);

            this.leaderFirstUpdateTask = leaderFirstUpdateTask;
            this.leaderFirstUpdate = true;
            this.leaderTerm = -1L;

            this.updateLeaderState = new AtomicBoolean(true);
            this.pendingMessageQueue = new ConcurrentLinkedQueue<>();
            this.timer = new HashedWheelTimer(timerThreadFactory);

            this.clusterNodes = clusterNodes;
            this.clusterName = clusterName;
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
            synchronized (clusterNodes) {
                for (RaftClusterNode node : clusterNodes) {
                    if (node.channel == null && ! node.isConnecting) {

                        node.isConnecting = true;

                        ChannelFuture future = bootstrap.connect(node.address);
                        Channel channel = future.channel();

                        future.addListener(f -> {
                            if (f.isSuccess()) {
                                node.channel = channel;
                            } else {
                                log.info("无法连接到位于 {} 的 {} 节点，准备稍后重试...", node.address, clusterName);
                            }
                            node.isConnecting = false;
                        });
                    }
                }
            }
            timer.newTimeout(t -> loopConnect(), reconnectInterval, TimeUnit.MILLISECONDS);
        }

        /**
         * 尝试抢占{@link #updateLeaderState}标志锁，启动leader信息更新的进程
         */
        private void updateLeader() {
            if (updateLeaderState.compareAndSet(true, false)) {
                log.info("leader节点响应超时，正在进行更新操作...");
                leaderChannel = null;
                updateLeader0();
            }
        }

        /**
         * 具体更新leader信息。向所有{@link Channel}通信信道可用的节点，通过{@link #queryLeader(RaftClusterNode, DynamicCounter)}
         * 发送leader探测消息，当收到leader声明响应时，更新本地的leader节点信息
         */
        private void updateLeader0() {
            timer.newTimeout(t -> {
                DynamicCounter counter = new DynamicCounter() {
                    @Override
                    public void onAchieveTarget() {
                        if (leaderChannel == null) {
                            log.info("leader节点信息更新失败，准备稍后重试...");
                            updateLeader0();
                        } else {
                            log.info("成功更新leader节点信息，位于 {}", leaderChannel.remoteAddress());
                            checkLeaderFirstUpdate();
                            updateLeaderState.set(true);
                            flushPendingMessage();
                        }
                    }
                };

                synchronized (clusterNodes) {
                    for (RaftClusterNode node : clusterNodes) {
                        if (node.channel != null) {
                            queryLeader(node, counter);
                        }
                    }
                }

                counter.setTarget();

            }, reconnectInterval, TimeUnit.MILLISECONDS);
        }

        /**
         * 向目标节点发送leader探测消息，并修改对应当前探测活动的{@link DynamicCounter}的计数信息
         *
         * @param node {@link RaftClusterClient}
         * @param counter {@link DynamicCounter}
         */
        private void queryLeader(RaftClusterNode node, DynamicCounter counter) {
            AsyncFuture<QueryLeaderResult> asyncFuture = AsyncFuture.of(QueryLeaderResult.class);
            long sessionId = asyncFuture.getSessionId();

            sessionMap.put(sessionId, asyncFuture);

            ByteBufMessage message = ByteBufMessage
                    .build("/query-leader")
                    .doWrite(byteBuf -> byteBuf.writeLong(sessionId));

            Channel channel = node.channel;

            channel.writeAndFlush(message.create());
            counter.addTarget();

            asyncFuture.addListener(result -> {
                if (result == null) {
                    synchronized (clusterNodes) {
                        if (node.channel != null && channel == node.channel) {
                            node.channel = null;
                            channel.close();
                        }
                    }
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
            timer.stop();
            synchronized (clusterNodes) {
                for (RaftClusterNode node : clusterNodes) {
                    if (node.channel != null) {
                        node.channel.close();
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

    /**
     * {@link RaftClusterNode}描述了一个raft集群节点的基本信息和连接状态
     *
     * @author RealDragonking
     */
    private static class RaftClusterNode {
        private final InetSocketAddress address;
        private volatile boolean isConnecting;
        private Channel channel;
        private RaftClusterNode(InetSocketAddress address) {
            this.address = address;
            this.isConnecting = false;
        }
    }

}
