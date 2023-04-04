package cn.nova.client;

import cn.nova.AsyncFuture;
import cn.nova.AsyncFutureImpl;
import cn.nova.DynamicCounter;
import cn.nova.client.response.AppendNewEntryResult;
import cn.nova.client.response.QueryLeaderResult;
import cn.nova.client.response.ReadEntryResult;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static cn.nova.CommonUtils.getThreadFactory;
import static cn.nova.CommonUtils.writePath;

/**
 * {@link NovaIOClient}的默认实现类
 *
 * @author RealDragonking
 */
final class NovaIOClientImpl implements NovaIOClient {

    private static final Logger log = LogManager.getLogger(NovaIOClientImpl.class);
    private final Map<Long, AsyncFuture<?>> sessionMap;
    private final EventLoopGroup ioThreadGroup;
    private final ByteBufAllocator alloc;
    private final Bootstrap bootstrap;
    private final RaftClusterClient viewNodeClient;
    private final int timeout;
    private final int reconnectInterval;

    NovaIOClientImpl(Map<Long, AsyncFuture<?>> sessionMap,
                     InetSocketAddress[] addresses,
                     EventLoopGroup ioThreadGroup,
                     Bootstrap bootstrap,
                     int timeout,
                     int reconnectInterval) {

        this.viewNodeClient = new RaftClusterClient("ViewNode-Cluster", bootstrap, addresses);
        this.alloc = ByteBufAllocator.DEFAULT;

        this.ioThreadGroup = ioThreadGroup;
        this.sessionMap = sessionMap;
        this.bootstrap = bootstrap;

        this.timeout = timeout;
        this.reconnectInterval = reconnectInterval;
    }

    /**
     * 根据给定的Entry序列号，从所有集群中读取对应的Entry块数据
     *
     * @param entryIndex Entry序列号
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<ReadEntryResult> readEntry(long entryIndex) {
//        AsyncFuture<ReadEntryResult> asyncFuture = new AsyncFutureImpl<>(ReadEntryResult.class);
//        long sessionId = asyncFuture.getSessionId();
//
//        sessionMap.put(sessionId, asyncFuture);
//
//        return asyncFuture;
        viewNodeClient.queryNewLeader();
        return null;
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

        private final AtomicBoolean queryLeaderState;
        private final boolean[] channelStates;
        private final Channel[] channels;
        private final String clusterName;
        private final Timer timer;
        private final Lock locker;
        private volatile long leaderTerm;
        private Channel leaderChannel;

        private RaftClusterClient(String clusterName, Bootstrap bootstrap, InetSocketAddress[] addresses) {
            ThreadFactory timerThreadFactory = getThreadFactory(clusterName + "-Timer", false);
            int nodeNumber = addresses.length;

            this.queryLeaderState = new AtomicBoolean(false);
            this.timer = new HashedWheelTimer(timerThreadFactory);

            this.channelStates = new boolean[nodeNumber];
            this.channels = new Channel[nodeNumber];
            this.locker = new ReentrantLock();

            this.clusterName = clusterName;

            this.timer.newTimeout(t -> {
                for (int i = 0; i < nodeNumber; i++) {
                    if (channels[i] == null && channelStates[i]) {

                        channelStates[i] = false;

                        ChannelFuture future = bootstrap.connect(addresses[i]);
                        Channel channel = future.channel();
                        int channelIdx = i;

                        future.addListener(f -> {
                            if (f.isSuccess()) {
                                channels[channelIdx] = channel;
                            } else {
                                log.info("无法连接到位于 " + addresses[channelIdx] + " 的"
                                        + clusterName + "节点，准备稍后重试...");
                            }
                            channelStates[channelIdx] = true;
                        });
                    }
                }
            }, reconnectInterval, TimeUnit.MILLISECONDS);
        }

        /**
         * 尝试抢占标志锁，启动询问集群新任leader的进程
         */
        private void queryNewLeader() {
            if (queryLeaderState.compareAndSet(false, true)) {
                log.info(clusterName + " leader响应超时，正在进行更新操作...");
                leaderChannel = null;
                queryNewLeader0();
            }
        }

        /**
         * 向所有节点发出询问消息，探测最新的leader
         */
        private void queryNewLeader0() {
            DynamicCounter counter = new DynamicCounter() {
                @Override
                public void onAchieveTarget() {
                    if (leaderChannel == null) {
                        log.info("更新" + clusterName + " leader失败，准备稍后重试...");
                        timer.newTimeout(t -> queryNewLeader0(), reconnectInterval, TimeUnit.MILLISECONDS);
                    } else {
                        log.info("成功更新" + clusterName + " leader");
                        queryLeaderState.set(false);
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

                ByteBuf byteBuf = alloc.buffer().writerIndex(4);

                writePath(byteBuf, "/query-leader");
                byteBuf.writeLong(sessionId);

                int writerIndex = byteBuf.writerIndex();
                byteBuf.writerIndex(0)
                        .writeInt(writerIndex - 4)
                        .writerIndex(writerIndex);

                channel.writeAndFlush(byteBuf);

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

                timer.newTimeout(t -> {
                    sessionMap.remove(sessionId);
                    responseFuture.notifyResult(null);
                }, timeout, TimeUnit.MILLISECONDS);
            }

            counter.determineTarget();
        }

    }

}
