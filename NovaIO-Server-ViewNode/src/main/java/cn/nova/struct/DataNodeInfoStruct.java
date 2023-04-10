package cn.nova.struct;

import cn.nova.LocalStorageGroup;
import io.netty.buffer.ByteBuf;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityIterable;
import jetbrains.exodus.entitystore.PersistentEntityStore;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static cn.nova.CommonUtils.*;
import static cn.nova.EntityType.*;

/**
 * {@link DataNodeInfoStruct}是DataNode集群相关的信息结构体，提供了增改、查询、持久化DataNode集群信息的接口方法
 *
 * @author RealDragonking
 */
public final class DataNodeInfoStruct {

    private final Map<String, Set<InetSocketAddress>> clusterInfoMap;
    private final PersistentEntityStore entityStore;

    public DataNodeInfoStruct(LocalStorageGroup storageGroup) {
        this.entityStore = storageGroup.getEntityStore();
        this.clusterInfoMap = new ConcurrentHashMap<>();

        this.entityStore.executeInReadonlyTransaction(txn -> {
            for (Entity clusterInfo : txn.getAll(DATANODE_CLUSTER)) {

                String name = parse(clusterInfo.getProperty("name"));
                EntityIterable addresses = clusterInfo.getLinks("address");

                Set<InetSocketAddress> addrSet = createAddrSet();

                clusterInfoMap.put(name, addrSet);

                for (Entity address : addresses) {
                    String ipAddr = parse(address.getProperty("ip"));
                    int port = parse(address.getProperty("port"));

                    addrSet.add(new InetSocketAddress(ipAddr, port));
                }
            }
        });
    }

    /**
     * 新增一个DataNode节点集群，如果已经存在则创建失败
     *
     * @param clusterName 集群名称
     * @param addresses 所有节点的{@link InetSocketAddress}列表
     * @return 是否成功创建
     */
    public boolean addDataNodeCluster(String clusterName, InetSocketAddress[] addresses) {
        return entityStore.computeInExclusiveTransaction(txn -> {

            if (clusterInfoMap.containsKey(clusterName)) {
                return false;
            }

            Set<InetSocketAddress> addrSet = createAddrSet();

            Entity clusterInfo = txn.newEntity(DATANODE_CLUSTER);
            clusterInfo.setProperty("name", clusterName);

            for (InetSocketAddress address : addresses) {
                Entity addrEntity = txn.newEntity(DATANODE_CLUSTER_ADDRESS);

                addrEntity.setProperty("ip", address.getAddress().getHostAddress());
                addrEntity.setProperty("port", address.getPort());

                clusterInfo.addLink("address", addrEntity);
                addrSet.add(address);
            }

            clusterInfoMap.put(clusterName, addrSet);
            return true;
        });
    }

    /**
     * 删除一个DataNode节点集群，如果不存在则删除失败
     *
     * @param clusterName 集群名称
     * @return 是否成功删除
     */
    public boolean removeDataNodeCluster(String clusterName) {
        return entityStore.computeInExclusiveTransaction(txn -> {
            if (txn.find(DATANODE_CLUSTER, "name", clusterName).isEmpty()) {
                return false;
            }

            Entity clusterInfo = txn.find(DATANODE_CLUSTER, "name", clusterName).getFirst();

            if (clusterInfo != null) {
                for (Entity addrEntity : clusterInfo.getLinks("address")) {
                    addrEntity.delete();
                }
                clusterInfo.delete();
            }

            clusterInfoMap.remove(clusterName);
            return true;
        });
    }

    /**
     * 将所有DataNode集群的信息，读入给定的{@link ByteBuf}字节缓冲区
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    public void readDataNodeInfo(ByteBuf byteBuf) {
        for (Map.Entry<String, Set<InetSocketAddress>> clusterInfo : clusterInfoMap.entrySet()) {

            Set<InetSocketAddress> addrSet = clusterInfo.getValue();
            String clusterName = clusterInfo.getKey();

            writeString(byteBuf, clusterName);
            byteBuf.writeInt(addrSet.size());

            for (InetSocketAddress addr : addrSet) {

                String ipAddr = addr.getAddress().getHostAddress();
                int port = addr.getPort();

                writeString(byteBuf, ipAddr);
                byteBuf.writeInt(port);
            }
        }
    }

}
