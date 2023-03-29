package cn.nova.client;

/**
 * {@link AsyncFutureListener}定义了对NovaIO服务节点计算执行的异步返回结果的监听，本质上是一个功能性的回调函数
 *
 * @author RealDragonking
 * @param <T> 响应消息的类型
 */
public interface AsyncFutureListener<T> {

    /**
     * 通知NovaIO服务节点计算执行的异步返回结果
     *
     * @param response NovaIO服务节点计算执行的异步返回结果
     */
    void onNotify(T response);

}
