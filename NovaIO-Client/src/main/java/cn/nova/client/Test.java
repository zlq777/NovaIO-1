package cn.nova.client;

import java.net.InetSocketAddress;

public class Test {

    public static void main(String[] args) {
        InetSocketAddress[] addresses = new InetSocketAddress[] {
                new InetSocketAddress("127.0.0.1", 4000),
                new InetSocketAddress("127.0.0.1", 4001),
                new InetSocketAddress("127.0.0.1", 4002)
//                new InetSocketAddress("127.0.0.1", 4003),
//                new InetSocketAddress("127.0.0.1", 4004)
        };

        NovaIOClient client = NovaIOClients.create(addresses);
        client.addNewDataNodeCluster("鸡你太美", new InetSocketAddress[]{
                new InetSocketAddress("127.0.0.1", 4010),
                new InetSocketAddress("127.0.0.1", 4011),
                new InetSocketAddress("127.0.0.1", 4012)
        });
    }

}
