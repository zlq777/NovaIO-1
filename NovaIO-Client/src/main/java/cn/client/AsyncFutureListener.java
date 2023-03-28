package cn.client;

/**
 * {@link AsyncFutureListener}定义了对NovaIO服务节点计算执行的异步返回结果的监听，本质上是一个功能性的回调函数
 *
 * @author RealDragonking
 * @param <T>
 */
public interface AsyncFutureListener<T> {

    /**
     * 通知NovaIO服务节点计算执行的异步返回结果
     *
     * @param result NovaIO服务节点计算执行的异步返回结果
     */
    void onNotify(T result);

}
