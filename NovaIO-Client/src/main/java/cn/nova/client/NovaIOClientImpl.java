package cn.nova.client;

import cn.nova.async.AsyncFuture;
import cn.nova.async.AsyncFutureImpl;
import cn.nova.client.response.AppendNewEntryResult;
import cn.nova.client.response.QueryLeaderResult;
import cn.nova.client.response.ReadEntryResult;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static cn.nova.CommonUtils.writePath;

/**
 * {@link NovaIOClient}的默认实现类。
 * <p>我们只会和leader节点进行读写操作，当leader节点表示自己已经失去了leader身份时，我们会使用leader节点返回的新leader的index，
 * 更新通信信道。</p>
 * <p>如果leader节点响应超时，那么我们会通过{@link #queryViewNodeLeader()}启动对其它节点的leader身份探测。这种探测可能会面临以下两种情况：
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
final class NovaIOClientImpl implements NovaIOClient {

    private final Map<Long, AsyncFuture<?>> sessionMap;
    private final CompletableFuture<Boolean> initFuture;
    private final AtomicBoolean queryLeaderLock;
    private final EventLoopGroup ioThreadGroup;
    private final Channel[] viewNodeChannels;
    private final ByteBufAllocator alloc;
    private final Bootstrap bootstrap;
    private final Lock locker;
    private final Timer timer;
    private final int timeout;
    private volatile boolean notInit;
    private volatile long viewNodeLeaderTerm;
    private Channel viewNodeLeaderChannel;

    NovaIOClientImpl(EventLoopGroup ioThreadGroup, Timer timer,
                     Map<Long, AsyncFuture<?>> sessionMap,
                     Bootstrap bootstrap,
                     Channel[] viewNodeChannels,
                     int timeout) {

        this.alloc = ByteBufAllocator.DEFAULT;

        this.queryLeaderLock = new AtomicBoolean(false);
        this.initFuture = new CompletableFuture<>();
        this.locker = new ReentrantLock();

        this.viewNodeChannels = viewNodeChannels;
        this.ioThreadGroup = ioThreadGroup;
        this.sessionMap = sessionMap;
        this.bootstrap = bootstrap;
        this.timer = timer;

        this.notInit = true;
        this.timeout = timeout;
    }

    /**
     * 根据给定的Entry序列号，从所有集群中读取对应的Entry块数据
     *
     * @param entryIndex Entry序列号
     * @return {@link AsyncFuture}
     */
    @Override
    public AsyncFuture<ReadEntryResult> readEntry(long entryIndex) {
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
        timer.stop();
    }

    /**
     * 定时检查和ViewNode的Leader节点的{@link Channel}通信信道是否有效
     */
    void startLoopQueryViewNodeLeader() {
        TimerTask queryTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) {
                //
            }
        };
        timer.newTimeout(queryTask, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * 等待和ViewNode集群的Leader节点连接创建完成
     *
     * @param initTimeout 初始化连接超时时间
     * @exception Exception 和ViewNode集群的Leader节点连接创建失败引发的异常
     */
    void waitForInitComplete(int initTimeout) throws Exception {
        initFuture.get(initTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * 向所有ViewNode发出询问消息，探测最新的leader
     */
    private void queryViewNodeLeader() {
        if (queryLeaderLock.compareAndSet(false, true)) {
            for (int i = 0; i < viewNodeChannels.length; i++) {

                Channel channel = viewNodeChannels[i];
                int channelIndex = i;

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

                addTimeoutTask(sessionId);

                responseFuture.addListener(result -> {
                    if (result == null) {
                        viewNodeChannels[channelIndex] = null;
                        channel.close();
                    } else {
                        locker.lock();
                        if (result.isLeader() && result.getTerm() > viewNodeLeaderTerm) {
                            viewNodeLeaderTerm = result.getTerm();
                            viewNodeLeaderChannel = channel;
                            if (notInit) {
                                notInit = false;
                                initFuture.complete(true);
                            }
                        }
                        locker.unlock();
                    }
                });
            }
            queryLeaderLock.set(false);
        }
    }

    /**
     * 新增一个超时放弃等待响应的{@link io.netty.util.TimerTask}任务
     *
     * @param sessionId 对应的sessionId
     */
    private void addTimeoutTask(long sessionId) {
        timer.newTimeout(t -> {
            AsyncFuture<?> asyncFuture = sessionMap.remove(sessionId);
            if (asyncFuture != null) {
                asyncFuture.notifyResult(null);
            }
        }, timeout, TimeUnit.MILLISECONDS);
    }

}
