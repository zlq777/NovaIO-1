package cn.nova.network;

/**
 * {@link TCPService}定义了一个标准的TCP网络通信服务，提供了初始化启动、停止运行的能力
 *
 * @author RealDragonking
 */
public interface TCPService {

    /**
     * 初始化启动此{@link TCPService}
     *
     * @return 是否成功启动
     */
    boolean start();

    /**
     * 关闭此{@link TCPService}
     */
    void close();

}
