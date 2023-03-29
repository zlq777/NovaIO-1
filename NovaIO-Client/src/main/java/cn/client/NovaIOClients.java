package cn.client;

/**
 * {@link NovaIOClients}作为一个工厂类，提供了创建{@link NovaIOClient}具体实现类的api
 *
 * @author RealDragonking
 */
public final class NovaIOClients {

    private NovaIOClients() {}

    /**
     * 根据给定的host和port，创建连接到目标NovaIO服务节点的{@link NovaIOClient}
     *
     * @param host host
     * @param port port
     * @return {@link NovaIOClient}
     */
    public static NovaIOClient create(String host, int port) {
        return null;
    }

}
