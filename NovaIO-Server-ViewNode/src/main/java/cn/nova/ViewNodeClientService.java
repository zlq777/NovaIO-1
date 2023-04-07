package cn.nova;

import cn.nova.cluster.RaftCore;
import cn.nova.network.PathMapping;
import cn.nova.struct.DataNodeInfoStruct;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;

import static cn.nova.OperateCode.*;

/**
 * {@link ViewNodeClientService}负责提供面向客户端、与全局数据视图相关的TCP服务接口
 *
 * @author RealDragonking
 */
public final class ViewNodeClientService {

    private final DataNodeInfoStruct dataNodeInfoStruct;
    private final ByteBufAllocator alloc;
    private final RaftCore raftCore;

    public ViewNodeClientService(RaftCore raftCore, DataNodeInfoStruct dataNodeInfoStruct) {
        this.alloc = ByteBufAllocator.DEFAULT;
        this.dataNodeInfoStruct = dataNodeInfoStruct;
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

    /**
     * 接收并处理请求，新增一个DataNode节点集群，如果已经存在则创建失败
     *
     * @param channel {@link Channel}通信信道
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @PathMapping(path = "/add-datanode-cluster")
    public void receiveAddDataNodeInfoRequest(Channel channel, ByteBuf byteBuf) {
        long sessionId = byteBuf.readLong();

        ByteBuf entryData = alloc.buffer();
        entryData.writeInt(ADD_NEW_DATANODE_CLUSTER).writeBytes(byteBuf);

        byteBuf.release();

        AsyncFuture<Boolean> asyncFuture = raftCore.appendEntryOnLeaderState(entryData);
        asyncFuture.addListener(result -> {
            if (result != null) {
                ByteBufMessage message = ByteBufMessage.build().doWrite(msg -> {
                    msg.writeLong(sessionId);
                    msg.writeBoolean(result);
                });
                channel.writeAndFlush(message.create());
            }
        });
    }

    /**
     * 接收并处理请求，查询所有的DataNode集群的信息结构体
     *
     * @param channel {@link Channel}通信信道
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @PathMapping(path = "/query-datanode-info")
    public void receiveQueryDataNodeInfoRequest(Channel channel, ByteBuf byteBuf) {
        long sessionId = byteBuf.readLong();

        byteBuf.release();

        ByteBufMessage message = ByteBufMessage.build().doWrite(msg -> {
            msg.writeLong(sessionId);
            dataNodeInfoStruct.readDataNodeInfo(msg);
        });

        channel.writeAndFlush(message.create());
    }

}
