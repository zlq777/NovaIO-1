package cn.nova.cluster;

/**
 * 枚举了{@link RaftCore}运行过程中的几种状态
 *
 * @author RealDragonking
 */
public enum RaftState {

    LEADER,

    FOLLOWER,

    CANDIDATE

}
