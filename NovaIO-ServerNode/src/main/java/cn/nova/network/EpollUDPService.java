package cn.nova.network;

import cn.nova.config.NetworkConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;

import static cn.nova.CommonUtils.getThreadFactory;

/**
 * {@link UDPService}的特化级实现，使用了linux3.9才开始提供的epoll nio解决方案。
 * 除了性能更强以外，还允许UDP服务开启多个读线程，重用端口监听
 *
 * @author RealDragonking
 */
public final class EpollUDPService implements UDPService {

    private final NetworkConfig config;
    private final EventLoopGroup ioThreadGroup;
    private final EventExecutorGroup exeThreadGroup;
    private final Bootstrap bootstrap;
    private Entry loopEntry;

    public EpollUDPService(NetworkConfig config, MsgHandler handler) {
        this.config = config;

        this.ioThreadGroup = new EpollEventLoopGroup(config.getUDPioThreadNumber(),
                getThreadFactory("udp-io", true));
        this.exeThreadGroup = new UnorderedThreadPoolEventExecutor(config.getUDPexecThreadNumber(),
                getThreadFactory("udp-exec", true));

        this.bootstrap = new Bootstrap()
                .group(ioThreadGroup)
                .option(EpollChannelOption.SO_REUSEPORT, true)
                .channel(EpollDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) {
                        ch.pipeline().addLast(exeThreadGroup, handler);
                    }
                });
    }

    /**
     * 初始化启动此{@link UDPService}。由于epoll UDP会需要我们创建多个{@link Channel}，数据发送操作操作的负载均衡
     * 也因此需要实现。为了最大化提升性能，我们选择使用完全无锁的循环链表来实现轮询模式的负载均衡
     *
     * @return 是否成功启动
     */
    @Override
    public boolean start() {
        try {
            int listenPort = config.getUDPListenPort();
            int ioThreads = config.getUDPioThreadNumber();

            Channel[] channels = new Channel[ioThreads];

            for (int i = 0; i < ioThreads; i++) {
                channels[i] = bootstrap.bind(listenPort).sync().channel();
            }

            this.loopEntry = initLoopEntry(channels);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 发送{@link DatagramPacket}数据包。由于epoll UDP会需要我们创建多个{@link Channel}，数据发送操作操作的负载均衡
     * 也因此需要实现。为了最大化提升性能，我们选择使用完全无锁的循环链表来实现轮询模式的负载均衡
     *
     * @param packet 数据包
     */
    @Override
    public void send(DatagramPacket packet) {
        Entry entry = this.loopEntry;
        Channel channel = entry.wrapChannel;
        EventLoop eventLoop = channel.eventLoop();

        eventLoop.execute(() -> channel.writeAndFlush(packet));

        this.loopEntry = entry.next;
    }

    /**
     * 关闭此{@link UDPService}
     */
    @Override
    public void close() {
        this.ioThreadGroup.shutdownGracefully();
    }

    /**
     * 初始化一个由{@link Entry}组成的环形链表
     *
     * @param channels {@link Channel}数组
     * @return {@link Entry}
     */
    private Entry initLoopEntry(Channel[] channels) {
        Entry head = new Entry(channels[0]);
        Entry node, prev = head;

        int ioThreads = config.getUDPioThreadNumber();

        for (int i = 1; i < ioThreads; i++) {
            node = new Entry(channels[i]);
            prev.next = node;
            prev = node;
        }

        prev.next = head;

        return head;
    }

    private static class Entry {
        private Entry next;
        private final Channel wrapChannel;
        private Entry(Channel wrapChannel) {
            this.wrapChannel = wrapChannel;
        }
    }

}
