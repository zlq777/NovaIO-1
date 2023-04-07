package cn.nova.cluster;

import cn.nova.LocalStorage;
import cn.nova.AsyncFuture;
import cn.nova.config.TimeConfig;
import cn.nova.network.UDPService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static cn.nova.CommonUtils.*;

/**
 * 实现了{@link RaftCore}的核心算法逻辑，还需要子类对{@link RaftCore#applyEntry(long, ByteBuf, AsyncFuture)}
 * 也就是EntryData的应用逻辑给出具体实现
 *
 * @author RealDragonking
 */
public abstract class AbstractRaftCore implements RaftCore {

    private static final Logger log = LogManager.getLogger(RaftCore.class);
    private final Queue<PendingEntry> pendingEntryQueue;
    private final ClusterNode[] otherNodes;
    private final ByteBufAllocator alloc;
    private final UDPService udpService;
    private final LocalStorage storage;
    private final Timer timer;

    /**
     * 当前节点在集群中的序列号
     */
    private final int index;
    /**
     * 当前集群的大多数
     */
    private final int majority;
    /**
     * {@link #resetWaitTicks}的随机下限
     */
    private final int minElectTimeoutTicks;
    /**
     * {@link #resetWaitTicks}的随机上限
     */
    private final int maxElectTimeoutTicks;
    /**
     * leader状态下发送心跳控制、Entry数据的时间间隔
     */
    private final int sendMsgIntervalTicks;

    /**
     * waitTicks控制了以下三种行为的执行间隔时间：
     * <ul>
     *     <li>
     *         leader状态下发送心跳控制、Entry数据
     *     </li>
     *     <li>
     *         follower状态下接收不到leader的消息进入candidate状态发起选举
     *     </li>
     *     <li>
     *         candidate状态下收不到足够的响应消息，重置选举进程，重新发起一次选举
     *     </li>
     * </ul>
     */
    private volatile int waitTicks;
    /**
     * 为follower/candidate状态下的{@link #waitTicks}提供了复位的可能，主要是起到一个随机时间的效果
     */
    private volatile int resetWaitTicks;
    /**
     * 已经完成同步最新Entry的节点数量
     */
    private volatile int syncedNodeNumber;
    /**
     * 在candidate选举进程中，当前节点获得的支持票数
     */
    private volatile int successVoteNumber;
    /**
     * 在candidate选举进程中，当前节点获得的反对票数
     */
    private volatile int failVoteNumber;
    /**
     * 当前节点所处的任期
     */
    private volatile long currentTerm;
    /**
     * 已经完成了集群大多数写入、已应用Entry序列号。每个节点的applyIndex可能会不一样
     */
    private volatile long applyEntryIndex;
    /**
     * pendingEntryIndex表示{@link #pendingEntry}的序列号
     */
    private volatile long pendingEntryIndex;
    /**
     * 当前节点针对{@link #currentTerm}是否已经进行过投票
     */
    private volatile boolean hasVote;
    /**
     * pendingEntry在不同状态下有不同的含义：
     * <ul>
     *     <li>
     *         leader状态下，表示正在全局同步的全局最新Entry，来源于客户端的写入任务。{@link PendingEntry#asyncFuture}不为空，需要在
     *         集群大多数确认后执行回调通知
     *     </li>
     *     <li>
     *         follower状态下，表示针对性同步的局部最新Entry，来源于leader节点的主动同步复制。{@link PendingEntry#asyncFuture}为空
     *     </li>
     * </ul>
     */
    private volatile PendingEntry pendingEntry;
    private volatile ClusterNode leader;
    private volatile RaftState state;

    public AbstractRaftCore(ClusterInfo clusterInfo,
                            ByteBufAllocator alloc,
                            TimeConfig timeConfig,
                            UDPService udpService,
                            LocalStorage storage,
                            Timer timer,
                            int tickTime) {

        this.otherNodes = clusterInfo.getOtherNodes();
        this.pendingEntryQueue = new ConcurrentLinkedQueue<>();
        this.udpService = udpService;
        this.storage = storage;
        this.timer = timer;
        this.alloc = alloc;

        this.index = clusterInfo.getIndex();
        this.majority = ((otherNodes.length + 1) >> 1) + 1;
        this.minElectTimeoutTicks = timeConfig.getMinElectTimeout() / tickTime;
        this.maxElectTimeoutTicks = timeConfig.getMaxElectTimeout() / tickTime;
        this.sendMsgIntervalTicks = timeConfig.getSendMsgInterval() / tickTime;

        this.currentTerm = storage.readLong("term", -1L);
        this.applyEntryIndex = storage.readLong("apply-entry-index", -1L);

        this.waitTicks = resetWaitTicks = randomElectTicks();
        this.state = RaftState.FOLLOWER;
    }

    /**
     * 启动此{@link RaftCore}
     */
    @Override
    public void start() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) {
                timer.newTimeout(this, 0, TimeUnit.MILLISECONDS);
                synchronized (AbstractRaftCore.this) {
                    if (waitTicks == 0) {
                        switch (state) {
                            case LEADER:
                                waitTicks = sendMsgIntervalTicks;
                                sendEntrySyncMsg();
                                break;
                            case FOLLOWER:
                                state = RaftState.CANDIDATE;
                                leader = null;
                            case CANDIDATE:
                                startNewVote();
                        }
                    } else {
                        waitTicks --;
                    }
                }
            }
        };
        timer.newTimeout(timerTask, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * 启动新一轮竞选
     */
    private void startNewVote() {
        successVoteNumber = 1;
        failVoteNumber = 0;
        waitTicks = resetWaitTicks = randomElectTicks();

        changeTerm(currentTerm + 1);
        sendVoteRequest();
    }

    /**
     * 使用给定的字段范围随机化一个follower参与竞选时间/candidate选举超时时间
     *
     * @return follower参与竞选时间/candidate选举超时时间
     */
    private int randomElectTicks() {
        return randomInRange(minElectTimeoutTicks, maxElectTimeoutTicks);
    }

    /**
     * 根据给定的节点序列号，获取到目标{@link ClusterNode}节点。这个方法是为了正确处理序列号和{@link #otherNodes}数组索引的偏移问题
     *
     * @param index 节点序列号
     * @return {@link ClusterNode}
     */
    private ClusterNode getNode(int index) {
        return otherNodes[index + (index > this.index ? -1 : 0)];
    }

    /**
     * 修改并持久化新的任期
     *
     * @param newTerm 新的任期
     */
    private void changeTerm(long newTerm) {
        currentTerm = newTerm;
        storage.writeLong("term", newTerm);
    }

    /**
     * 修改并持久化新的已应用Entry序列号
     *
     * @param newIndex 新的已应用Entry序列号
     */
    private void changeApplyEntryIndex(long newIndex) {
        applyEntryIndex = newIndex;
        storage.writeLong("apply-entry-index", newIndex);
    }

    /**
     * 向所有{@link ClusterNode}节点发送Entry条目同步数据
     */
    private void sendEntrySyncMsg() {
        for (ClusterNode node : otherNodes) {
            sendEntrySyncMsg(node);
        }
    }

    /**
     * 检查是否可以向{@link ClusterNode}节点发送Entry数据，如若可以则调用{@link #sendEntrySyncMsg(ClusterNode, long, boolean)}实际执行
     *
     * @param node {@link ClusterNode}
     */
    private void sendEntrySyncMsg(ClusterNode node) {
        if (node.hasChecked()) {
            long sendEntryIndex = node.inSyncEntryIndex();

            if (pendingEntry != null && sendEntryIndex == pendingEntryIndex) {
                sendEntrySyncMsg(node, sendEntryIndex, true);
                return;
            } else if (sendEntryIndex <= applyEntryIndex){
                sendEntrySyncMsg(node, sendEntryIndex, false);
                return;
            }
        }
        sendHeartbeatMsg(node);
    }

    /**
     * 向{@link ClusterNode}节点发送Entry条目数据
     *
     * @param node {@link ClusterNode}
     * @param sendEntryIndex 准备发送的Entry序列号
     * @param useInSyncEntryData 是否使用正在等待写入集群的Entry数据
     */
    private void sendEntrySyncMsg(ClusterNode node, long sendEntryIndex, boolean useInSyncEntryData) {
        ByteBuf content = alloc.buffer();
        DatagramPacket packet = new DatagramPacket(content, node.address());

        writeString(content, "/msg/entry-sync");
        content.writeInt(index).writeLong(currentTerm).writeLong(sendEntryIndex);

        if (useInSyncEntryData) {
            ByteBuf entryData = pendingEntry.entryData;

            entryData.markReaderIndex();
            content.writeBytes(entryData);
            entryData.resetReaderIndex();
        } else {
            storage.readBytes(sendEntryIndex, content);
        }

        udpService.send(packet);
    }

    /**
     * 向指定节点发送心跳信息
     *
     * @param node {@link ClusterNode}
     */
    private void sendHeartbeatMsg(ClusterNode node) {
        ByteBuf content = alloc.buffer();
        DatagramPacket packet = new DatagramPacket(content, node.address());

        writeString(content, "/msg/heartbeat");
        content.writeInt(index).writeLong(currentTerm).writeLong(node.inSyncEntryIndex());

        udpService.send(packet);
    }

    /**
     * 向其它节点发送选票获取请求
     */
    private void sendVoteRequest() {
        for (ClusterNode node : otherNodes) {
            ByteBuf content = alloc.buffer();
            DatagramPacket packet = new DatagramPacket(content, node.address());

            writeString(content, "/vote/request");
            content.writeInt(index).writeLong(currentTerm).writeLong(applyEntryIndex);

            udpService.send(packet);
        }
    }

    /**
     * 处理来自其它节点的选票获取请求。作为leader、follower、candidate都可以处理这一请求，
     * 唯一不同的是leader、candidate遇到一个term更大的candidate时，会同意给予选票并切换成follower。
     * 对于follower来说选票请求可以防止它们参与竞选
     *
     * @param candidateIndex   candidate节点的序列号
     * @param candidateTerm    candidate节点竞选的任期
     * @param applyEntryIndex candidate节点的已应用Entry序列号
     */
    @Override
    public void receiveVoteRequest(int candidateIndex, long candidateTerm, long applyEntryIndex) {
        ClusterNode node = getNode(candidateIndex);
        ByteBuf content = alloc.buffer();
        DatagramPacket packet = new DatagramPacket(content, node.address());

        writeString(content, "/vote/response");
        content.writeLong(candidateTerm);

        synchronized (this) {
            if (receiveVoteRequest0(candidateTerm, applyEntryIndex)) {
                if (candidateTerm > currentTerm) {
                    changeTerm(candidateTerm);
                }
                content.writeBoolean(true);
            } else {
                content.writeBoolean(false);
            }

            waitTicks = resetWaitTicks;
        }

        udpService.send(packet);
    }

    /**
     * 具体处理来自其它节点的选票获取请求
     *
     * @param candidateTerm  candidate节点竞选的任期
     * @param applyEntryIndex candidate节点的已应用Entry序列号
     * @return 是否可以给予选票
     */
    private boolean receiveVoteRequest0(long candidateTerm, long applyEntryIndex) {
        if (candidateTerm < currentTerm || applyEntryIndex < this.applyEntryIndex) {
            return false;
        }

        if (state == RaftState.FOLLOWER) {
            if (candidateTerm > currentTerm || ! hasVote) {
                hasVote = true;
                return true;
            }
        } else {
            if (candidateTerm > currentTerm) {
                state = RaftState.FOLLOWER;
                return true;
            }
        }

        return false;
    }

    /**
     * 处理来自其它节点的投票响应。这一响应只对candidate来说有作用，并且我们需要检查voteTerm是否等于当前term，
     * 防止历史投票请求生效
     *
     * @param voteTerm  选票所属term
     * @param isSuccess 是否成功获取选票
     */
    @Override
    public void receiveVoteResponse(long voteTerm, boolean isSuccess) {
        synchronized (this) {
            if (voteTerm == currentTerm && state == RaftState.CANDIDATE) {
                receiveVoteResponse0(isSuccess);
            }
        }
    }

    /**
     * 具体处理来自其它节点的投票响应
     *
     * @param isSuccess 是否成功获得选票
     */
    private void receiveVoteResponse0(boolean isSuccess) {
        if (isSuccess) {
            successVoteNumber ++;
        } else {
            failVoteNumber ++;
        }

        if (successVoteNumber == majority) {
            log.info("当前节点拿到了大多数选票, 成功当选leader, currentTerm:" + currentTerm);
            initLeader();
        } else if (failVoteNumber == majority) {
            log.info("当前节点不甘心失败, 重新发起了一次选举");
            startNewVote();
        }
    }

    /**
     * 刚刚成为leader角色，进行初始化
     */
    private void initLeader() {
        hasVote = false;
        state = RaftState.LEADER;
        waitTicks = sendMsgIntervalTicks;
        for (ClusterNode node : otherNodes) {
            node.setInSyncEntryIndex(-1L);
            sendHeartbeatMsg(node);
        }
    }

    /**
     * 处理来自Leader节点的心跳控制消息
     *
     * @param leaderIndex leader节点的序列号
     * @param leaderTerm leader节点的任期
     * @param inSyncEntryIndex leader节点正在向本节点同步的Entry序列号
     */
    @Override
    public void receiveHeartbeatMsg(int leaderIndex, long leaderTerm, long inSyncEntryIndex) {
        InetSocketAddress leaderAddr;

        PendingEntry pendingEntry = null;
        long applyEntryIndex;

        synchronized (this) {
            receiveLeaderMsg(leaderIndex, leaderTerm);

            leaderAddr = leader.address();
            applyEntryIndex = this.applyEntryIndex;

            if (this.pendingEntry != null && inSyncEntryIndex > pendingEntryIndex) {
                pendingEntry = this.pendingEntry;
                applyEntryIndex = pendingEntryIndex;

                storage.writeBytes(applyEntryIndex, pendingEntry.entryData);
                changeApplyEntryIndex(applyEntryIndex);

                this.pendingEntry = null;
            }
        }

        ByteBuf content = alloc.buffer();
        DatagramPacket packet = new DatagramPacket(content, leaderAddr);

        writeString(content, "/msg/heartbeat/response");
        content.writeInt(index).writeLong(applyEntryIndex);

        udpService.send(packet);

        if (pendingEntry != null) {
            applyEntry(applyEntryIndex, pendingEntry.entryData, pendingEntry.asyncFuture);
        }
    }

    /**
     * 处理来自leader节点的Entry条目数据同步消息
     *
     * @param leaderIndex leader节点的序列号
     * @param leaderTerm  leader节点的任期
     * @param entryIndex  同步的Entry序列号
     * @param entryData   同步的Entry数据
     */
    @Override
    public void receiveEntrySyncMsg(int leaderIndex, long leaderTerm, long entryIndex, ByteBuf entryData) {
        InetSocketAddress leaderAddr;

        PendingEntry pendingEntry = null;
        long applyEntryIndex = -1L;

        synchronized (this) {
            receiveLeaderMsg(leaderIndex, leaderTerm);

            leaderAddr = leader.address();

            if (entryIndex > applyEntryIndex) {
                if (this.pendingEntry != null) {
                    if (entryIndex > pendingEntryIndex) {

                        pendingEntry = this.pendingEntry;
                        applyEntryIndex = pendingEntryIndex;

                        storage.writeBytes(applyEntryIndex, pendingEntry.entryData);
                        changeApplyEntryIndex(applyEntryIndex);

                        this.pendingEntry = new PendingEntry(entryData, null);
                        pendingEntryIndex = entryIndex;
                    } else {
                        entryData.release();
                    }
                } else {
                    this.pendingEntry = new PendingEntry(entryData, null);
                    pendingEntryIndex = entryIndex;
                }
            } else {
                entryData.release();
            }
        }

        ByteBuf content = alloc.buffer();
        DatagramPacket packet = new DatagramPacket(content, leaderAddr);

        writeString(content, "/msg/entry-sync/response");
        content.writeInt(index).writeLong(entryIndex);

        udpService.send(packet);

        if (pendingEntry != null) {
            applyEntry(applyEntryIndex, pendingEntry.entryData, pendingEntry.asyncFuture);
        }
    }

    /**
     * 具体处理来自leader节点的消息
     *
     * @param leaderIndex leader节点的序列号
     * @param leaderTerm leader节点的任期
     */
    private void receiveLeaderMsg(int leaderIndex, long leaderTerm) {
        hasVote = false;
        leader = getNode(leaderIndex);
        waitTicks = resetWaitTicks;

        if (leaderTerm != currentTerm) {
            changeTerm(leaderTerm);
        }

        if (state == RaftState.LEADER) {
            PendingEntry pendingEntry = this.pendingEntry;

            if (pendingEntry != null) {
                pendingEntry.entryData.release();
                pendingEntry.asyncFuture.notifyResult(null);
                this.pendingEntry = null;
            }

            while ((pendingEntry = pendingEntryQueue.poll()) != null) {
                pendingEntry.entryData.release();
                pendingEntry.asyncFuture.notifyResult(null);
            }
        }

        state = RaftState.FOLLOWER;
    }

    /**
     * 处理来自其他节点的心跳控制响应消息
     *
     * @param nodeIndex         响应节点的序列号
     * @param applyEntryIndex 响应节点的已应用Entry序列号
     */
    @Override
    public void receiveHeartbeatResponse(int nodeIndex, long applyEntryIndex) {
        synchronized (this) {
            if (state == RaftState.LEADER) {
                ClusterNode node = getNode(nodeIndex);
                if (! node.hasChecked()) {
                    node.setInSyncEntryIndex(applyEntryIndex + 1);
                }
            }
        }
    }

    /**
     * 处理来自其它节点的Entry数据同步响应消息
     *
     * @param nodeIndex        响应节点的序列号
     * @param syncedEntryIndex 响应节点的已同步Entry序列号
     */
    @Override
    public void receiveEntrySyncResponse(int nodeIndex, long syncedEntryIndex) {
        PendingEntry pendingEntry = null;
        long applyEntryIndex = -1L;

        synchronized (this) {
            if (state == RaftState.LEADER) {

                ClusterNode node = getNode(nodeIndex);
                boolean onlySendOne = true;

                if (syncedEntryIndex == node.inSyncEntryIndex()) {
                    node.setInSyncEntryIndex(syncedEntryIndex + 1);
                }

                if (this.pendingEntry != null && syncedEntryIndex == pendingEntryIndex) {
                    if (++ syncedNodeNumber == majority) {

                        pendingEntry = this.pendingEntry;
                        applyEntryIndex = pendingEntryIndex;
                        onlySendOne = false;

                        storage.writeBytes(applyEntryIndex, pendingEntry.entryData);
                        changeApplyEntryIndex(applyEntryIndex);

                        if ((this.pendingEntry = pendingEntryQueue.poll()) != null) {
                            syncedNodeNumber = 1;
                            pendingEntryIndex = applyEntryIndex + 1;
                        }
                    }
                }

                if (onlySendOne) {
                    sendEntrySyncMsg(node);
                } else {
                    sendEntrySyncMsg();
                }
            }
        }

        if (pendingEntry != null) {
            applyEntry(applyEntryIndex, pendingEntry.entryData, pendingEntry.asyncFuture);
        }
    }

    /**
     * 作为leader节点，把数据同步写入集群大多数节点
     *
     * @param entryData 准备进行集群大多数确认的新Entry数据
     * @return {@link AsyncFuture}
     */
    @Override
    public <T> AsyncFuture<T> appendEntryOnLeaderState(ByteBuf entryData) {
        AsyncFuture<T> asyncFuture = AsyncFuture.of(null);
        PendingEntry newEntry = new PendingEntry(entryData, asyncFuture);

        synchronized (this) {
            if (state == RaftState.LEADER) {
                if (pendingEntry == null) {
                    syncedNodeNumber = 1;
                    pendingEntry = newEntry;
                    pendingEntryIndex = applyEntryIndex + 1;
                    sendEntrySyncMsg();
                } else {
                    pendingEntryQueue.offer(newEntry);
                }
            } else {
                entryData.release();
                asyncFuture.notifyResult(null);
            }
        }

        return asyncFuture;
    }

    /**
     * 获取到当前节点是否是Leader身份
     *
     * @return 当前节点是否是Leader身份
     */
    @Override
    public boolean isLeader() {
        return state == RaftState.LEADER;
    }

    /**
     * 获取到当前节点所处的任期
     *
     * @return 当前节点所处的任期
     */
    @Override
    public long getCurrentTerm() {
        return this.currentTerm;
    }

    /**
     * {@link PendingEntry}是正在进行同步、等待被确认应用的Entry
     *
     * @author RealDragonking
     */
    private static class PendingEntry {
        private final ByteBuf entryData;
        private final AsyncFuture<?> asyncFuture;
        private PendingEntry(ByteBuf entryData, AsyncFuture<?> asyncFuture) {
            this.entryData = entryData;
            this.asyncFuture = asyncFuture;
        }
    }

}
