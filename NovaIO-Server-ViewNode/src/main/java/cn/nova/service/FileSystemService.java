package cn.nova.service;

import cn.nova.AsyncFuture;
import cn.nova.cluster.RaftCore;
import cn.nova.network.PathMapping;
import cn.nova.struct.FileSystemStruct;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;

import static cn.nova.CommandType.*;

/**
 * {@link FileSystemService}负责提供与文件系统视图相关的TCP服务接口
 *
 * @author RealDragonking
 */
public final class FileSystemService {

    private final FileSystemStruct fileSystemStruct;
    private final ByteBufAllocator alloc;
    private final RaftCore raftCore;

    public FileSystemService(RaftCore raftCore, FileSystemStruct fileSystemStruct) {
        this.fileSystemStruct = fileSystemStruct;
        this.alloc = ByteBufAllocator.DEFAULT;
        this.raftCore = raftCore;
    }

    /**
     * 接受并处理请求，创建一个用于存放文件夹/对象的命名空间，如果已经存在同名的命名空间则创建失败
     *
     * @param channel {@link Channel}通信信道
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    @PathMapping(path = "/create-namespace")
    public void createNameSpace(Channel channel, ByteBuf byteBuf) {
        long sessionId = byteBuf.readLong();

        ByteBuf entryData = alloc.buffer();
        entryData.writeInt(CREATE_NAMESPACE).writeBytes(byteBuf);

        byteBuf.release();

        AsyncFuture<?> asyncFuture = raftCore.appendEntryOnLeaderState(entryData);
        asyncFuture.addListener(result -> {
            if (result != null) {

            }
        });
    }
    
}
