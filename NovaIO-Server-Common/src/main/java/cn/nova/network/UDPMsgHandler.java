package cn.nova.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * UDP网络通信的消息处理者
 *
 * @author RealDragonking
 */
@ChannelHandler.Sharable
public final class UDPMsgHandler extends MsgHandler {

    private static final Logger LOG = LogManager.getLogger(UDPMsgHandler.class);

    public UDPMsgHandler() {}

    /**
     * 对接口服务实例进行注册扫描，从中解析出{@link MethodHandle}
     *
     * @param handleServiceEntity 处理服务实体
     */
    @Override
    public void register(Object handleServiceEntity) {
        Class<?> entityClass = handleServiceEntity.getClass();
        Method[] methods = entityClass.getMethods();
        String prefixPath = "";

        PathMapping classAnnotation = entityClass.getAnnotation(PathMapping.class);
        if (classAnnotation != null) {
            prefixPath = classAnnotation.path();
        }

        for (Method method : methods) {
            PathMapping methodAnnotation = method.getAnnotation(PathMapping.class);
            if (methodAnnotation != null) {

                Class<?>[] params = method.getParameterTypes();

                if (params.length == 1 && params[0] == DatagramPacket.class) {

                    MethodType methodType = MethodType.methodType(void.class, DatagramPacket.class);

                    try {
                        MethodHandle methodHandle = lookup.bind(handleServiceEntity, method.getName(), methodType);
                        methodHandleMap.put(prefixPath + methodAnnotation.path(), methodHandle);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                } else {
                    LOG.error("这不是一个合法的处理服务类，合法参数类型 DatagramPacket.class，合法返回类型 void.class");
                }
            }
        }
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelRead(Object)} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Subclasses may override this method to change behavior.
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param msg 接收到的数据，一般只可能是{@link DatagramPacket}
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        DatagramPacket packet = (DatagramPacket) msg;
        ByteBuf content = packet.content();

        int pathLen = content.readInt();
        CharSequence path = content.readCharSequence(pathLen, StandardCharsets.UTF_8);
        content.discardReadBytes();

        MethodHandle methodHandle = methodHandleMap.get(path);

        if (methodHandle != null) {
            try {
                methodHandle.invokeExact(packet);
            } catch (Throwable t) {
                exceptionCaught(ctx, t);
            }
        } else {
            LOG.error("无效数据，没有找到对应的处理服务");
        }
    }

}
