package cn.nova;

/**
 * {@link AsyncFuture}定义了本地或是远程计算执行的异步返回结果，可以通过添加{@link AsyncFutureListener}的方式挂载回调函数
 *
 * @author RealDragonking
 * @param <T> 响应消息的类型
 */
public interface AsyncFuture<T> {

    /**
     * 使用给定的{@link Class}类型，创建一个{@link AsyncFuture}
     *
     * @param typeClass {@link Class}类型
     * @return {@link AsyncFuture}
     * @param <T> {@link AsyncFuture}的类型
     */
    static <T> AsyncFuture<T> of(Class<T> typeClass) {
        return new AsyncFutureImpl<>(typeClass);
    }

    /**
     * 获取到返回结果的类型
     *
     * @return {@link Class}
     */
    Class<T> getResultType();

    /**
     * 获取到自动生成的、不重复的sessionId
     *
     * @return sessionId
     */
    long getSessionId();

    /**
     * 新增一个{@link AsyncFutureListener}
     *
     * @param listener {@link AsyncFutureListener}
     */
    void addListener(AsyncFutureListener<T> listener);

    /**
     * 通知异步执行结果
     *
     * @param result 执行结果
     */
    void notifyResult(T result);

}
