package cn.nova.network;

import cn.nova.config.NetworkConfig;

/**
 * {@link TCPService}的通用级实现，可以被允许运行在所有操作系统上
 *
 * @author RealDragonking
 */
public final class GeneralTCPService implements TCPService {

    public GeneralTCPService(NetworkConfig config, MsgHandler handler) {

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
