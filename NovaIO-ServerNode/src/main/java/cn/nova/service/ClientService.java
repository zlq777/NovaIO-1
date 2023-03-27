package cn.nova.service;

import cn.nova.cluster.RaftStateMachine;
import cn.nova.network.PathMapping;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * {@link ClientService}负责提供面向客户端、接收处理客户端通信消息的TCP服务接口
 *
 * @author RealDragonking
 */
public final class ClientService {

    private final RaftStateMachine stateMachine;

    public ClientService(RaftStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    /**
     * 处理客户端写入新Entry数据的命令，进行持久化并同步到整个集群
     *
     * @param channel {@link Channel}通信信道
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @PathMapping(path = "/append-new-entry")
    public void appendNewEntry(Channel channel, ByteBuf byteBuf) {
        //
    }

}
