package cn.nova.config;

/**
 * {@link TimeConfig}作为二级配置类，读取了和时间值有关的参数设置
 *
 * @author RealDragonking
 */
public final class TimeConfig {

    private final int minElectTimeout;
    private final int maxElectTimeout;
    private final int sendMsgInterval;

    public TimeConfig(SourceConfig src) {
        this.minElectTimeout = src.getIntValue("min-elect-timeout", 400);
        this.maxElectTimeout = src.getIntValue("max-elect-timeout", 1000);
        this.sendMsgInterval = src.getIntValue("send-msg-interval", 200);
    }

    /**
     * 获取到Follower状态下心跳控制超时，及Candidate状态下选举超时的随机时间下限
     *
     * @return Follower状态下心跳控制超时，及Candidate状态下选举超时的随机时间下限
     */
    public int getMinElectTimeout() {
        return this.minElectTimeout;
    }

    /**
     * 获取到Follower状态下心跳控制超时，及Candidate状态下选举超时的随机时间上限
     *
     * @return Follower状态下心跳控制超时，及Candidate状态下选举超时的随机时间上限
     */
    public int getMaxElectTimeout() {
        return this.maxElectTimeout;
    }

    /**
     * 获取到Leader状态下主动推送消息的时间间隔
     *
     * @return Leader状态下主动推送消息的时间间隔
     */
    public int getSendMsgInterval() {
        return this.sendMsgInterval;
    }

}
