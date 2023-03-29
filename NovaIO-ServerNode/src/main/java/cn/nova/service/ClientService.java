package cn.nova.service;

import cn.nova.LocalStorage;
import cn.nova.cluster.EntrySyncTask;
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
    private final LocalStorage storage;

    public ClientService(RaftStateMachine stateMachine, LocalStorage storage) {
        this.stateMachine = stateMachine;
        this.storage = storage;
    }

    /**
     * 处理客户端读取Entry数据的命令
     *
     * @param channel {@link Channel}通信信道
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @PathMapping(path = "/read-entry")
    public void readEntry(Channel channel, ByteBuf byteBuf) {
        long sessionId = byteBuf.readLong();
        long entryIndex = byteBuf.readLong();
        storage.readEntry(entryIndex, byteBuf.writeLong(sessionId));
        channel.writeAndFlush(byteBuf);
    }

    /**
     * 处理客户端追加写入新Entry数据的命令，进行持久化并同步到整个集群
     *
     * @param channel {@link Channel}通信信道
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @PathMapping(path = "/append-new-entry")
    public void appendNewEntry(Channel channel, ByteBuf byteBuf) {
        long sessionId = byteBuf.readLong();
        stateMachine.submitEntrySyncTask(new EntrySyncTask() {
            @Override
            public ByteBuf entryData() {
                return byteBuf;
            }

            @Override
            public void onSyncFinish(long entryIndex) {
                byteBuf.clear().writeLong(sessionId).writeLong(entryIndex);
                channel.writeAndFlush(byteBuf);
            }
        });
    }

}
