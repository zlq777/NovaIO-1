package cn.nova.config;

/**
 * {@link NetworkConfig}作为二级配置类，读取了和网络通信有关的参数设置
 *
 * @author RealDragonking
 */
public final class NetworkConfig {

    private final int ioThreadNumber;
    private final int exeThreadNumber;
    private final int udpListenPort;

    public NetworkConfig(SourceConfig src) {
        int processNumber = Runtime.getRuntime().availableProcessors();
        this.ioThreadNumber = src.getIntValue("udp-io-thread-number", processNumber);
        this.exeThreadNumber = src.getIntValue("udp-exe-thread-number", processNumber);

        this.udpListenPort = src.getIntValue("udp-listen-port", 8080);
    }

    /**
     * 获取到本地UDP服务执行读写任务的线程数量
     *
     * @return 本地UDP服务执行读写任务的线程数量
     */
    public int getIoThreadNumber() {
        return this.ioThreadNumber;
    }

    /**
     * 获取到本地UDP服务执行处理任务的线程数量
     *
     * @return 本地UDP服务执行处理任务的线程数量
     */
    public int getExeThreadNumber() {
        return this.exeThreadNumber;
    }

    /**
     * 获取到本地UDP服务监听的端口号
     *
     * @return 本地UDP服务监听的端口号
     */
    public int getUDPListenPort() {
        return this.udpListenPort;
    }

}
