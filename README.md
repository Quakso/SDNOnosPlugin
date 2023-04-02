## 简介
基于ONOS编写插件，实现时延检测、链路改变检测接口

## ONOS插件详细设计与实现

### AppComponent

实现插件的主要逻辑，实现`DelayService`接口，`LinkChangeService`接口，`UdpListenService`接口。

主要包含成员变量如下表

| 名称           | 类型                            | 含义                                                         |
| -------------- | ------------------------------- | ------------------------------------------------------------ |
| linkService    | LinkService                     | onos提供的链路相关服务，通过该变量获取所有链路信息，设置listener链路改变监听 |
| packetService  | PacketService                   | onos提供的数据包处理服务，通过该变量设置Processor处理包      |
| log            | Logger                          | 进行日志打印                                                 |
| processor      | DelayPacketProcesser            | 进行时延包处理                                               |
| delayEtherType | Short                           | 发出的时延探测包的以太网类型0x0802                           |
| detecting      | boolean                         | 决定时延检测线程是否结束                                     |
| delayMap       | Map <String,Map<String,String>> | 存放时延检测结果的HashMap                                    |
| linkListener   | InternalLinkListenr             | 链路改变监听                                                 |
| curDelayThread | DelayThread                     | 时延检测线程                                                 |
| changeId       | Interger                        | 链路改变Id，检测到链路改变会修改这个Id                       |
| changeMsg      | String                          | 链路改变信息                                                 |
| linkChangeSrc  | String                          | 链路改变的源节点信息                                         |
| linkChangeDst  | String                          | 链路改变的目的节点信息                                       |
| linkEvent      | String                          | 链路改变的事件                                               |
| udpServiceId   | Interger                        | udp服务的Id，检测到未正常转发的udp包会修改这个Id             |
| udpSrcIp       | String                          | 未正常转发的udp包的源IP                                      |
| udpDstIp       | String                          | 未正常转发的udp包的目的IP                                    |
|                |                                 |                                                              |

成员方法如下表

| 方法名           | 参数                                             | 返回值类型                     | 含义                                                         |
| ---------------- | ------------------------------------------------ | ------------------------------ | ------------------------------------------------------------ |
| activate         | -                                                | -                              | 启动插件执行的操作，添加DelayPacketProcessor，初始化时延HashMap，添加链路监听 |
| deactivate       | -                                                | -                              | 关停插件执行的操作，移除链路监听，取消DelayPacketProcessor，设置时延检测线程结束 |
| checkLinkChange  | -                                                | Map<String,String>             | 实现LinkChangeService接口的方法，返回链路改变信息            |
| getUdpListenMsg  | -                                                | Map<String,String>             | 实现UdpListenService接口方法，返回最新的未转发的udp包的信息  |
| SendDelayPacket  | String tgtDeviceIdLong srcPortNumLong dstPortNum | -                              | 根据指定的目的交换机Id和源端口号，目的端口号发送时延探测包   |
| initMap          | -                                                | -                              | 初始化存放时延信息的Map，因为不同的链路结构会对应不同的Map所以当链路发生改变时需要及时地进行修改 |
| getDelayService  | -                                                | Map<String,Map<String,String>> | 实现DelayService接口的方法，直接返回存放时延信息地HashMap    |
| startDelayDetect | -                                                | Map<String,String>             | 实现DelayService接口的方法，如果当前有检测线程正在检测，就进返回已有检测线程，若无，就开启新的时延检测线程 |
| stopDelayDetect  | -                                                | Map<String,String>             | 实现DelayService接口的方法，设置线程检测标志为false，从而停止时延检测 |
|                  |                                                  |                                |                                                              |

内部类如下表

| 类名                 | 含义                                                         |
| -------------------- | ------------------------------------------------------------ |
| DelayPacketProcessor | 时延的包处理器，实现PacketProcessor接口，重写process方法，捕获包后解析看其以太网类型是否为delayEtherType，同时解析除其发包时间beginTime，源交换机Id，源端口号，目的端口号，根据当前时间计算出时间差作为时延估值，并将时延信息放入delayMap中。如果捕获到的是IPV4类型的UDP包，那么视为未正常转发的UDP包，纪录下该包信息。 |
| DelayThread          | 时延检测线程类，每隔10s向各个连接发送时延检测包，设置连接的目的节点作为时延探测包的目的 |
| InternalLinkListener | 链路改变监听类，检测到链路改变，修改changeId，记录改变信息，终止链路检测线程进行重启 |

### AppWebResource

定义暴露的Http接口，如下图

![img](https://uestc.feishu.cn/space/api/box/stream/download/asynccode/?code=OWUyNmE4MTU2ZDIwMjFhZDJlMDYyMWQxYzdlMDEyMjRfcTN4UUFmWWVkZmd6czFiaEpzV0tWZjhtdXBlT2RwUVNfVG9rZW46Ym94Y25qQjRTNTQzRFF4a1FLdzJnSjAzYlRlXzE2ODA0Mzc1Mjc6MTY4MDQ0MTEyN19WNA)

暴露的Http接口示意图

### DelayService

时延服务接口，需要实现

```Python
/**
 * 获取时延检测包
 * @return
 /
Map<String, Map<String,String>> getDelayService();

/*
 * 开始时延检测
 /
Map<String,String> startDelayDetect();

/*
 * 停止时延检测
 */
Map<String,String> stopDelayDetect();
```

### LinkChangeService

链路改变服务接口，需要实现

```Python
/**
 * 获取链路changeId
 * @return
 */
Map<String,String> checkLinkChanged();
```

### UdpListenService

未转发udp包检测服务，需要实现

```Python
/**
 * 获取未转发的Udp包信息
 * @return
 */
Map<String,String> getUdpListenMsg();
```

### DelayDetectPacket

![img](https://uestc.feishu.cn/space/api/box/stream/download/asynccode/?code=NTlmYzlhYWJhNTUwNWM3YTNkMjBkMDQ1MTg3MTkyNTdfYTUzQ2NMSmRhUTkzazFVcUYyaTRzOWxuNE5hZnJ6dW5fVG9rZW46Ym94Y25paEJCUTlvY1gwTUJpMDF0S3gzMnRmXzE2ODA0Mzc3Mjc6MTY4MDQ0MTMyN19WNA)

发送接收实验探测包图

时延检测数据包类，继承BasePacket。

成员变量如下表

| 名称        | 类型 | 含义             |
| ----------- | ---- | ---------------- |
| begin       | Long | 时延包发出时间   |
| srcDeviceId | Long | 源交换机ID       |
| dstPortNum  | Long | 目的交换机端口号 |
| srcPortNum  | Long | 源交换机端口号   |

主要成员方法如下表

| 方法名         | 参数             | 返回类型 | 含义                             |
| -------------- | ---------------- | -------- | -------------------------------- |
| serialize      | -                | byte[]   | 序列化，将各成员变量放入字节数组 |
| hashCode       | -                | int      | -                                |
| equals         | -                | boolean  | -                                |
| toString       | -                | String   | 以字符串的形式显示该包           |
| getBeginTime   | -                | Long     | 获取开始发包时间                 |
| getSrcDeviceId | -                | String   | 获取源交换机Id的字符串表示       |
| getSrcDeviceId | Long srcDeviceId | String   | 重载，静态方法，同上             |
| getSrcPorNum   | -                | Long     | 获取源交换机端口号               |