package cn.nova.struct;

import cn.nova.LocalStorage;
import io.netty.buffer.ByteBuf;

import java.net.InetSocketAddress;
import java.util.*;

import static cn.nova.CommonUtils.*;

/**
 * {@link DataNodeInfoStruct}是DataNode集群相关的信息结构体
 *
 * @author RealDragonking
 */
public class DataNodeInfoStruct {

    private final Map<String, Set<InetSocketAddress>> dataNodeInfoMap;
    private final LocalStorage storage;

    public DataNodeInfoStruct(LocalStorage storage) {
        this.dataNodeInfoMap = new HashMap<>();
        this.storage = storage;
        initDataNodeInfoMap();
    }

    /**
     * 新增一个DataNode节点集群，如果已经存在则创建失败
     *
     * @param clusterName 集群名称
     * @param addresses 所有节点的{@link InetSocketAddress}列表
     * @param byteBuf 用于读写的{@link ByteBuf}字节缓冲区。复用已有的，去掉了二次创建带来的开销
     * @return 是否成功创建
     */
    public boolean addDataNodeCluster(String clusterName, InetSocketAddress[] addresses, ByteBuf byteBuf) {
        try {
            synchronized (this) {
                Set<InetSocketAddress> addrSet = dataNodeInfoMap.get(clusterName);
                if (addrSet != null) {
                    return false;
                }

                dataNodeInfoMap.put(clusterName, addrSet = createAddrSet());

                for (String name : dataNodeInfoMap.keySet()) {
                    writeString(byteBuf, name);
                }

                storage.writeBytes("clusters", byteBuf);
                byteBuf.clear();

                for (InetSocketAddress address : addresses) {
                    String ipAddr = address.getAddress().getHostAddress();
                    int port = address.getPort();

                    addrSet.add(address);

                    writeString(byteBuf, ipAddr);
                    byteBuf.writeInt(port);
                }

                storage.writeBytes("cluster:" + clusterName, byteBuf);
                return true;
            }
        } finally {
            byteBuf.release();
        }
    }

    /**
     * 删除一个DataNode节点集群，如果不存在则删除失败
     *
     * @param clusterName 集群名称
     * @param byteBuf 用于读写的{@link ByteBuf}字节缓冲区。复用已有的，去掉了二次创建带来的开销
     * @return 是否成功删除
     */
    public boolean removeDataNodeCluster(String clusterName, ByteBuf byteBuf) {
        try {
            synchronized (this) {
                if (dataNodeInfoMap.remove(clusterName) == null) {
                    return false;
                }

                for (String name : dataNodeInfoMap.keySet()) {
                    writeString(byteBuf, name);
                }

                storage.writeBytes("clusters", byteBuf);
                byteBuf.clear();

                // 实现LocalStorage的删除能力

                return true;
            }
        } finally {
            byteBuf.release();
        }
    }

    /**
     * 将所有DataNode集群的信息，读入给定的{@link ByteBuf}字节缓冲区
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    public void readDataNodeInfo(ByteBuf byteBuf) {
        synchronized (this) {
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
        }
    }

    /**
     * 从硬盘中初始化加载所有DataNode集群的信息，并写入到{@link #dataNodeInfoMap}中
     */
    private void initDataNodeInfoMap() {
        ByteBuf byteBuf = storage.readBytes("clusters");

        while (byteBuf.readableBytes() > 0) {
            dataNodeInfoMap.put(readString(byteBuf), createAddrSet());
        }

        for (Map.Entry<String, Set<InetSocketAddress>> entry : dataNodeInfoMap.entrySet()) {
            Set<InetSocketAddress> addrSet = entry.getValue();

            storage.readBytes("cluster:" + entry.getKey(), byteBuf);

            while (byteBuf.readableBytes() > 0) {
                String ipAddr = readString(byteBuf);
                int port = byteBuf.readInt();

                addrSet.add(new InetSocketAddress(ipAddr, port));
            }
        }

        byteBuf.release();
    }

}
