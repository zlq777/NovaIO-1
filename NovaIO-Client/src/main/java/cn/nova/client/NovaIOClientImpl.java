package cn.nova.client;

import cn.nova.AsyncFuture;
import cn.nova.ByteBufMessage;
import cn.nova.DynamicCounter;
import cn.nova.client.response.*;
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
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static cn.nova.CommonUtils.getThreadFactory;

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

        Runnable task = new UpdateDataNodeInfoTask();
        this.viewNodeClient = new RaftClusterClient("ViewNode-Cluster", addresses);
        this.viewNodeClient.setLeaderFirstUpdateTask(task);
        this.viewNodeClient.loopConnect();
    }

    /**
     * 往一个DataNode集群的信息结构体中，加入一个新节点的{@link InetSocketAddress}
     *
     * @param clusterName 集群名称，如果不存在会进行创建
     * @param address     {@link InetSocketAddress}
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<ChangeDataNodeInfoResult> addNewDataNode(String clusterName, InetSocketAddress address) {
        AsyncFuture<ChangeDataNodeInfoResult> asyncFuture = AsyncFuture.of(ChangeDataNodeInfoResult.class);
        return null;
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
     * {@link UpdateDataNodeInfoTask}需要被挂载到面向ViewNode节点集群的{@link RaftClusterClient}上，
     * 会在第一次获取到ViewNode节点集群的leader信息时，通过{@link RaftClusterClient#onLeaderFirstUpdate()}
     * 被回调执行一次，启动对DataNode节点集群们的信息动态轮询
     *
     * @author RealDragonking
     */
    private class UpdateDataNodeInfoTask implements Runnable {
        @Override
        public void run() {
            AsyncFuture<UpdateDataNodeInfoResult> asyncFuture = AsyncFuture.of(UpdateDataNodeInfoResult.class);
            long sessionId = asyncFuture.getSessionId();

            ByteBufMessage message = ByteBufMessage
                    .build("/update-datanode-info")
                    .doWrite(byteBuf -> byteBuf.writeLong(sessionId));

            viewNodeClient.sendMessage(message.create(), asyncFuture);

            asyncFuture.addListener(result -> {
                //
            });

            viewNodeClient.timer.newTimeout(t -> run(), updateInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * {@link RaftClusterClient}实现了一个能够和raft集群进行通信的、通用可靠的客户端。
     * <p>我们只会和leader节点进行读写操作，当leader节点表示自己已经失去了leader身份时，我们会使用leader节点返回的新leader的index，
     * 更新通信信道。</p>
     * <p>如果leader节点响应超时，那么我们会通过{@link #updateLeader()}启动对其它节点的leader身份探测。这种探测可能会面临以下两种情况：
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
        private final InetSocketAddress[] addresses;
        private final boolean[] channelStates;
        private final Lock updateLeaderLocker;
        private final Channel[] channels;
        private final String clusterName;
        private final Timer timer;
        private final int nodeNumber;
        private volatile boolean leaderFirstUpdate;
        private volatile long leaderTerm;
        private Runnable leaderFirstUpdateTask;
        private Channel leaderChannel;

        private RaftClusterClient(String clusterName, InetSocketAddress[] addresses) {

            ThreadFactory timerThreadFactory = getThreadFactory(clusterName + "-Timer", false);
            this.nodeNumber = addresses.length;
            this.leaderFirstUpdate = true;
            this.leaderTerm = -1L;

            this.updateLeaderState = new AtomicBoolean(true);
            this.timer = new HashedWheelTimer(timerThreadFactory);
            this.pendingMessageQueue = new ConcurrentLinkedQueue<>();
            this.updateLeaderLocker = new ReentrantLock();

            this.channelStates = new boolean[nodeNumber];
            this.channels = new Channel[nodeNumber];

            this.clusterName = clusterName;
            this.addresses = addresses;
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
            for (int i = 0; i < nodeNumber; i++) {
                if (channels[i] == null && ! channelStates[i]) {

                    channelStates[i] = true;

                    ChannelFuture future = bootstrap.connect(addresses[i]);
                    Channel channel = future.channel();
                    int channelIdx = i;

                    future.addListener(f -> {
                        if (f.isSuccess()) {
                            channels[channelIdx] = channel;
                        } else {
                            log.info("无法连接到位于{}的{}节点，准备稍后重试...", addresses[channelIdx], clusterName);
                        }
                        channelStates[channelIdx] = false;
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
                log.info("{}-leader节点响应超时，正在进行更新操作...", clusterName);
                leaderChannel = null;
                updateLeader0();
            }
        }

        /**
         * 具体更新leader信息。向所有{@link Channel}通信信道可用的节点，通过{@link #queryLeader(Channel, int, DynamicCounter)}
         * 发送leader探测消息，当收到leader声明响应时，更新本地的leader节点信息
         */
        private void updateLeader0() {
            timer.newTimeout(t -> {
                DynamicCounter counter = new DynamicCounter() {
                    @Override
                    public void onAchieveTarget() {
                        if (leaderChannel == null) {
                            log.info("{}-leader节点信息更新失败，准备稍后重试...", clusterName);
                            updateLeader0();
                        } else {
                            log.info("成功更新{}-leader节点信息，位于{}", clusterName, leaderChannel.remoteAddress());
                            if (leaderFirstUpdate) {
                                onLeaderFirstUpdate();
                            }
                            updateLeaderState.set(true);
                            flushPendingMessage();
                        }
                    }
                };

                for (int i = 0; i < channels.length; i++) {
                    Channel channel = channels[i];
                    if (channel != null) {
                        queryLeader(channel, i, counter);
                    }
                }

                counter.setTarget();

            }, reconnectInterval, TimeUnit.MILLISECONDS);
        }

        /**
         * 向目标节点发送leader探测消息，并修改对应当前探测活动的{@link DynamicCounter}的计数信息
         *
         * @param channel {@link Channel}通信信道
         * @param channelIdx 节点{@link Channel}在{@link #channels}中的索引下标
         * @param counter {@link DynamicCounter}
         */
        private void queryLeader(Channel channel, int channelIdx, DynamicCounter counter) {
            AsyncFuture<UpdateLeaderResult> asyncFuture = AsyncFuture.of(UpdateLeaderResult.class);
            long sessionId = asyncFuture.getSessionId();

            sessionMap.put(sessionId, asyncFuture);

            ByteBufMessage message = ByteBufMessage
                    .build("/query-leader")
                    .doWrite(byteBuf -> byteBuf.writeLong(sessionId));

            channel.writeAndFlush(message.create());

            counter.addTarget();

            asyncFuture.addListener(result -> {
                if (result == null) {
                    channels[channelIdx] = null;
                    channel.close();
                } else {
                    updateLeaderLocker.lock();
                    if (result.isLeader() && result.getTerm() >= leaderTerm) {
                        leaderTerm = result.getTerm();
                        leaderChannel = channel;
                    }
                    updateLeaderLocker.unlock();
                }
                counter.addCount();
            });

            timer.newTimeout(t -> {
                sessionMap.remove(sessionId);
                asyncFuture.notifyResult(null);
            }, timeout, TimeUnit.MILLISECONDS);
        }

        /**
         * 设置作用于第一次获取到集群leader节点信息时刻的{@link Runnable}回调方法
         *
         * @param task {@link Runnable}任务
         */
        private void setLeaderFirstUpdateTask(Runnable task) {
            this.leaderFirstUpdateTask = task;
        }

        /**
         * 执行作用于第一次获取到集群leader节点信息时刻的{@link Runnable}回调方法
         */
        private void onLeaderFirstUpdate() {
            if (leaderFirstUpdateTask != null) {
                leaderFirstUpdateTask.run();
            }
            leaderFirstUpdate = false;
        }

        /**
         * 安全且优雅地关闭此{@link RaftClusterClient}
         */
        private void close() {
            timer.stop();
        }

        /**
         * 正在等待发送的消息
         *
         * @author RealDragonking
         */
        private class PendingMessage {
            private final ByteBuf byteBuf;
            private final AsyncFuture<?> asyncFuture;
            private PendingMessage(ByteBuf byteBuf, AsyncFuture<?> asyncFuture) {
                this.byteBuf = byteBuf;
                this.asyncFuture = asyncFuture;
            }
        }

    }

}
