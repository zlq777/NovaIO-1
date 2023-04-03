package cn.nova;

import cn.nova.cluster.RaftCore;
import cn.nova.network.PathMapping;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * {@link ClientService}负责提供面向客户端、与全局数据视图相关的TCP服务接口
 *
 * @author RealDragonking
 */
public final class ClientService {

    private final RaftCore raftCore;
    private final LocalStorage storage;

    public ClientService(RaftCore raftCore, LocalStorage storage) {
        this.raftCore = raftCore;
        this.storage = storage;
    }

    /**
     * 查询当前节点是否是新任的集群Leader
     *
     * @param channel {@link Channel}通信信道
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @PathMapping(path = "/query-leader")
    public void queryLeader(Channel channel, ByteBuf byteBuf) {
        long sessionId = byteBuf.readLong();

        byteBuf.clear().writerIndex(4);

        byteBuf.writeLong(sessionId)
                .writeBoolean(raftCore.isLeader())
                .writeLong(raftCore.getCurrentTerm());

        int writerIndex = byteBuf.writerIndex();
        byteBuf.writerIndex(0)
                .writeInt(writerIndex - 4)
                .writerIndex(writerIndex);

        channel.writeAndFlush(byteBuf);
    }

}
