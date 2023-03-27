package cn.nova.cluster;

import io.netty.buffer.ByteBuf;

/**
 * {@link EntrySyncTask}定义了raft算法运转过程中需要执行的Entry条目数据同步任务，这个任务是一个异步回调函数
 *
 * @author RealDragonking
 */
public interface EntrySyncTask {

    /**
     * 获取到写入了需要同步的Entry数据的{@link ByteBuf}字节缓冲区
     *
     * @return {@link ByteBuf}
     */
    ByteBuf entryData();

    /**
     * 异步执行同步完成后的结果。如果这个节点不是leader节点，会立即返回失败结果
     *
     * @param entryIndex 同步操作的序列号，如果失败的话会返回-1
     */
    void onSyncFinish(long entryIndex);

}
