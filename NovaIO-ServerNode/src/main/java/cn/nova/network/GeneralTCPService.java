package cn.nova.network;

import cn.nova.config.NetworkConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;

import static cn.nova.CommonUtils.getThreadFactory;

/**
 * {@link TCPService}的通用级实现，可以被允许运行在所有操作系统上
 *
 * @author RealDragonking
 */
public final class GeneralTCPService implements TCPService {

    private final NetworkConfig config;
    private final EventLoopGroup acceptThreadGroup;
    private final EventLoopGroup ioThreadGroup;
    private final EventExecutorGroup exeThreadGroup;
    private final ServerBootstrap bootstrap;

    public GeneralTCPService(NetworkConfig config, MsgHandler handler) {
        this.config = config;

        this.acceptThreadGroup = new NioEventLoopGroup(1);
        this.ioThreadGroup = new NioEventLoopGroup(config.getTCPioThreadNumber(),
                getThreadFactory("tcp-io", true));
        this.exeThreadGroup = new UnorderedThreadPoolEventExecutor(config.getTCPexecThreadNumber(),
                getThreadFactory("tcp-exec", true));

        this.bootstrap = new ServerBootstrap()
                .group(acceptThreadGroup, ioThreadGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4))
                                .addLast(exeThreadGroup, handler);
                    }
                });
    }

    /**
     * 初始化启动此{@link TCPService}
     *
     * @return 是否成功启动
     */
    @Override
    public boolean start() {
        try {
            bootstrap.bind(config.getTcpBindAddress(), config.getTcpBindPort()).sync();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 关闭此{@link TCPService}
     */
    @Override
    public void close() {
        acceptThreadGroup.shutdownGracefully();
        ioThreadGroup.shutdownGracefully();
        exeThreadGroup.shutdownGracefully();
    }

}
