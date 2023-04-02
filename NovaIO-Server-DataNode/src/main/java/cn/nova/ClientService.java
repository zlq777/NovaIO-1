package cn.nova;

import cn.nova.cluster.RaftCore;

/**
 * {@link ClientService}负责提供面向客户端、与Entry数据读写相关的TCP服务接口
 *
 * @author RealDragonking
 */
public final class ClientService {

    private final RaftCore raftCore;

    public ClientService(RaftCore raftCore) {
        this.raftCore = raftCore;
    }

}
