package cn.nova;

import cn.nova.cluster.AbstractRaftCore;
import cn.nova.cluster.ClusterInfo;
import cn.nova.config.TimeConfig;
import cn.nova.network.UDPService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.nova.OperateCode.*;
import static cn.nova.CommonUtils.*;

/**
 * {@link ViewNodeRaftCore}给出了ViewNode节点的EntryData应用逻辑
 *
 * @author RealDragonking
 */
public class ViewNodeRaftCore extends AbstractRaftCore {

    private static final Logger log = LogManager.getLogger(ViewNodeRaftCore.class);
    private final Map<String, List<InetSocketAddress>> dataNodeMap;
    private final ByteBufAllocator alloc;
    private final LocalStorage storage;

    public ViewNodeRaftCore(ClusterInfo clusterInfo,
                            ByteBufAllocator alloc,
                            TimeConfig timeConfig,
                            UDPService udpService,
                            LocalStorage storage,
                            Timer timer,
                            int tickTime) {
        super(clusterInfo, alloc, timeConfig, udpService, storage, timer, tickTime);
        this.dataNodeMap = new HashMap<>();
        this.storage = storage;
        this.alloc = alloc;
    }

    /**
     * 应用已经完成集群多数派写入的Entry数据。leader节点需要返回响应数据结构体
     *
     * @param isLeader    当前节点是否作为leader节点完成了这一Entry数据的同步
     * @param entryIndex  已经完成集群多数派写入的Entry序列号
     * @param entryData   已经完成集群多数派写入的Entry数据
     * @param asyncFuture {@link AsyncFuture}
     */
    @Override
    public void applyEntry(boolean isLeader, long entryIndex, ByteBuf entryData, AsyncFuture<?> asyncFuture) {
        int operateCode = entryData.readInt();

        switch (operateCode) {
            case ADD_NEW_DATANODE :
                String clusterName = readString(entryData);
                String ipAddress = readString(entryData);
                int port = entryData.readInt();
                InetSocketAddress address = new InetSocketAddress(ipAddress, port);

                entryData.release();

                addNewDataNode(clusterName, address);
        }
    }

    /**
     * 往一个DataNode集群的信息结构体中，加入一个新节点的{@link java.net.InetSocketAddress}
     *
     * @param clusterName 集群名称
     * @param address {@link InetSocketAddress}
     */
    private void addNewDataNode(String clusterName, InetSocketAddress address) {
        List<InetSocketAddress> addressList = dataNodeMap.get(clusterName);
        ByteBuf byteBuf = alloc.buffer();

        if (addressList == null) {
            addressList = new ArrayList<>();
            dataNodeMap.put(clusterName, addressList);

            for (String name : dataNodeMap.keySet()) {
                writeString(byteBuf, name);
            }

            storage.writeBytes("clusters", byteBuf);
            byteBuf.clear();
        }

        addressList.add(address);
    }

}
