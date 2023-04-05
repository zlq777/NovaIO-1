package cn.nova.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;

public class Test {

    public static void main(String[] args) {
        Logger log = LogManager.getLogger(Test.class);

        InetSocketAddress[] addresses = new InetSocketAddress[] {
                new InetSocketAddress("127.0.0.1", 4000),
                new InetSocketAddress("127.0.0.1", 4001),
                new InetSocketAddress("127.0.0.1", 4002),
                new InetSocketAddress("127.0.0.1", 4003),
                new InetSocketAddress("127.0.0.1", 4004)
        };

        NovaIOClient client = NovaIOClients.create(addresses);
        client.addNewDataNode("niubi", new InetSocketAddress("127.0.0.1", 4010))
                .addListener(f -> {
                    if (f != null) {
                        log.info(f.isSuccess());
                    }
                });
    }
}
