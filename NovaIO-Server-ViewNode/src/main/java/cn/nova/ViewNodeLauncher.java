package cn.nova;
import cn.nova.cluster.ClusterInfo;
import cn.nova.cluster.RaftCore;
import cn.nova.cluster.RaftService;
import cn.nova.config.NetworkConfig;
import cn.nova.config.SourceConfig;
import cn.nova.config.TimeConfig;
import cn.nova.network.*;
import cn.nova.service.DataNodeInfoService;
import cn.nova.service.FileSystemService;
import cn.nova.service.RaftInfoService;
import cn.nova.struct.DataNodeInfoStruct;
import cn.nova.struct.FileSystemStruct;
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

        LocalStorageGroup storageGroup = initLocalStorage();

        MessageHandler udpHandler = new UDPMessageHandler();
        MessageHandler tcpHandler = new TCPMessageHandler();

        NetworkServiceGroup networkGroup = selectNetworkService(netConfig, udpHandler, tcpHandler);
        UDPService udpService = networkGroup.getUdpService();
        TCPService tcpService = networkGroup.getTcpService();

        int tickTime = 20;

        Timer timer = new HashedWheelTimer(
                getThreadFactory("timer", false),
                tickTime,
                TimeUnit.MILLISECONDS);

        DataNodeInfoStruct dataNodeInfoStruct = new DataNodeInfoStruct(storageGroup);
        FileSystemStruct fileSystemStruct = new FileSystemStruct(storageGroup);

        RaftCore raftCore = new ViewNodeRaftCore(
                dataNodeInfoStruct,
                fileSystemStruct,
                storageGroup,
                clusterInfo,
                ByteBufAllocator.DEFAULT,
                timeConfig,
                udpService,
                timer,
                tickTime);

        udpHandler.register(new RaftService(raftCore));

        tcpHandler
                .register(new RaftInfoService(raftCore))
                .register(new DataNodeInfoService(raftCore, dataNodeInfoStruct))
                .register(new FileSystemService(raftCore, fileSystemStruct));

        onShutdown(() -> {
            timer.stop();
            networkGroup.close();
            storageGroup.close();
        });

        startUDPService(udpService);
        raftCore.start();
        startTCPService(tcpService);
    }

}
