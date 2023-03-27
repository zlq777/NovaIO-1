package cn.nova.network;

import io.netty.channel.socket.DatagramPacket;

/**
 * {@link UDPService}定义了一个标准的UDP网络通信服务，提供了初始化启动、数据发送的能力
 *
 * @author RealDragonking
 */
public interface UDPService {

    /**
     * 初始化启动此{@link UDPService}
     *
     * @param handler 消息处理器
     * @return 是否成功启动
     */
    boolean start(MsgHandler handler);

    /**
     * 发送{@link DatagramPacket}数据包
     *
     * @param packet 数据包
     */
    void send(DatagramPacket packet);

    /**
     * 关闭此{@link UDPService}
     */
    void close();

}
