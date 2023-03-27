package cn.nova.config;

/**
 * {@link NetworkConfig}作为二级配置类，读取了和网络通信有关的参数设置
 *
 * @author RealDragonking
 */
public final class NetworkConfig {

    private final int udp_ioThreadNumber;
    private final int udp_execThreadNumber;
    private final int udpListenPort;
    private final int tcp_ioThreadNumber;
    private final int tcp_exeThreadNumber;
    private final String tcpBindAddress;
    private final int tcpBindPort;

    public NetworkConfig(SourceConfig srcConfig) {
        this.udp_ioThreadNumber = srcConfig.getIntValue("udp-io-thread-number", 1);
        this.udp_execThreadNumber = srcConfig.getIntValue("udp-exec-thread-number", 1);
        this.udpListenPort = srcConfig.getIntValue("udp-listen-port", 8080);

        this.tcp_ioThreadNumber = srcConfig.getIntValue("tcp-io-thread-number", 1);
        this.tcp_exeThreadNumber = srcConfig.getIntValue("tcp-exe-thread-number", 1);
        this.tcpBindAddress = srcConfig.getString("tcp-bind-ip-address");
        this.tcpBindPort = srcConfig.getIntValue("tcp-bind-port", 8081);
    }

    /**
     * 获取到本地UDP服务执行读写任务的线程数量
     *
     * @return 本地UDP服务执行读写任务的线程数量
     */
    public int getUDPioThreadNumber() {
        return this.udp_ioThreadNumber;
    }

    /**
     * 获取到本地UDP服务执行处理任务的线程数量
     *
     * @return 本地UDP服务执行处理任务的线程数量
     */
    public int getUDPexecThreadNumber() {
        return this.udp_execThreadNumber;
    }

    /**
     * 获取到本地UDP服务监听的端口号
     *
     * @return 本地UDP服务监听的端口号
     */
    public int getUDPListenPort() {
        return this.udpListenPort;
    }

    /**
     * 获取到本地TCP服务的读写线程数量
     *
     * @return 本地TCP服务的读写线程数量
     */
    public int getTCPioThreadNumber() {
        return this.tcp_ioThreadNumber;
    }

    /**
     * 获取到本地TCP服务的业务处理线程数量
     *
     * @return 本地TCP服务的业务处理线程数量
     */
    public int getTCPexecThreadNumber() {
        return this.tcp_exeThreadNumber;
    }

    /**
     * 获取到本地TCP服务绑定的ip-address
     *
     * @return 本地TCP服务绑定的ip-address
     */
    public String getTcpBindAddress() {
        return this.tcpBindAddress;
    }

    /**
     * 获取到本地TCP服务绑定的port端口号
     *
     * @return 本地TCP服务绑定的port端口号
     */
    public int getTcpBindPort() {
        return this.tcpBindPort;
    }

}
