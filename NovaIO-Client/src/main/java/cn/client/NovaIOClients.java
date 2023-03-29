package cn.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link NovaIOClients}作为一个工厂类，提供了创建{@link NovaIOClient}具体实现类的api
 *
 * @author RealDragonking
 */
public final class NovaIOClients {

    private NovaIOClients() {}

    /**
     * 根据给定的host和port，创建连接到目标NovaIO服务节点的{@link NovaIOClient}
     *
     * @param host host
     * @param port port
     * @return {@link NovaIOClient}
     */
    public static NovaIOClient create(String host, int port) throws Exception {
        EventLoopGroup ioThreadGroup = new NioEventLoopGroup(1);
        Map<Long, AsyncFuture<?>> waiterMap = new ConcurrentHashMap<>();

        Bootstrap bootstrap = new Bootstrap()
                .group(ioThreadGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4))
                                .addLast(new ResponseMsgHandler(waiterMap));
                    }
                });

        Channel channel = bootstrap.connect(host, port).sync().channel();

        return new NovaIOClientImpl(ioThreadGroup, channel, waiterMap);
    }

}
