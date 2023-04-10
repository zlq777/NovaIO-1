package cn.nova.network;

import cn.nova.config.NetworkConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;

import java.util.concurrent.ThreadFactory;

import static cn.nova.CommonUtils.getThreadFactory;

/**
 * {@link UDPService}的通用级实现，可以被允许运行在所有操作系统上，缺点是只能启用一个io线程
 *
 * @author RealDragonking
 */
public final class GeneralUDPService implements UDPService {

    private final NetworkConfig config;
    private final EventLoopGroup ioThreadGroup;
    private final EventExecutorGroup exeThreadGroup;
    private final Bootstrap bootstrap;
    private Channel channel;

    public GeneralUDPService(NetworkConfig config, MsgHandler handler) {
        this.config = config;

        ThreadFactory threadFactory;

        threadFactory = getThreadFactory("udp-io", true);
        this.ioThreadGroup = new NioEventLoopGroup(1, threadFactory);

        threadFactory = getThreadFactory("udp-exec", true);
        this.exeThreadGroup = new UnorderedThreadPoolEventExecutor(config.getUDPexecThreadNumber(), threadFactory);

        this.bootstrap = new Bootstrap()
                .group(ioThreadGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) {
                        ch.pipeline().addLast(exeThreadGroup, handler);
                    }
                });
    }

    /**
     * 初始化启动此{@link UDPService}
     *
     * @return 是否成功启动
     */
    @Override
    public boolean start() {
        try {
            int listenPort = config.getUDPListenPort();
            this.channel = bootstrap.bind(listenPort).sync().channel();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 发送{@link DatagramPacket}数据包
     *
     * @param packet 数据包
     */
    @Override
    public void send(DatagramPacket packet) {
        channel.writeAndFlush(packet);
    }

    /**
     * 关闭此{@link UDPService}
     */
    @Override
    public void close() {
        this.ioThreadGroup.shutdownGracefully();
    }

}
