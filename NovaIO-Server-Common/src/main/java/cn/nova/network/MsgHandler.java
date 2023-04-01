package cn.nova.network;

import io.netty.channel.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link MsgHandler}定义了网络通信中的消息处理者，实现了{@link ChannelInboundHandler}，
 * 能够注册处理接口服务类、找到对应的接口来处理接收到的消息
 *
 * @author RealDragonking
 */
@ChannelHandler.Sharable
public abstract class MsgHandler extends ChannelInboundHandlerAdapter {

    protected final MethodHandles.Lookup lookup;
    protected final Map<CharSequence, MethodHandle> methodHandleMap;

    public MsgHandler() {
        this.lookup = MethodHandles.publicLookup();
        this.methodHandleMap = new HashMap<>();
    }

    /**
     * 对接口服务实例进行注册扫描，从中解析出{@link MethodHandle}
     *
     * @param handleServiceEntity 处理服务实体
     */
    public abstract void register(Object handleServiceEntity);

    /**
     * Calls {@link ChannelHandlerContext#fireExceptionCaught(Throwable)} to forward
     * to the next {@link ChannelHandler} in the {@link ChannelPipeline}.
     * <p>
     * Subclasses may override this method to change behavior.
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param cause {@link Throwable}异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
    }

}
