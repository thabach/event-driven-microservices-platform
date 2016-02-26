# Admin Commands

https://zookeeper.apache.org/doc/r3.1.2/zookeeperAdmin.html#sc_zkCommands

## Verify that Zookeeper is running

```
echo stat | nc 192.168.99.100 2181
```

Result
```
Zookeeper version: 3.4.6-1569965, built on 02/20/2014 09:09 GMT
Clients:
 /172.17.0.4:42600[1](queued=0,recved=215,sent=218)
 /192.168.99.1:57761[0](queued=0,recved=1,sent=0)

Latency min/avg/max: 0/0/52
Received: 225
Sent: 227
Connections: 2
Outstanding: 0
Zxid: 0x23
Mode: standalone
Node count: 24
```

```
echo mntr | nc 192.168.99.100 2181
```

Result

```
zk_version	3.4.6-1569965, built on 02/20/2014 09:09 GMT
zk_avg_latency	0
zk_max_latency	52
zk_min_latency	0
zk_packets_received	271
zk_packets_sent	273
zk_num_alive_connections	2
zk_outstanding_requests	0
zk_server_state	standalone
zk_znode_count	24
zk_watch_count	12
zk_ephemerals_count	2
zk_approximate_data_size	713
zk_open_file_descriptor_count	29
zk_max_file_descriptor_count	1048576
```

## Get Zookeeper Environment Information

```
echo envi | nc 192.168.99.100 2181
```

Result

```
Environment:
zookeeper.version=3.4.6-1569965, built on 02/20/2014 09:09 GMT
host.name=7167a11e1223
java.version=1.7.0_65
java.vendor=Oracle Corporation
java.home=/usr/lib/jvm/java-7-openjdk-amd64/jre
java.class.path=/opt/zookeeper-3.4.6/bin/../build/classes:/opt/zookeeper-3.4.6/bin/../build/lib/*.jar:/opt/zookeeper-3.4.6/bin/../lib/slf4j-log4j12-1.6.1.jar:/opt/zookeeper-3.4.6/bin/../lib/slf4j-api-1.6.1.jar:/opt/zookeeper-3.4.6/bin/../lib/netty-3.7.0.Final.jar:/opt/zookeeper-3.4.6/bin/../lib/log4j-1.2.16.jar:/opt/zookeeper-3.4.6/bin/../lib/jline-0.9.94.jar:/opt/zookeeper-3.4.6/bin/../zookeeper-3.4.6.jar:/opt/zookeeper-3.4.6/bin/../src/java/lib/*.jar:/opt/zookeeper-3.4.6/bin/../conf:
java.library.path=/usr/java/packages/lib/amd64:/usr/lib/x86_64-linux-gnu/jni:/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu:/usr/lib/jni:/lib:/usr/lib
java.io.tmpdir=/tmp
java.compiler=<NA>
os.name=Linux
os.arch=amd64
os.version=4.1.17-boot2docker
user.name=root
user.home=/root
user.dir=/opt/zookeeper-3.4.6
```
