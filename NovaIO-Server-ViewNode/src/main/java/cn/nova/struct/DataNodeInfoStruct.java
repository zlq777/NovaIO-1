package cn.nova.struct;

import cn.nova.LocalStorageGroup;
import io.netty.buffer.ByteBuf;
import jetbrains.exodus.entitystore.PersistentEntityStore;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static cn.nova.CommonUtils.*;

/**
 * {@link DataNodeInfoStruct}是DataNode集群相关的信息结构体
 *
 * @author RealDragonking
 */
public class DataNodeInfoStruct {

    private final Map<String, Set<InetSocketAddress>> dataNodeInfoMap;
    private final PersistentEntityStore entityStore;
    private final Lock locker;

    public DataNodeInfoStruct(LocalStorageGroup storageGroup) {
        this.entityStore = storageGroup.getEntityStore();
        this.dataNodeInfoMap = new HashMap<>();
        this.locker = new ReentrantLock();
        initDataNodeInfoMap();
    }

    /**
     * 新增一个DataNode节点集群，如果已经存在则创建失败
     *
     * @param clusterName 集群名称
     * @param addresses 所有节点的{@link InetSocketAddress}列表
     * @return 是否成功创建
     */
    public boolean addDataNodeCluster(String clusterName, InetSocketAddress[] addresses) {
        return true;
    }

    /**
     * 删除一个DataNode节点集群，如果不存在则删除失败
     *
     * @param clusterName 集群名称
     * @return 是否成功删除
     */
    public boolean removeDataNodeCluster(String clusterName) {
        return true;
    }

    /**
     * 将所有DataNode集群的信息，读入给定的{@link ByteBuf}字节缓冲区
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    public void readDataNodeInfo(ByteBuf byteBuf) {
        locker.lock();
        for (Map.Entry<String, Set<InetSocketAddress>> entry : dataNodeInfoMap.entrySet()) {
            Set<InetSocketAddress> addrSet = entry.getValue();

            writeString(byteBuf, entry.getKey());
            byteBuf.writeInt(addrSet.size());

            for (InetSocketAddress addr : addrSet) {
                String ipAddr = addr.getAddress().getHostAddress();
                int port = addr.getPort();

                writeString(byteBuf, ipAddr);
                byteBuf.writeInt(port);
            }
        }
        locker.unlock();
    }

    /**
     * 从硬盘中初始化加载所有DataNode集群的信息，并写入到{@link #dataNodeInfoMap}中
     */
    private void initDataNodeInfoMap() {

    }

}
