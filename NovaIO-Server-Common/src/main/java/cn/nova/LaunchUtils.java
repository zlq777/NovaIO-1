package cn.nova;

import cn.nova.cluster.ClusterInfo;
import cn.nova.cluster.ClusterNode;
import cn.nova.config.NetworkConfig;
import cn.nova.config.SourceConfig;
import cn.nova.jsonutils.JSONParser;
import cn.nova.jsonutils.model.JsonArray;
import cn.nova.jsonutils.model.JsonObject;
import cn.nova.network.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.artbits.quickio.api.KV;
import com.github.artbits.quickio.core.QuickIO;
import io.netty.channel.epoll.Epoll;
import io.netty.util.internal.PlatformDependent;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 提供了一些启动过程中会用到的方法
 *
 * @author RealDragonking
 */
public final class LaunchUtils {

    private static final Logger LOG = LogManager.getLogger(LaunchUtils.class);

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
     * 初始化加载{@link ClusterInfo}集群配置信息
     *
     * @return {@link ClusterInfo}
     */
    public static ClusterInfo initClusterNodeInfo() {
        LOG.info("正在初始化加载集群配置信息...");

        File configFile = new File("./cluster.json5");
        ClusterInfo clusterInfo = null;

        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {

                JSONParser jsonParser = new JSONParser();
                JsonObject info = jsonParser.fromJSON(new InputStreamReader(input),JsonObject.class);

                int index = info.get("index", Integer.class);
                int num = 0;
                List<ClusterNode> tempList = new ArrayList<>();
                JsonArray otherJson = info.getJsonArray("other-nodes");

                for(Object json : otherJson){
                    JsonObject jsonObject = (JsonObject) json;
                    String host = jsonObject.get("udp-host",String.class);
                    int port = jsonObject.get("udp-port",Integer.class);

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
     * 初始化加载{@link com.github.artbits.quickio.api.KV}和{@link jetbrains.exodus.entitystore.PersistentEntityStore}，
     * 并通过{@link LocalStorageGroup}打包返回
     *
     * @return {@link LocalStorageGroup}
     */
    public static LocalStorageGroup initLocalStorage() {
        LOG.info("正在初始化加载本地数据库...");

        LocalStorageGroup localStorage = null;

        try {
            KV kvStore = QuickIO.usingKV(".");
            PersistentEntityStore entityStore = PersistentEntityStores.newInstance("data");

            localStorage = new LocalStorageGroup(kvStore, entityStore);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (localStorage == null) {
            LOG.info("本地数据库加载失败，当前节点进程正在关闭...");
            System.exit(0);
        } else {
            LOG.info("本地数据库加载成功");
        }

        return localStorage;
    }

    /**
     * 基于当前运行环境，选择合适的{@link UDPService}和{@link TCPService}，并通过{@link NetworkServiceGroup}打包返回
     *
     * @param config {@link NetworkConfig}
     * @param udpHandler 处理udp通信的{@link MsgHandler}
     * @param tcpHandler 处理tcp通信的{@link MsgHandler}
     * @return {@link NetworkServiceGroup}
     */
    public static NetworkServiceGroup selectNetworkService(NetworkConfig config,
                                                           MsgHandler udpHandler, MsgHandler tcpHandler) {
        LOG.info("正在初始化选择网络io内核...");

        UDPService udpService;
        TCPService tcpService;

        if (Epoll.isAvailable()) {
            udpService = new EpollUDPService(config, udpHandler);
            tcpService = new EpollTCPService(config, tcpHandler);
        } else {
            udpService = new GeneralUDPService(config, udpHandler);
            tcpService = new GeneralTCPService(config, tcpHandler);
        }

        LOG.info("检测到基于 " + PlatformDependent.normalizedOs() + "_" + PlatformDependent.normalizedArch() +
                " 平台运行，使用 " + (Epoll.isAvailable() ? "epoll" : "general") + " 网络io内核");

        return new NetworkServiceGroup(udpService, tcpService);
    }

    /**
     * 尝试启动{@link UDPService}
     *
     * @param udpService {@link UDPService}
     */
    public static void startUDPService(UDPService udpService) {
        LOG.info("正在初始化启动UDP服务...");
        if (udpService.start()) {
            LOG.info("UDP服务启动成功");
        } else {
            LOG.info("UDP服务启动失败，正在退出当前节点进程...");
            System.exit(0);
        }
    }

    /**
     * 尝试启动{@link TCPService}
     *
     * @param tcpService {@link TCPService}
     */
    public static void startTCPService(TCPService tcpService) {
        LOG.info("正在初始化启动TCP服务...");
        if (tcpService.start()) {
            LOG.info("TCP服务启动成功");
        } else {
            LOG.info("TCP服务启动失败，正在退出当前节点进程...");
            System.exit(0);
        }
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
