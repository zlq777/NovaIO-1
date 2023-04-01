package cn.nova.service;

import cn.nova.cluster.RaftNode;
import cn.nova.network.PathMapping;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramPacket;

/**
 * {@link RaftService}负责提供面向其它节点、与raft分布式共识算法实现相关的UDP服务接口
 *
 * @author RealDragonking
 */
public final class RaftService {

    private final RaftNode stateMachine;

    public RaftService(RaftNode stateMachine) {
        this.stateMachine = stateMachine;
    }

    /**
     * 对应于{@link RaftNode#receiveVoteRequest(int, long, long)}
     *
     * @param packet {@link DatagramPacket}数据包
     */
    @PathMapping(path = "/vote/request")
    public void receiveVoteRequest(DatagramPacket packet) {
        ByteBuf content = packet.content();
        int candidateIndex = content.readInt();
        long candidateTerm = content.readLong();
        long syncedEntryIndex = content.readLong();

        stateMachine.receiveVoteRequest(candidateIndex, candidateTerm, syncedEntryIndex);
        packet.release();
    }

    /**
     * 对应于{@link RaftNode#receiveVoteResponse(long, boolean)}
     *
     * @param packet {@link DatagramPacket}数据包
     */
    @PathMapping(path = "/vote/response")
    public void receiveVoteResponse(DatagramPacket packet) {
        ByteBuf content = packet.content();
        long voteTerm = content.readLong();
        boolean isSuccess = content.readBoolean();

        stateMachine.receiveVoteResponse(voteTerm, isSuccess);
        packet.release();
    }

    /**
     * 对应于{@link RaftNode#receiveHeartbeatMsg(int, long, long)}
     *
     * @param packet {@link DatagramPacket}数据包
     */
    @PathMapping(path = "/msg/heartbeat")
    public void receiveHeartbeatMsg(DatagramPacket packet) {
        ByteBuf content = packet.content();
        int leaderIndex = content.readInt();
        long leaderTerm = content.readLong();
        long applicableEntryIndex = content.readLong();

        stateMachine.receiveHeartbeatMsg(leaderIndex, leaderTerm, applicableEntryIndex);
        packet.release();
    }

    /**
     * 对应于{@link RaftNode#receiveHeartbeatResponse(int, long)}
     *
     * @param packet {@link DatagramPacket}数据包
     */
    @PathMapping(path = "/msg/heartbeat/response")
    public void receiveHeartbeatResponse(DatagramPacket packet) {
        ByteBuf content = packet.content();
        int nodeIndex = content.readInt();
        long appliedEntryIndex = content.readLong();

        stateMachine.receiveHeartbeatResponse(nodeIndex, appliedEntryIndex);
        packet.release();
    }

    /**
     * 对应于{@link RaftNode#receiveEntrySyncMsg(int, long, long, ByteBuf)}
     *
     * @param packet {@link DatagramPacket}数据包
     */
    @PathMapping(path = "/msg/entry-sync")
    public void receiveEntrySyncMsg(DatagramPacket packet) {
        ByteBuf content = packet.content();
        int leaderIndex = content.readInt();
        long leaderTerm = content.readLong();
        long entryIndex = content.readLong();

        stateMachine.receiveEntrySyncMsg(leaderIndex, leaderTerm, entryIndex, content);
    }

    /**
     * 对应于{@link RaftNode#receiveEntrySyncResponse(int, long)}
     *
     * @param packet {@link DatagramPacket}数据包
     */
    @PathMapping(path = "/msg/entry-sync/response")
    public void receiveEntrySyncResponse(DatagramPacket packet) {
        ByteBuf content = packet.content();
        int nodeIndex = content.readInt();
        long syncedEntryIndex = content.readLong();

        stateMachine.receiveEntrySyncResponse(nodeIndex, syncedEntryIndex);
        packet.release();
    }

}
