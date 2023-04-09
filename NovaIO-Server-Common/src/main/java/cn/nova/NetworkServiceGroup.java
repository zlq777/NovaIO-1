package cn.nova;

import cn.nova.network.TCPService;
import cn.nova.network.UDPService;

/**
 * {@link NetworkServiceGroup}是一个中转传递类，便于返回{@link UDPService}和{@link TCPService}的具体实现类
 *
 * @author RealDragonking
 */
public final class NetworkServiceGroup {

    private final UDPService udpService;
    private final TCPService tcpService;

    public NetworkServiceGroup(UDPService udpService, TCPService tcpService) {
        this.udpService = udpService;
        this.tcpService = tcpService;
    }

    /**
     * 获取到{@link UDPService}的具体实现类
     *
     * @return {@link UDPService}
     */
    public UDPService getUdpService() {
        return this.udpService;
    }

    /**
     * 获取到{@link TCPService}的具体实现类
     *
     * @return {@link TCPService}
     */
    public TCPService getTcpService() {
        return this.tcpService;
    }

    /**
     * 关闭{@link UDPService}和{@link TCPService}
     */
    public void close() {
        udpService.close();
        tcpService.close();
    }

}
