package cn.nova.client.result;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

/**
 * {@link QueryDataNodeInfoResult}对应于获取DataNode集群信息的请求，封装了一个{@link Map}
 *
 * @author RealDragonking
 */
public class QueryDataNodeInfoResult {

    private final Map<String, Set<InetSocketAddress>> map;

    public QueryDataNodeInfoResult(Map<String, Set<InetSocketAddress>> map) {
        this.map = map;
    }

    /**
     * 获取到记录有DataNode集群信息的{@link Map}
     *
     * @return 记录有DataNode集群信息的{@link Map}
     */
    public Map<String, Set<InetSocketAddress>> getDataNodeInfoMap() {
        return this.map;
    }

}
