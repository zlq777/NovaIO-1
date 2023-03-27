package cn.nova;

import cn.nova.cluster.ClusterInfo;
import cn.nova.cluster.RaftStateMachine;
import cn.nova.cluster.RaftStateMachineImpl;
import cn.nova.config.SourceConfig;
import cn.nova.config.NetworkConfig;
import cn.nova.config.TimeConfig;
import cn.nova.network.MsgHandler;
import cn.nova.network.TCPMsgHandler;
import cn.nova.network.UDPMsgHandler;
import cn.nova.network.UDPService;
import cn.nova.service.RaftService;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.HashedWheelTimer;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import io.netty.util.Timer;

import java.util.concurrent.TimeUnit;

import static cn.nova.CommonUtils.getThreadFactory;
import static cn.nova.LaunchUtils.*;

/**
 * {@link Launcher}是一个初始化启动的入口，并且执行了一些硬编码的初始化设置
 *
 * @author RealDragonking
 */
public final class Launcher {

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(Level.PARANOID);
        printLogo();

        ClusterInfo clusterInfo = initClusterNodeInfo();

        SourceConfig srcConfig = initSourceConfig();
        NetworkConfig netConfig = new NetworkConfig(srcConfig);
        TimeConfig timeConfig = new TimeConfig(srcConfig);

        LocalStorage storage = initEntryStore();

        MsgHandler udpHandler = new UDPMsgHandler();
        UDPService udpService = initUDPService(netConfig, udpHandler);

        MsgHandler tcpHandler = new TCPMsgHandler();

        int tickTime = 20;

        Timer timer = new HashedWheelTimer(
                getThreadFactory("timer", false),
                tickTime,
                TimeUnit.MILLISECONDS);

        RaftStateMachine stateMachine = new RaftStateMachineImpl(
                clusterInfo,
                ByteBufAllocator.DEFAULT,
                timeConfig,
                udpService,
                storage,
                timer,
                tickTime);

        udpHandler.register(new RaftService(stateMachine));

        onShutdown(() -> {
            udpService.close();
            storage.close();
            timer.stop();
        });

        stateMachine.start();
    }

}
