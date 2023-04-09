package cn.nova;

import com.github.artbits.quickio.api.KV;
import jetbrains.exodus.entitystore.PersistentEntityStore;

/**
 * {@link LocalStorageGroup}是一个中转传递类，便于返回和本地存储相关的
 * {@link com.github.artbits.quickio.api.KV}和{@link jetbrains.exodus.entitystore.PersistentEntityStore}
 *
 * @author RealDragonking
 */
public final class LocalStorageGroup {

    private final KV kvStore;
    private final PersistentEntityStore entityStore;

    public LocalStorageGroup(KV kvStore, PersistentEntityStore entityStore) {
        this.kvStore = kvStore;
        this.entityStore = entityStore;
    }

    /**
     * 获取到{@link KV}
     *
     * @return {@link KV}
     */
    public KV getKVStore() {
        return this.kvStore;
    }

    /**
     * 获取到{@link PersistentEntityStore}
     *
     * @return {@link PersistentEntityStore}
     */
    public PersistentEntityStore getEntityStore() {
        return this.entityStore;
    }

    /**
     * 关闭{@link KV}和{@link PersistentEntityStore}
     */
    public void close() {
        kvStore.close();
        entityStore.close();
    }

}
