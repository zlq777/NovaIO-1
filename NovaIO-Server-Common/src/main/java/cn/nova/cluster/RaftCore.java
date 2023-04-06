package cn.nova.cluster;

import cn.nova.AsyncFuture;
import io.netty.buffer.ByteBuf;

/**
 * {@link RaftCore}定义了raft分布式共识算法的全部细节，方便进行框架化的实现
 *
 * @author RealDragonking
 */
public interface RaftCore {

    /**
     * 启动此{@link RaftCore}
     */
    void start();

    /**
     * 处理来自其它节点的选票获取请求
     *
     * @param candidateIndex candidate节点的序列号
     * @param candidateTerm candidate节点竞选的任期
     * @param applyEntryIndex candidate节点的已应用Entry序列号
     */
    void receiveVoteRequest(int candidateIndex, long candidateTerm, long applyEntryIndex);

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
     * @param inSyncEntryIndex leader节点正在向本节点同步的Entry序列号
     */
    void receiveHeartbeatMsg(int leaderIndex, long leaderTerm, long inSyncEntryIndex);

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
     * @param applyEntryIndex 响应节点的已应用Entry序列号
     */
    void receiveHeartbeatResponse(int nodeIndex, long applyEntryIndex);

    /**
     * 处理来自其它节点的Entry数据同步响应消息
     *
     * @param nodeIndex 响应节点的序列号
     * @param syncedEntryIndex 响应节点的已同步Entry序列号
     */
    void receiveEntrySyncResponse(int nodeIndex, long syncedEntryIndex);

    /**
     * 作为leader节点，把数据同步写入集群大多数节点
     *
     * @param entryData 准备进行集群大多数确认的新Entry数据
     * @return 异步返回在applyEntry中封装好的数据结构体，这样的设计可以让{@link #applyEntry(boolean, long, ByteBuf)}
     * 的子类实现给出动态灵活的消息响应拓展
     */
    AsyncFuture<Object> appendEntryOnLeaderState(ByteBuf entryData);

    /**
     * 应用已经完成集群多数派写入的Entry数据。leader节点需要返回响应数据结构体
     *
     * @param isLeader 当前节点是否作为leader节点完成了这一Entry数据的同步
     * @param entryIndex 已经完成集群多数派写入的Entry序列号
     * @param entryData 已经完成集群多数派写入的Entry数据
     * @return 响应数据结构体
     */
    Object applyEntry(boolean isLeader, long entryIndex, ByteBuf entryData);

    /**
     * 获取到当前节点是否是Leader身份
     *
     * @return 当前节点是否是Leader身份
     */
    boolean isLeader();

    /**
     * 获取到当前节点所处的任期
     *
     * @return 当前节点所处的任期
     */
    long getCurrentTerm();

}
