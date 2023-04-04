package cn.nova.client;

import cn.nova.AsyncFuture;
import cn.nova.AsyncFutureImpl;
import cn.nova.ByteBufMessage;
import cn.nova.DynamicCounter;
import cn.nova.client.response.AppendNewEntryResult;
import cn.nova.client.response.QueryLeaderResult;
import cn.nova.client.response.ReadEntryResult;
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
    private final Map<Long, AsyncFuture<?>> sessionMap;
    private final RaftClusterClient viewNodeClient;
    private final EventLoopGroup ioThreadGroup;
    private final Bootstrap bootstrap;
    private final int timeout;
    private final int reconnectInterval;

    NovaIOClientImpl(Map<Long, AsyncFuture<?>> sessionMap,
                     InetSocketAddress[] addresses,
                     EventLoopGroup ioThreadGroup,
                     Bootstrap bootstrap,
                     int timeout,
                     int reconnectInterval) {
        this.ioThreadGroup = ioThreadGroup;
        this.sessionMap = sessionMap;
        this.bootstrap = bootstrap;

        this.timeout = timeout;
        this.reconnectInterval = reconnectInterval;

        this.viewNodeClient = new RaftClusterClient("ViewNode-Cluster", addresses);
        this.viewNodeClient.tryConnect();
    }

    /**
     * 根据给定的Entry序列号，从所有集群中读取对应的Entry块数据
     *
     * @param entryIndex Entry序列号
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<ReadEntryResult> readEntry(long entryIndex) {
        AsyncFuture<ReadEntryResult> asyncFuture = new AsyncFutureImpl<>(ReadEntryResult.class);
        ByteBufMessage message = ByteBufMessage
                .build("/read-entry")
                .doWrite(byteBuf -> {
                    byteBuf.writeLong(asyncFuture.getSessionId());

                });

        //

        return asyncFuture;
    }

    /**
     * 将给定的Entry块数据写入所有集群，将尽最大可能在单次传输中写入更多的字节（上限32kb即32768字节）
     *
     * @param entryData Entry块数据
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<AppendNewEntryResult> appendNewEntry(ByteBuf entryData) {
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
     * {@link RaftClusterClient}实现了一个能够和raft集群进行通信的、通用可靠的客户端。
     * <p>我们只会和leader节点进行读写操作，当leader节点表示自己已经失去了leader身份时，我们会使用leader节点返回的新leader的index，
     * 更新通信信道。</p>
     * <p>如果leader节点响应超时，那么我们会通过{@link #queryNewLeader()}启动对其它节点的leader身份探测。这种探测可能会面临以下两种情况：
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

        private final AtomicBoolean leaderChannelState;
        private final Queue<WaiterMessage> waiterQueue;
        private final InetSocketAddress[] addresses;
        private final boolean[] channelStates;
        private final Channel[] channels;
        private final String clusterName;
        private final Timer timer;
        private final Lock locker;
        private final int nodeNumber;
        private volatile long leaderTerm;
        private Channel leaderChannel;

        private RaftClusterClient(String clusterName, InetSocketAddress[] addresses) {
            ThreadFactory timerThreadFactory = getThreadFactory(clusterName + "-Timer", false);
            this.nodeNumber = addresses.length;
            this.leaderTerm = -1L;

            this.leaderChannelState = new AtomicBoolean(false);
            this.timer = new HashedWheelTimer(timerThreadFactory);
            this.waiterQueue = new ConcurrentLinkedQueue<>();

            this.channelStates = new boolean[nodeNumber];
            this.channels = new Channel[nodeNumber];
            this.locker = new ReentrantLock();

            this.clusterName = clusterName;
            this.addresses = addresses;
        }

        /**
         * 尝试获取到发送{@link ByteBuf}消息的状态权限锁，如果抢锁失败则加入等候队列（不会立刻开始超时计时）。
         * 请确保{@link ByteBuf}已经完成写入头部的长度字段和路径字段、sessionId
         *
         * @param byteBuf {@link ByteBuf}字节缓冲区
         * @param asyncFuture {@link AsyncFuture}
         */
        private void sendMessage(ByteBuf byteBuf, AsyncFuture<?> asyncFuture) {
            if (leaderChannelState.compareAndSet(true, false)) {

                sendMessage0(byteBuf, asyncFuture);

                WaiterMessage waiter;
                while ((waiter = waiterQueue.poll()) != null) {
                    sendMessage0(waiter.byteBuf, waiter.asyncFuture);
                }

                leaderChannelState.set(true);
            } else {
                WaiterMessage waiter = new WaiterMessage(byteBuf, asyncFuture);
                waiterQueue.offer(waiter);
            }
        }

        /**
         * 尝试向leader节点发送一个完整的{@link ByteBuf}消息，并启动超时计时。一旦消息响应超时，
         * 那么启动{@link #queryNewLeader()}进程
         *
         * @param byteBuf {@link ByteBuf}字节缓冲区
         * @param asyncFuture {@link AsyncFuture}
         */
        private void sendMessage0(ByteBuf byteBuf, AsyncFuture<?> asyncFuture) {
            long sessionId = asyncFuture.getSessionId();
            sessionMap.put(sessionId, asyncFuture);

            leaderChannel.writeAndFlush(byteBuf);

            timer.newTimeout(t -> {
                sessionMap.remove(sessionId);
                asyncFuture.notifyResult(null);
                queryNewLeader();
            }, timeout, TimeUnit.MILLISECONDS);
        }

        /**
         * 延迟一段时间后，发起对集群节点的重新连接
         */
        private void delayReconnect() {
            this.timer.newTimeout(t -> tryConnect(), reconnectInterval, TimeUnit.MILLISECONDS);
        }

        /**
         * 尝试发起对集群节点的连接
         */
        private void tryConnect() {
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
                            log.info("无法连接到位于 " + addresses[channelIdx] +
                                    " 的" + clusterName + "节点，准备稍后重试...");
                        }
                        channelStates[channelIdx] = false;
                    });
                }
            }
            delayReconnect();
        }

        /**
         * 尝试抢占标志锁，启动询问集群新任leader的进程
         */
        private void queryNewLeader() {
            if (leaderChannelState.compareAndSet(true, false)) {
                log.info(clusterName + " leader节点响应超时，正在进行更新操作...");
                leaderChannel = null;
                queryNewLeader0();
            }
        }

        /**
         * 向所有节点发出询问消息，探测最新的leader
         */
        private void queryNewLeader0() {
            timer.newTimeout(t -> {
                DynamicCounter counter = new DynamicCounter() {
                    @Override
                    public void onAchieveTarget() {
                        if (leaderChannel == null) {
                            log.info("更新 " + clusterName + " leader节点失败，准备稍后重试...");
                            queryNewLeader0();
                        } else {
                            log.info("成功更新 " + clusterName + " leader节点");
                            leaderChannelState.set(true);
                        }
                    }
                };

                for (int i = 0; i < channels.length; i++) {
                    Channel channel = channels[i];
                    int channelIdx = i;

                    if (channel == null) {
                        continue;
                    }

                    AsyncFuture<QueryLeaderResult> responseFuture = new AsyncFutureImpl<>(QueryLeaderResult.class);
                    long sessionId = responseFuture.getSessionId();

                    sessionMap.put(sessionId, responseFuture);

                    ByteBufMessage message = ByteBufMessage
                            .build("/query-leader")
                            .doWrite(byteBuf -> byteBuf.writeLong(sessionId));

                    channel.writeAndFlush(message.create());

                    counter.addTarget();

                    responseFuture.addListener(result -> {
                        if (result == null) {
                            channels[channelIdx] = null;
                            channel.close();
                        } else {
                            locker.lock();
                            if (result.isLeader() && result.getTerm() > leaderTerm) {
                                leaderTerm = result.getTerm();
                                leaderChannel = channel;
                            }
                            locker.unlock();
                        }
                        counter.addCount();
                    });

                    timer.newTimeout(t1 -> {
                        sessionMap.remove(sessionId);
                        responseFuture.notifyResult(null);
                    }, timeout, TimeUnit.MILLISECONDS);
                }

                counter.determineTarget();

            }, reconnectInterval, TimeUnit.MILLISECONDS);
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
        private class WaiterMessage {
            private final ByteBuf byteBuf;
            private final AsyncFuture<?> asyncFuture;
            private WaiterMessage(ByteBuf byteBuf, AsyncFuture<?> asyncFuture) {
                this.byteBuf = byteBuf;
                this.asyncFuture = asyncFuture;
            }
        }

    }

}
