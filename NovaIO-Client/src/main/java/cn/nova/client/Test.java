package cn.nova.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;

public class Test {

    private static final Logger log = LogManager.getLogger(Test.class);

    public static void main(String[] args) {

        InetSocketAddress[] addresses = new InetSocketAddress[] {
                new InetSocketAddress("127.0.0.1", 4000),
                new InetSocketAddress("127.0.0.1", 4001),
                new InetSocketAddress("127.0.0.1", 4002),
                new InetSocketAddress("127.0.0.1", 4003),
                new InetSocketAddress("127.0.0.1", 4004)
        };

        try {
            NovaIOClient client = NovaIOClients.create(addresses);
            client.readEntry(-1);
        } catch (Exception e) {
            log.info(e);
        }
    }
}
