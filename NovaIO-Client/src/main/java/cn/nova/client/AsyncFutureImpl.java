package cn.nova.client;

/**
 * {@link AsyncFuture}的默认实现，使用数组作为{@link AsyncFutureListener}的底层存储结构
 *
 * @author RealDragonking
 * @param <T> 响应消息的类型
 */
public class AsyncFutureImpl<T> implements AsyncFuture<T> {

    private volatile int pos;
    private AsyncFutureListener<T>[] listeners;
    private T result;

    public AsyncFutureImpl() {
        this.pos = 0;
        this.listeners = new AsyncFutureListener[1];
    }

    /**
     * 新增一个{@link AsyncFutureListener}
     *
     * @param listener {@link AsyncFutureListener}
     */
    @Override
    public void addListener(AsyncFutureListener<T> listener) {
        synchronized (this) {
            if (result == null) {
                int len = listeners.length;
                if (pos == len) {
                    AsyncFutureListener<T>[] tempBucket = new AsyncFutureListener[len << 1];
                    System.arraycopy(listeners, 0, tempBucket, 0, len);
                    listeners = tempBucket;
                }
                listeners[pos ++] = listener;
            } else {
                listener.onNotify(result);
            }
        }
    }

    /**
     * 通知异步执行结果
     *
     * @param result 执行结果
     */
    @Override
    public void notifyResponse(T result) {
        synchronized (this) {
            this.result = result;
            for (AsyncFutureListener<T> listener : listeners) {
                listener.onNotify(result);
            }
        }
    }

}
