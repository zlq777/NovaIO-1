package cn.nova.network;

import cn.nova.config.NetworkConfig;

/**
 * {@link TCPService}的特化级实现，使用了linux3.9才开始提供的epoll nio解决方案
 *
 * @author RealDragonking
 */
public class EpollTCPService implements TCPService {

    public EpollTCPService(NetworkConfig config, MsgHandler handler) {

    }

    /**
     * 初始化启动此{@link TCPService}
     *
     * @return 是否成功启动
     */
    @Override
    public boolean start() {
        return false;
    }

    /**
     * 关闭此{@link TCPService}
     */
    @Override
    public void close() {

    }

}
