package cn.nova;
import cn.nova.cluster.ClusterInfo;
import cn.nova.cluster.RaftCore;
import cn.nova.cluster.RaftService;
import cn.nova.config.NetworkConfig;
import cn.nova.config.SourceConfig;
import cn.nova.config.TimeConfig;
import cn.nova.network.*;
import cn.nova.struct.DataNodeStruct;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.HashedWheelTimer;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import io.netty.util.Timer;

import java.util.concurrent.TimeUnit;

import static cn.nova.CommonUtils.getThreadFactory;
import static cn.nova.LaunchUtils.*;

/**
 * ViewNode节点的启动类
 *
 * @author RealDragonking
 */
public final class ViewNodeLauncher {

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(Level.PARANOID);
        printLogo();

        ClusterInfo clusterInfo = initClusterNodeInfo();

        SourceConfig srcConfig = initSourceConfig();
        NetworkConfig netConfig = new NetworkConfig(srcConfig);
        TimeConfig timeConfig = new TimeConfig(srcConfig);

        LocalStorage storage = initLocalStorage();

        MsgHandler udpHandler = new UDPMsgHandler();
        MsgHandler tcpHandler = new TCPMsgHandler();

        NetworkServiceGroup group = selectNetworkService(netConfig, udpHandler, tcpHandler);
        UDPService udpService = group.getUdpService();
        TCPService tcpService = group.getTcpService();

        int tickTime = 20;

        Timer timer = new HashedWheelTimer(
                getThreadFactory("timer", false),
                tickTime,
                TimeUnit.MILLISECONDS);

        DataNodeStruct dataNodeStruct = new DataNodeStruct(storage);

        RaftCore raftCore = new ViewNodeRaftCore(
                dataNodeStruct,
                clusterInfo,
                ByteBufAllocator.DEFAULT,
                timeConfig,
                udpService,
                storage,
                timer,
                tickTime);

        udpHandler.register(new RaftService(raftCore));

        tcpHandler.register(new ViewNodeClientService(raftCore, dataNodeStruct));

        onShutdown(() -> {
            udpService.close();
            tcpService.close();
            storage.close();
            timer.stop();
        });

        startUDPService(udpService);
        raftCore.start();
        startTCPService(tcpService);
    }

}
