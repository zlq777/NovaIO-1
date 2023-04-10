package cn.nova.service;

import cn.nova.ByteBufMessage;
import cn.nova.cluster.RaftCore;
import cn.nova.network.PathMapping;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * {@link RaftInfoService}提供了与Raft算法运转状态信息相关的TCP服务接口
 *
 * @author RealDragonking
 */
public final class RaftInfoService {

    private final RaftCore raftCore;

    public RaftInfoService(RaftCore raftCore) {
        this.raftCore = raftCore;
    }

    /**
     * 接收并处理请求，查询当前节点是否获得了新任集群Leader身份
     *
     * @param channel {@link Channel}通信信道
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @PathMapping(path = "/query-leader")
    public void receiveQueryLeaderRequest(Channel channel, ByteBuf byteBuf) {
        long sessionId = byteBuf.readLong();
        byteBuf.release();

        ByteBufMessage message = ByteBufMessage
                .build().doWrite(res -> {
                    res.writeLong(sessionId);
                    res.writeBoolean(raftCore.isLeader());
                    res.writeLong(raftCore.getCurrentTerm());
                });

        channel.writeAndFlush(message.create());
    }

}
