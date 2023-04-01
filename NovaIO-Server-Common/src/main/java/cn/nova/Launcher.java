package cn.nova;


/**
 * {@link Launcher}是一个初始化启动的入口，并且执行了一些硬编码的初始化设置
 *
 * @author RealDragonking
 */
public final class Launcher {

    public static void main(String[] args) {
//        ResourceLeakDetector.setLevel(Level.PARANOID);
//        printLogo();
//
//        ClusterInfo clusterInfo = initClusterNodeInfo();
//
//        SourceConfig srcConfig = initSourceConfig();
//        NetworkConfig netConfig = new NetworkConfig(srcConfig);
//        TimeConfig timeConfig = new TimeConfig(srcConfig);
//
//        LocalStorage storage = initEntryStore();
//
//        MsgHandler udpHandler = new UDPMsgHandler();
//        MsgHandler tcpHandler = new TCPMsgHandler();
//
//        NetworkServiceGroup group = selectNetworkService(netConfig, udpHandler, tcpHandler);
//        UDPService udpService = group.getUdpService();
//        TCPService tcpService = group.getTcpService();
//
//        int tickTime = 20;
//
//        Timer timer = new HashedWheelTimer(
//                getThreadFactory("timer", false),
//                tickTime,
//                TimeUnit.MILLISECONDS);
//
//        RaftNode stateMachine = new RaftNodeImpl(
//                clusterInfo,
//                ByteBufAllocator.DEFAULT,
//                timeConfig,
//                udpService,
//                storage,
//                timer,
//                tickTime);
//
//        udpHandler.register(new RaftService(stateMachine));
//
//        onShutdown(() -> {
//            udpService.close();
//            tcpService.close();
//            storage.close();
//            timer.stop();
//        });
//
//        startUDPService(udpService);
//        stateMachine.start();
//        startTCPService(tcpService);
    }

}
