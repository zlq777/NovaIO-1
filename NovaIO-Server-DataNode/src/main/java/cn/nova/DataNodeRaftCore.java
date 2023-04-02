package cn.nova;

import cn.nova.cluster.AbstractRaftCore;
import cn.nova.cluster.ClusterInfo;
import cn.nova.config.TimeConfig;
import cn.nova.network.UDPService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Timer;

/**
 * {@link DataNodeRaftCore}给出了DataNode节点的EntryData应用逻辑
 *
 * @author RealDragonking
 */
public class DataNodeRaftCore extends AbstractRaftCore {

    public DataNodeRaftCore(ClusterInfo clusterInfo,
                            ByteBufAllocator alloc,
                            TimeConfig timeConfig,
                            UDPService udpService,
                            LocalStorage storage,
                            Timer timer,
                            int tickTime) {
        super(clusterInfo, alloc, timeConfig, udpService, storage, timer, tickTime);
    }

    /**
     * 应用已经完成集群多数派写入的Entry数据
     *
     * @param entryIndex 已经完成集群多数派写入的Entry序列号
     * @param entryData  已经完成集群多数派写入的Entry数据
     */
    @Override
    public void applyEntry(long entryIndex, ByteBuf entryData) {

    }

}
