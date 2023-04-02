package cn.nova.async;

import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link AsyncFuture}的默认实现，使用数组作为{@link AsyncFutureListener}的底层存储结构
 *
 * @author RealDragonking
 * @param <T> 响应消息的类型
 */
@SuppressWarnings("unchecked")
public class AsyncFutureImpl<T> implements AsyncFuture<T> {

    private static final AtomicLong sessionIdFactory = new AtomicLong(0L);
    private final Class<T> resultType;
    private final long sessionId;
    private AsyncFutureListener<T>[] listeners;
    private volatile boolean hasResult;
    private volatile int pos;
    private T result;

    public AsyncFutureImpl(Class<T> resultType) {
        this.resultType = resultType;
        this.sessionId = sessionIdFactory.getAndIncrement();

        this.pos = 0;
        this.hasResult = false;
        this.listeners = new AsyncFutureListener[1];
    }

    /**
     * 获取到返回结果的类型
     *
     * @return {@link Class}
     */
    @Override
    public Class<T> getResultType() {
        return this.resultType;
    }

    /**
     * 获取到自动生成的、不重复的sessionId
     *
     * @return sessionId
     */
    @Override
    public long getSessionId() {
        return this.sessionId;
    }

    /**
     * 新增一个{@link AsyncFutureListener}
     *
     * @param listener {@link AsyncFutureListener}
     */
    @Override
    public void addListener(AsyncFutureListener<T> listener) {
        synchronized (this) {
            if (hasResult) {
                listener.onNotify(result);
            } else {
                int len = listeners.length;
                if (pos == len) {
                    AsyncFutureListener<T>[] tempBucket = new AsyncFutureListener[len << 1];
                    System.arraycopy(listeners, 0, tempBucket, 0, len);
                    listeners = tempBucket;
                }
                listeners[pos ++] = listener;
            }
        }
    }

    /**
     * 通知异步执行结果
     *
     * @param result 执行结果
     */
    @Override
    public void notifyResult(T result) {
        synchronized (this) {
            this.hasResult = true;
            this.result = result;
            for (AsyncFutureListener<T> listener : listeners) {
                listener.onNotify(result);
            }
        }
    }

}
