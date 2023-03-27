package cn.nova.cluster;

import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;

/**
 * {@link RaftStateMachine}定义了raft分布式共识算法的全部细节，方便进行框架化的实现
 *
 * @author RealDragonking
 */
public interface RaftStateMachine {

    /**
     * 启动此{@link RaftStateMachine}
     */
    void start();

    /**
     * 处理来自其它节点的选票获取请求
     *
     * @param candidateIndex candidate节点的序列号
     * @param candidateTerm candidate节点竞选的任期
     * @param syncedEntryIndex candidate节点的已同步Entry序列号
     */
    void receiveVoteRequest(int candidateIndex, long candidateTerm, long syncedEntryIndex);

    /**
     * 处理来自其它节点的投票响应
     *
     * @param voteTerm 选票所属term
     * @param isSuccess 是否成功获取选票
     */
    void receiveVoteResponse(long voteTerm, boolean isSuccess);

    /**
     * 处理来自Leader节点的心跳控制消息
     *
     * @param leaderIndex leader节点的序列号
     * @param leaderTerm leader节点的任期
     * @param applicableEntryIndex 可应用的Entry序列号
     */
    void receiveHeartbeatMsg(int leaderIndex, long leaderTerm, long applicableEntryIndex);

    /**
     * 处理来自leader节点的Entry条目数据同步消息
     *
     * @param leaderIndex leader节点的序列号
     * @param leaderTerm leader节点的任期
     * @param entryIndex 同步的Entry序列号
     * @param entryData 同步的Entry数据
     */
    void receiveEntrySyncMsg(int leaderIndex, long leaderTerm, long entryIndex, ByteBuf entryData);

    /**
     * 处理来自其他节点的心跳控制响应消息
     *
     * @param nodeIndex 响应节点的序列号
     * @param appliedEntryIndex 响应节点的已应用Entry序列号
     */
    void receiveHeartbeatResponse(int nodeIndex, long appliedEntryIndex);

    /**
     * 处理来自其它节点的Entry数据同步响应消息
     *
     * @param nodeIndex 响应节点的序列号
     * @param syncedEntryIndex 响应节点的已同步Entry序列号
     */
    void receiveEntrySyncResponse(int nodeIndex, long syncedEntryIndex);

    /**
     * 提交{@link EntrySyncTask}，任务执行结果会异步的返回。这个任务只能由leader节点执行，非leader节点会立即返回
     * 同步失败的结果。如果{@link EntrySyncTask}携带的{@link ByteBuffer}被成功写入到raft集群的大多数，那么任务结果会成功返回
     *
     * @param task {@link EntrySyncTask}
     */
    void submitEntrySyncTask(EntrySyncTask task);

}
