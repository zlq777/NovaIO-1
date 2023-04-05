package cn.nova;

import cn.nova.cluster.RaftCore;
import cn.nova.network.PathMapping;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * {@link ViewNodeClientService}负责提供面向客户端、与全局数据视图相关的TCP服务接口
 *
 * @author RealDragonking
 */
public final class ViewNodeClientService {

    private final RaftCore raftCore;
    private final LocalStorage storage;

    public ViewNodeClientService(RaftCore raftCore, LocalStorage storage) {
        this.raftCore = raftCore;
        this.storage = storage;
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

    /**
     * 接收并处理请求，从当前节点处获取到DataNode节点集群的信息数据
     *
     * @param channel {@link Channel}通信信道
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @PathMapping(path = "/update-datanode-info")
    public void receiveQueryDataNodeInfoRequest(Channel channel, ByteBuf byteBuf) {
        long sessionId = byteBuf.readLong();
        byteBuf.release();

        ByteBufMessage message = ByteBufMessage
                .build().doWrite(res -> {
                    res.writeLong(sessionId);
                });

        channel.writeAndFlush(message.create());
    }

}
