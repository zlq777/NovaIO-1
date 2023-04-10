package cn.nova;

/**
 * {@link CommandType}枚举了一系列类型常量，指示集群节点在应用Entry数据的具体行为
 *
 * @author RealDragonking
 */
public final class CommandType {

    private CommandType() {}

    public static final int ADD_DATANODE_CLUSTER = 0;
    public static final int REMOVE_DATANODE_CLUSTER = 1;

}
