package cn.nova.client;

import cn.nova.async.AsyncFuture;
import cn.nova.async.AsyncFutureImpl;
import cn.nova.async.AsyncFutureListener;
import cn.nova.client.response.AppendNewEntryResult;
import cn.nova.client.response.QueryLeaderResult;
import cn.nova.client.response.ReadEntryResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final ByteBufAllocator alloc;
    private final AtomicBoolean queryLeaderLock;
    private final EventLoopGroup ioThreadGroup;
    private final Channel[] viewNodeChannels;
    private final Timer timer;
    private final int timeout;
    private Channel leaderChannel;

    NovaIOClientImpl(EventLoopGroup ioThreadGroup, Timer timer,
                     Map<Long, AsyncFuture<?>> sessionMap,
                     Channel[] viewNodeChannels,
                     int timeout) {
        this.alloc = ByteBufAllocator.DEFAULT;
        this.queryLeaderLock = new AtomicBoolean(false);
        this.viewNodeChannels = viewNodeChannels;
        this.ioThreadGroup = ioThreadGroup;
        this.sessionMap = sessionMap;
        this.timer = timer;
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
     * 向所有ViewNode发出询问消息，探测最新的leader
     */
    private void queryViewNodeLeader() {
        if (queryLeaderLock.compareAndSet(false, true)) {

            QueryLeaderListener listener = new QueryLeaderListener();

            for (int i = 0; i < viewNodeChannels.length; i++) {

                Channel channel = viewNodeChannels[i];
                int channelIndex = i;

                if (channel == null) {
                    continue;
                }

                AsyncFuture<QueryLeaderResult> asyncFuture = new AsyncFutureImpl<>(QueryLeaderResult.class);
                long sessionId = asyncFuture.getSessionId();

                sessionMap.put(sessionId, asyncFuture);

                ByteBuf byteBuf = alloc.buffer().writerIndex(4);

                writePath(byteBuf, "/query-leader");
                byteBuf.writeLong(sessionId).writeInt(channelIndex);

                int writerIndex = byteBuf.writerIndex();
                byteBuf.writerIndex(0)
                        .writeInt(writerIndex - 4)
                        .writerIndex(writerIndex);

                channel.writeAndFlush(byteBuf);

                addTimeoutTask(sessionId);

                asyncFuture.addListener(listener);
                asyncFuture.addListener(result -> {
                    if (result == null) {
                        viewNodeChannels[channelIndex] = null;
                        channel.close();
                    }
                });
            }

            listener.ensureSendAll();
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

    /**
     * 监听{@link QueryLeaderResult}消息，进行Leader节点视图的更新
     *
     * @author RealDragonking
     */
    private class QueryLeaderListener implements AsyncFutureListener<QueryLeaderResult> {
        private volatile boolean hasSendAll = false;
        private volatile int answerNodeNumber = 0;
        private volatile int queryNodeNumber = 0;
        private volatile long mostNewTerm = -1L;

        /**
         * 通知异步返回结果
         *
         * @param result 异步返回结果
         */
        @Override
        public void onNotify(QueryLeaderResult result) {
            if (result == null) {
                return;
            }
            synchronized (this) {

            }
        }

        /**
         * 确认所有消息已经发送
         */
        private void ensureSendAll() {
            synchronized (this) {
                hasSendAll = true;
                if (answerNodeNumber == queryNodeNumber) {

                }
            }
        }
    }

}
