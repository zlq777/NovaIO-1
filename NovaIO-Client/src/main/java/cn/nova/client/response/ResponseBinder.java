package cn.nova.client.response;

import cn.nova.async.AsyncFuture;

/**
 * {@link ResponseBinder}绑定了sessionId和{@link cn.nova.async.AsyncFuture}，是一个信息组合类
 *
 * @author RealDragonking
 */
public class ResponseBinder {

    private final AsyncFuture<?> asyncFuture;
    private final Class<?> futureType;

    public ResponseBinder(AsyncFuture<?> asyncFuture, Class<?> futureType) {
        this.asyncFuture = asyncFuture;
        this.futureType = futureType;
    }

    /**
     * 获取到和sessionId绑定的{@link AsyncFuture}
     *
     * @return {@link AsyncFuture}
     */
    public AsyncFuture<?> getAsyncFuture() {
        return this.asyncFuture;
    }

    /**
     * 获取到{@link AsyncFuture}响应的消息{@link Class}类型
     *
     * @return {@link Class}
     */
    public Class<?> getFutureType() {
        return this.futureType;
    }

}
