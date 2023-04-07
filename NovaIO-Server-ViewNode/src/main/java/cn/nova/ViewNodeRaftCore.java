package cn.nova;

import cn.nova.cluster.AbstractRaftCore;
import cn.nova.cluster.ClusterInfo;
import cn.nova.config.TimeConfig;
import cn.nova.network.UDPService;
import cn.nova.struct.DataNodeInfoStruct;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;

import static cn.nova.OperateCode.*;
import static cn.nova.CommonUtils.*;

/**
 * {@link ViewNodeRaftCore}给出了ViewNode节点的EntryData应用逻辑
 *
 * @author RealDragonking
 */
public class ViewNodeRaftCore extends AbstractRaftCore {

    private static final Logger log = LogManager.getLogger(ViewNodeRaftCore.class);
    private final DataNodeInfoStruct dataNodeInfoStruct;

    public ViewNodeRaftCore(DataNodeInfoStruct dataNodeInfoStruct,
                            ClusterInfo clusterInfo,
                            ByteBufAllocator alloc,
                            TimeConfig timeConfig,
                            UDPService udpService,
                            LocalStorage storage,
                            Timer timer,
                            int tickTime) {
        super(clusterInfo, alloc, timeConfig, udpService, storage, timer, tickTime);
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
    @SuppressWarnings("unchecked")
    public void applyEntry(long entryIndex, ByteBuf entryData, AsyncFuture<?> asyncFuture) {
        int operateCode = entryData.readInt();

        switch (operateCode) {
            case ADD_NEW_DATANODE_CLUSTER:
                String clusterName = readString(entryData);
                int nodeNumber = entryData.readInt();

                InetSocketAddress[] addresses = new InetSocketAddress[nodeNumber];

                for (int i = 0; i < nodeNumber; i++) {
                    String ipAddress = readString(entryData);
                    int port = entryData.readInt();

                    addresses[i] = new InetSocketAddress(ipAddress, port);
                }

                boolean isSuccess = dataNodeInfoStruct.addNewDataNodeCluster(clusterName, addresses, entryData);

                if (asyncFuture != null) {
                    ((AsyncFuture<Boolean>)asyncFuture).notifyResult(isSuccess);
                }
                break;
        }
    }

}
