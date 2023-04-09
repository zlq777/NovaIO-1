package cn.nova;

import cn.nova.cluster.AbstractRaftCore;
import cn.nova.cluster.ClusterInfo;
import cn.nova.config.TimeConfig;
import cn.nova.network.UDPService;
import cn.nova.struct.DataNodeInfoStruct;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Timer;

import java.net.InetSocketAddress;

import static cn.nova.OperateCode.*;
import static cn.nova.CommonUtils.*;

/**
 * {@link ViewNodeRaftCore}给出了ViewNode节点的EntryData应用逻辑
 *
 * @author RealDragonking
 */
@SuppressWarnings("unchecked")
public final class ViewNodeRaftCore extends AbstractRaftCore {

    private final DataNodeInfoStruct dataNodeInfoStruct;

    public ViewNodeRaftCore(DataNodeInfoStruct dataNodeInfoStruct,
                            LocalStorageGroup storageGroup,
                            ClusterInfo clusterInfo,
                            ByteBufAllocator alloc,
                            TimeConfig timeConfig,
                            UDPService udpService,
                            Timer timer,
                            int tickTime) {

        super(storageGroup, clusterInfo, alloc, timeConfig, udpService, timer, tickTime);
        this.dataNodeInfoStruct = dataNodeInfoStruct;
    }

    /**
     * 应用已经完成集群多数派写入的Entry数据，leader节点会额外给出对应客户端请求的{@link AsyncFuture}
     *
     * @param entryIndex  已经完成集群多数派写入的Entry序列号
     * @param entryData   已经完成集群多数派写入的Entry数据
     * @param asyncFuture {@link AsyncFuture}
     */
    @Override
    public void applyEntry(long entryIndex, ByteBuf entryData, AsyncFuture<?> asyncFuture) {
        int operateCode = entryData.readInt();

        switch (operateCode) {
            case ADD_DATANODE_CLUSTER :
                doAddDataNodeCluster(entryData, asyncFuture);
                break;
            case REMOVE_DATANODE_CLUSTER :
                doRemoveDataNodeCluster(entryData, asyncFuture);
                break;
        }
        entryData.release();
    }

    /**
     * 对应于{@link OperateCode#ADD_DATANODE_CLUSTER}的数据处理逻辑
     *
     * @param entryData 已经完成集群多数派写入的Entry数据
     * @param asyncFuture {@link AsyncFuture}
     */
    private void doAddDataNodeCluster(ByteBuf entryData, AsyncFuture<?> asyncFuture) {
        String clusterName = readString(entryData);
        int nodeNumber = entryData.readInt();

        InetSocketAddress[] addresses = new InetSocketAddress[nodeNumber];

        for (int i = 0; i < nodeNumber; i++) {
            String ipAddress = readString(entryData);
            int port = entryData.readInt();

            addresses[i] = new InetSocketAddress(ipAddress, port);
        }

        boolean isSuccess = dataNodeInfoStruct.addDataNodeCluster(clusterName, addresses);

        if (asyncFuture != null) {
            ((AsyncFuture<Boolean>)asyncFuture).notifyResult(isSuccess);
        }
    }

    /**
     * 对应于{@link OperateCode#REMOVE_DATANODE_CLUSTER}的数据处理逻辑
     *
     * @param entryData 已经完成集群多数派写入的Entry数据
     * @param asyncFuture {@link AsyncFuture}
     */
    private void doRemoveDataNodeCluster(ByteBuf entryData, AsyncFuture<?> asyncFuture) {
        String clusterName = readString(entryData);

        boolean isSuccess = dataNodeInfoStruct.removeDataNodeCluster(clusterName);

        if (asyncFuture != null) {
            ((AsyncFuture<Boolean>)asyncFuture).notifyResult(isSuccess);
        }
    }

}
