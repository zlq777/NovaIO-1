package cn.nova.cluster;

/**
 * {@link RaftCommand}枚举了命令的几种类型
 *
 * @author RealDragonking
 */
public final class RaftCommand {

    private RaftCommand() {}

    public static final int COMMAND_APPEND = 0;
    public static final int COMMAND_DELETE = 1;

}
