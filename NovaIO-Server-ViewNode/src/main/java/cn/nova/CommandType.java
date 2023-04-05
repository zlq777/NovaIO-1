package cn.nova;

/**
 * {@link CommandType}枚举了一系列类型常量，指示集群节点在应用Entry数据的具体行为
 *
 * @author RealDragonking
 */
public final class CommandType {

    private CommandType() {}

    /**
     * 往一个DataNode集群的信息结构体中，加入一个新节点的{@link java.net.InetSocketAddress}
     */
    public static final int ADD_NEW_DATANODE = 0;

}
