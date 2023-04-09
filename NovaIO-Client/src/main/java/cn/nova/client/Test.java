package cn.nova.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;

public class Test {

    public static void main(String[] args) throws Exception {
        Logger log = LogManager.getLogger(Test.class);

        InetSocketAddress[] addresses = new InetSocketAddress[] {
                new InetSocketAddress("127.0.0.1", 4000),
                new InetSocketAddress("127.0.0.1", 4001),
                new InetSocketAddress("127.0.0.1", 4002)
//                new InetSocketAddress("127.0.0.1", 4003),
//                new InetSocketAddress("127.0.0.1", 4004)
        };

        NovaIOClient client = NovaIOClients.create(addresses);
        Thread.sleep(15000);
        client.addNewDataNodeCluster("鸡你太美", new InetSocketAddress[] {
                new InetSocketAddress("127.0.0.1", 4010),
                new InetSocketAddress("127.0.0.1", 4011),
                new InetSocketAddress("127.0.0.1", 4012)
        }).addListener(result -> {
            if (result != null) {
                log.info(result.isSuccess());
            }
        });
        Thread.sleep(15000);
        client.removeDataNodeCluster("鸡你太美")
                .addListener(result -> {
                    if (result != null) {
                        log.info(result.isSuccess());
                    }
                });
        client.removeDataNodeCluster("happy")
                .addListener(result -> {
                    if (result != null) {
                        log.info(result.isSuccess());
                    }
                });
    }

}
