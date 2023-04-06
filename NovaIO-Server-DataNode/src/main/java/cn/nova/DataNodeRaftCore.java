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
     * 应用已经完成集群多数派写入的Entry数据。leader节点需要返回{@link ByteBuf}模式的标准响应体
     * （即在写入了头部长度、session字段的{@link ByteBuf}）
     *
     * @param isLeader   当前节点是否作为leader节点完成了这一Entry数据的同步
     * @param entryIndex 已经完成集群多数派写入的Entry序列号
     * @param entryData  已经完成集群多数派写入的Entry数据
     */
    @Override
    public ByteBuf applyEntry(boolean isLeader, long entryIndex, ByteBuf entryData) {
        return null;
    }

}
