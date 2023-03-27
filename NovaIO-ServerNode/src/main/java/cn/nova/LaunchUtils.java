package cn.nova;

import cn.nova.cluster.ClusterInfo;
import cn.nova.cluster.ClusterNode;
import cn.nova.config.NetworkConfig;
import cn.nova.config.SourceConfig;
import cn.nova.network.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.netty.channel.epoll.Epoll;
import io.netty.util.internal.PlatformDependent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 提供了一些启动过程中会用到的方法
 *
 * @author RealDragonking
 */
public final class LaunchUtils {

    private static final Logger LOG = LogManager.getLogger(Launcher.class);

    private LaunchUtils() {}

    /**
     * 打印出NovaIO的logo
     */
    public static void printLogo() {
        System.out.println("\n" +
                "                                                                     \n" +
                "         ,--.                                             ,----..    \n" +
                "       ,--.'|                                    ,---,   /   /   \\   \n" +
                "   ,--,:  : |                                 ,`--.' |  /   .     :  \n" +
                ",`--.'`|  ' :   ,---.                         |   :  : .   /   ;.  \\ \n" +
                "|   :  :  | |  '   ,'\\      .---.             :   |  '.   ;   /  ` ; \n" +
                ":   |   \\ | : /   /   |   /.  ./|   ,--.--.   |   :  |;   |  ; \\ ; | \n" +
                "|   : '  '; |.   ; ,. : .-' . ' |  /       \\  '   '  ;|   :  | ; | ' \n" +
                "'   ' ;.    ;'   | |: :/___/ \\: | .--.  .-. | |   |  |.   |  ' ' ' : \n" +
                "|   | | \\   |'   | .; :.   \\  ' .  \\__\\/: . . '   :  ;'   ;  \\; /  | \n" +
                "'   : |  ; .'|   :    | \\   \\   '  ,\" .--.; | |   |  ' \\   \\  ',  /  \n" +
                "|   | '`--'   \\   \\  /   \\   \\    /  /  ,.  | '   :  |  ;   :    /   \n" +
                "'   : |        `----'     \\   \\ |;  :   .'   \\;   |.'    \\   \\ .'    \n" +
                ";   |.'                    '---\" |  ,     .-./'---'       `---`      \n" +
                "'---'                             `--`---'                           \n" +
                "                                                                     \n");
    }

    /**
     * 初始化加载{@link SourceConfig}
     *
     * @return {@link SourceConfig}
     */
    public static SourceConfig initSourceConfig() {
        LOG.info("正在初始化加载本地配置信息...");

        SourceConfig srcConfig = SourceConfig.init();

        if (srcConfig == null) {
            LOG.error("本地配置信息加载失败，当前节点进程正在关闭...");
            System.exit(0);
        } else {
            LOG.info("本地配置信息加载成功");
        }

        return srcConfig;
    }

    /**
     * 初始化加载集群成员的配置信息
     *
     * @return 集群成员的配置信息
     */
    public static ClusterInfo initClusterNodeInfo() {
        LOG.info("正在初始化加载集群配置信息...");

        File configFile = new File("./cluster.json5");
        ClusterInfo clusterInfo = null;

        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {

                JSONObject rootInfo = JSON.parseObject(input, JSONObject.class);

                int index = rootInfo.getIntValue("index", -1);
                JSONObject[] otherNodeInfos = rootInfo.getObject("other-nodes", JSONObject[].class);

                int num = 0;
                List<ClusterNode> tempList = new ArrayList<>();

                if (otherNodeInfos != null && index > -1) {
                    for (JSONObject otherNodeInfo : otherNodeInfos) {

                        String host = otherNodeInfo.getString("udp-host");
                        int port = otherNodeInfo.getIntValue("udp-port");

                        if (host != null) {
                            InetSocketAddress address = new InetSocketAddress(host, port);
                            ClusterNode node = new ClusterNode(num, address);

                            tempList.add(node);
                            num ++;
                        }
                    }

                    ClusterNode[] otherNodes = new ClusterNode[num];
                    for (int i = 0; i < num; i++) {
                        otherNodes[i] = tempList.get(i);
                    }

                    clusterInfo = new ClusterInfo(index, otherNodes);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (clusterInfo == null) {
            LOG.info("集群配置信息加载失败，当前节点进程正在关闭...");
            System.exit(0);
        } else {
            LOG.info("集群配置信息加载成功");
        }

        return clusterInfo;
    }

    /**
     * 初始化加载{@link LocalStorage}
     *
     * @return {@link LocalStorage}
     */
    public static LocalStorage initEntryStore() {
        LOG.info("正在初始化加载本地数据库...");

        LocalStorage entryStore = null;

        try {
            entryStore = new LocalStorageImpl();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (entryStore == null) {
            LOG.info("本地数据库加载失败，当前节点进程正在关闭...");
            System.exit(0);
        } else {
            LOG.info("本地数据库加载成功");
        }

        return entryStore;
    }

    /**
     * 初始化启动{@link UDPService}
     *
     * @param config {@link NetworkConfig}
     * @param provider {@link UDPMsgHandler}
     * @return {@link UDPService}
     */
    public static UDPService initUDPService(NetworkConfig config, MsgHandler provider) {
        LOG.info("正在初始化启动UDP服务...");

        UDPService udpService;

        if (Epoll.isAvailable()) {
            udpService = new EpollUDPService(config);
        } else {
            udpService = new GeneralUDPService(config);
        }

        LOG.info("检测到基于 " + PlatformDependent.normalizedOs() + "_" + PlatformDependent.normalizedArch() +
                " 平台运行，使用 " + (Epoll.isAvailable() ? "epoll" : "general") + " io内核");

        if (! udpService.start(provider)) {
            LOG.error("UDP服务启动失败，当前节点进程正在关闭...");
            System.exit(0);
        } else {
            LOG.info("UDP服务启动成功，正在监听 " + config.getUDPListenPort() + " 端口");
        }

        return udpService;
    }

    /**
     * 设置当前节点进程被关闭时应当执行的{@link Runnable}回调函数
     *
     * @param task {@link Runnable}
     */
    public static void onShutdown(Runnable task) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("当前节点进程正在关闭...");
            task.run();
        }, "shutdown"));
    }

}
