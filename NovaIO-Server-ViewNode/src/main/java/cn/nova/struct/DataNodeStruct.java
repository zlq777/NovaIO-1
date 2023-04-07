package cn.nova.struct;

import cn.nova.LocalStorage;
import io.netty.buffer.ByteBuf;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static cn.nova.CommonUtils.*;

/**
 * {@link DataNodeStruct}是DataNode集群相关的信息结构体
 *
 * @author RealDragonking
 */
public class DataNodeStruct {

    private final Map<String, Set<InetSocketAddress>> dataNodeMap;
    private final Comparator<InetSocketAddress> addrComparator;
    private final LocalStorage storage;
    private final Lock locker;

    public DataNodeStruct(LocalStorage storage) {
        this.addrComparator = createAddressComparator();
        this.dataNodeMap = new HashMap<>();
        this.locker = new ReentrantLock();
        this.storage = storage;
        initDataNodeMap();
    }

    /**
     * 往一个DataNode集群的信息结构体中，加入一个新节点的{@link java.net.InetSocketAddress}
     *
     * @param clusterName 集群名称
     * @param address {@link InetSocketAddress}
     * @param bucket 作为临时载体的{@link ByteBuf}字节缓冲区
     */
    public boolean addNewDataNode(String clusterName, InetSocketAddress address, ByteBuf bucket) {
        locker.lock();
        boolean result = addNewDataNode0(clusterName, address, bucket);
        locker.unlock();
        bucket.release();
        return result;
    }

    /**
     * 具体执行往一个DataNode集群的信息结构体中，加入一个新节点的{@link java.net.InetSocketAddress}
     *
     * @param clusterName 集群名称
     * @param address {@link InetSocketAddress}
     * @param bucket 作为临时载体的{@link ByteBuf}字节缓冲区
     */
    private boolean addNewDataNode0(String clusterName, InetSocketAddress address, ByteBuf bucket) {
        Set<InetSocketAddress> addrSet = dataNodeMap.get(clusterName);

        if (addrSet == null) {
            addrSet = new TreeSet<>(addrComparator);
            dataNodeMap.put(clusterName, addrSet);

            for (String name : dataNodeMap.keySet()) {
                writeString(bucket, name);
            }

            storage.writeBytes("clusters", bucket);
            bucket.clear();
        }

        if (addrSet.add(address)) {
            for (InetSocketAddress addr : addrSet) {
                String ipAddr = addr.getAddress().getHostAddress();
                int port = addr.getPort();

                writeString(bucket, ipAddr);
                bucket.writeInt(port);
            }

            storage.writeBytes("cluster:" + clusterName, bucket);
            return true;
        } else {
            return false;
        }
    }

    /**
     * 将所有DataNode集群的信息，读入给定的{@link ByteBuf}字节缓冲区
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    public void readDataNodeInfo(ByteBuf byteBuf) {
        locker.lock();
        readDataNodeInfo0(byteBuf);
        locker.unlock();
    }

    /**
     * 具体执行将所有DataNode集群的信息，读入给定的{@link ByteBuf}字节缓冲区
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    private void readDataNodeInfo0(ByteBuf byteBuf) {

    }

    /**
     * 从硬盘中初始化加载所有DataNode集群的信息，并写入到{@link #dataNodeMap}中
     */
    private void initDataNodeMap() {
        ByteBuf byteBuf = storage.readBytes("clusters");

        while (byteBuf.readableBytes() > 0) {
            String clusterName = readString(byteBuf);
            dataNodeMap.put(clusterName, new TreeSet<>(addrComparator));
        }

        for (Map.Entry<String, Set<InetSocketAddress>> entry : dataNodeMap.entrySet()) {
            String clusterName = entry.getKey();
            Set<InetSocketAddress> addrSet = entry.getValue();

            storage.readBytes("cluster:" + clusterName, byteBuf);

            while (byteBuf.readableBytes() > 0) {
                String ipAddr = readString(byteBuf);
                int port = byteBuf.readInt();
                addrSet.add(new InetSocketAddress(ipAddr, port));
            }
        }
    }

}
