package cn.nova.client;

import io.netty.buffer.ByteBuf;

/**
 * {@link AsyncFuture}定义了NovaIO服务节点计算执行的异步返回结果，可以通过添加{@link AsyncFutureListener}的方式挂载回调函数
 *
 * @author RealDragonking
 */
public interface AsyncFuture {

    /**
     * 新增一个{@link AsyncFutureListener}
     *
     * @param listener {@link AsyncFutureListener}
     */
    void addListener(AsyncFutureListener listener);

    /**
     * 通知异步执行结果
     *
     * @param response 执行结果
     */
    void notifyResponse(ByteBuf response);

}
