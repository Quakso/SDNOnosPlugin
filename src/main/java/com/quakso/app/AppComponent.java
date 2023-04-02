/*
 * Copyright 2022-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.quakso.app;

import java.nio.ByteBuffer;
import java.util.*;

import org.onlab.packet.*;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.net.*;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.*;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent implements  DelayService, LinkChangeService,UdpListenService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;
    private final Logger log = LoggerFactory.getLogger(getClass());

    private PacketProcessor processor = new DelayPacketProcessor();

    private final Short delayEtherType = (short) 0x0802;


    /**
     * 决定时延检测线程是否结束的标志
     */
    private boolean detecting = false;

    private Map<String, Map<String, String>> delayMap = new HashMap<>();

    /**
     * 监听链路改变
     */
    private final InternalLinkListener linkListener = new InternalLinkListener();

//    @Reference(cardinality = ReferenceCardinality.MANDATORY)
//    protected DeviceService deviceService;
//
//    @Reference(cardinality = ReferenceCardinality.MANDATORY)
//    protected HostService hostService;

//    private final DeviceListener deviceListener = new InternalDeviceListener();
//    private final HostListener hostListener = new InternalHostListener();
    private DelayThread curDelayThread;

    private Integer changeId = 0;
    private String changeMsg = "";
    private String linkChangeSrc="";
    private String linkChangeDst="";
    private String linkEvent="";

    private Integer udpServiceId=0;
    private String udpSrcIp="";
    private String udpDstIp="";
    private String udpSrcMac="";
    private String udpDstMac="";

    @Activate
    protected void activate() {
        log.info("Started");
        packetService.addProcessor(processor, PacketProcessor.director(1));
        packetService.requestPackets(
                //流表匹配对应etherType
                DefaultTrafficSelector.builder().matchEthType(delayEtherType).build(),
                PacketPriority.MAX,
                new DefaultApplicationId(0, "org.onosproject.core")
        );
        initMap();
        linkService.addListener(linkListener);
//        deviceService.addListener(deviceListener);
//        hostService.addListener(hostListener);
    }

    @Deactivate
    protected void deactivate() {
//        deviceService.removeListener(deviceListener);
//        hostService.removeListener(hostListener);

        linkService.removeListener(linkListener);
        packetService.removeProcessor(processor);
        packetService.cancelPackets(
                //流表匹配对应etherType
                DefaultTrafficSelector.builder().matchEthType(delayEtherType).build(),
                PacketPriority.MAX,
                new DefaultApplicationId(0, "org.onosproject.core")
        );
        //结束时延检测线程
        detecting = false;
        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        log.info("Reconfigured");
    }

//    @Override
//    public Map<String, List<String>> getDeviceAndHost() {
//        Map<String, List<String>> map = new HashMap<>();
//        Iterable<Device> devices = deviceService.getDevices();
//        for (Device device : devices) {
//            log.info(device.id().toString());
//            Set<Host> hosts = hostService.getConnectedHosts(device.id());
//            List<String> hostsList = new ArrayList<>();
//            for (Host host : hosts) {
//                log.info(host.mac().toString() + host.ipAddresses());
//                hostsList.add(host.mac().toString() + host.ipAddresses());
//            }
//            map.put(device.id().toString(), hostsList);
//        }
//        return map;
//    }

    @Override
    public Map<String,String> checkLinkChanged() {
        Map<String,String> map = new HashMap<>();
        map.put("changeId", String.valueOf(changeId));
        map.put("changeMsg",changeMsg);
        map.put("linkEvent",linkEvent);
        map.put("linkChangeSrc",linkChangeSrc);
        map.put("linkChangeDst",linkChangeDst);
        return map;
    }

    @Override
    public Map<String, String> getUdpListenMsg() {
        Map<String,String> map = new HashMap<>();
        map.put("udpServiceId", String.valueOf(udpServiceId));
        map.put("udpSrcMac",udpSrcMac);
        map.put("udpDstMac",udpDstMac);
        map.put("udpSrcIp",udpSrcIp);
        map.put("udpDstIp",udpDstIp);
        return map;
    }


//    private class InternalDeviceListener implements DeviceListener {
//        @Override
//        public void event(DeviceEvent event) {
//            DeviceEvent.Type type = event.type();
//            if (type == DeviceEvent.Type.DEVICE_ADDED || type == Type.DEVICE_REMOVED) {
//                getDeviceAndHost();
//            }
//        }
//    }

//    private class InternalHostListener implements HostListener {
//        @Override
//        public void event(HostEvent event) {
//            getDeviceAndHost();
//        }
//    }

    private class DelayPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            try {
                if (ethPkt.getEtherType() == delayEtherType) {
                    log.info("Get a delay PACKET_IN");
                    ByteBuffer buffer = ByteBuffer.wrap(ethPkt.getPayload().serialize());
                    long beginTime = buffer.getLong(0);
//                    log.info("beginTime " + beginTime);
                    long deviceId = buffer.getLong(Long.BYTES);
//                    log.info("srcDeviceId " + deviceId);
                    long srcPortNumRaw = buffer.getLong(Long.BYTES<<1);
//                    log.info("portNum " + portNum);
                    long dstPortNumRaw = buffer.getLong(Long.BYTES*3);

                    //算出时间差
                    long deltaDelay = System.currentTimeMillis() - beginTime;
                    //目的
                    DeviceId dstDeviceId = pkt.receivedFrom().deviceId();
                    //TODO:修改portNum为收到的接口
                    PortNumber dstPortNum=PortNumber.portNumber(dstPortNumRaw);
                    //源
                    DeviceId srcDeviceId = DeviceId.deviceId(DelayDetectPacket.getSrcDeviceId(deviceId));
                    PortNumber srcPortNum = PortNumber.portNumber(srcPortNumRaw);
                    //构建ConnectPoint
                    ConnectPoint srcPoint = new ConnectPoint(srcDeviceId, srcPortNum);
                    ConnectPoint dstPoint = new ConnectPoint(dstDeviceId, dstPortNum);
                    //往时延map中put值
                    log.info("Put into MAP " + srcPoint + ":{" + dstPoint + ":" + deltaDelay + "}");
                    delayMap.get(srcPoint.toString()).put(dstPoint.toString(), Long.toString(deltaDelay));
                    //阻断该包继续传播
                    context.block();
                }else if(ethPkt.getEtherType()==Ethernet.TYPE_IPV4){
                    IPv4 ipv4Packet= (IPv4) ethPkt.getPayload();
                    if( ipv4Packet.getProtocol()==IPv4.PROTOCOL_UDP){
                        //如果是检测到的特殊的UDP服务，说明可能发生了链路故障
                        //记录下源和目的的ip与mac
                        udpSrcMac=ethPkt.getSourceMAC().toString();
                        udpDstMac=ethPkt.getDestinationMAC().toString();
                        udpSrcIp=IPv4.fromIPv4Address(ipv4Packet.getSourceAddress());
                        udpDstIp=IPv4.fromIPv4Address(ipv4Packet.getDestinationAddress());
                        udpServiceId++;
                        log.info("UdpServiceId:"+udpServiceId+"UdpSrcMac:"+udpSrcMac+" UdpDstMac:"+udpDstMac+" UdpSrcIp:"+udpSrcIp+" UdpDstIp:"+udpDstIp);
                        context.send();//继续传播
                    }
                }
            } catch (Exception e) {
                log.error(e.toString());
            }
        }
    }

    private void SendDelayPacket(String tgtDeviceId, Long srcPortNum,Long dstPortNum) {
        Ethernet toSend = new Ethernet();//创建以太网帧
        DelayDetectPacket packet = new DelayDetectPacket(System.currentTimeMillis(), tgtDeviceId, dstPortNum,srcPortNum);
        toSend.setDestinationMACAddress("ff:ff:ff:ff:ff:ff");
        toSend.setSourceMACAddress("ff:ff:ff:ff:ff:ff");
        toSend.setEtherType(delayEtherType);
        toSend.setPayload(packet);
        OutboundPacket pkt = new DefaultOutboundPacket(DeviceId.deviceId(tgtDeviceId),
                DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(srcPortNum)).build(),
                ByteBuffer.wrap(toSend.serialize()));
        packetService.emit(pkt);
    }

    /**
     * 初始化时延Map
     * 会设置检测标志detecting为true
     */
    private void initMap() {
        delayMap.clear();
        delayMap=new HashMap<>();
        Iterable<Link> links = linkService.getLinks();
        for (Link li : links) {
            if (!delayMap.containsKey(li.src().toString())) {
                //如果不存在该ConnectPoint作为键
                //就需要初始化Map，再把键值对放进去
                Map<String, String> tmpMap = new HashMap<>();
                tmpMap.put(li.dst().toString(), "");
                delayMap.put(li.src().toString(), tmpMap);
            } else {
                //如果存在该ConnectPoint作为键
                //就直接将键值对put进去
                Map<String, String> tmpMap = delayMap.get(li.src().toString());
                tmpMap.put(li.dst().toString(), "");
            }
        }
    }

    private class DelayThread extends Thread {
        public DelayThread(){
        }

        @Override
        public void run() {
            log.info("Begin of a delay detecting thread "+this.getId());
            while (detecting) {
                if(this.isInterrupted()){
                    log.info("Interrupt of a delay detecting thead "+this.getId());
                    break;
                }
                try {
                    Iterable<Link> links = linkService.getLinks();
                    links.forEach(link -> {
                        log.info("send PACKET_OUT to " + link.src().deviceId().toString() + "/" + link.src().port().toString());
                        SendDelayPacket(link.src().deviceId().toString(), link.src().port().toLong(),link.dst().port().toLong());
                    });
                    //每隔10秒发送时延探测包
                    sleep(10000);
                } catch (InterruptedException e) {
                    log.info("Interrupt of a delay detecting thead "+this.getId()+" during sleep");
                    throw new RuntimeException(e);
                }
            }
            log.info("End of a delay detecting thread "+ this.getId());
        }
    }

    @Override
    public Map<String, Map<String, String>> getDelayService() {
        return delayMap;
    }

    @Override
    public Map<String,String> startDelayDetect(){
        String msg="";
        Map<String,String> map=new HashMap<>();
        if(detecting){
            log.info("Already a running delay detection thread "+curDelayThread.getId());
            msg="Already a running delay detection thread "+curDelayThread.getId();
        }else{
            initMap();
            detecting=true;
            curDelayThread = new DelayThread();
            curDelayThread.start();
            log.info("Start Delay Detection");
            msg="Start Delay Detection";
        }
        map.put("msg",msg);
        return map;
    }

    @Override
    public Map<String,String> stopDelayDetect(){
        String msg="";
        Map<String,String> map=new HashMap<>();
        detecting=false;
        //中断线程
        curDelayThread.interrupt();
        log.info("Delay Detection Stopped");
        msg="Delay Detection Stopped";
        map.put("msg",msg);
        return map;
    }

    /**
     * 发送测试的时延包
     *
     * @param tgtDeviceId
     * @param srcPortNum
     * @return
     */
    @Override
    public int sendTestPacket(String tgtDeviceId, Long srcPortNum,Long dstPortNum) {
        Ethernet toSend = new Ethernet();//创建以太网帧
        DelayDetectPacket packet = new DelayDetectPacket(System.currentTimeMillis(), tgtDeviceId, dstPortNum,srcPortNum);
        log.info("packet:" + packet);
        toSend.setDestinationMACAddress("ff:ff:ff:ff:ff:ff");
        toSend.setSourceMACAddress("ff:ff:ff:ff:ff:ff");
        toSend.setEtherType(delayEtherType);
        toSend.setPayload(packet);
        log.info("eth pkt:" + toSend);
        OutboundPacket pkt = new DefaultOutboundPacket(DeviceId.deviceId(tgtDeviceId),
                DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(srcPortNum)).build(),
                ByteBuffer.wrap(toSend.serialize()));
        log.info(Arrays.toString(toSend.serialize()));
        log.info("send pkt:" + pkt);
        packetService.emit(pkt);
        return 0;
    }

    private class InternalLinkListener implements LinkListener {
        @Override
        public void event(LinkEvent event) {
            LinkEvent.Type type = event.type();
            if (type == LinkEvent.Type.LINK_ADDED || type == LinkEvent.Type.LINK_REMOVED || type == LinkEvent.Type.LINK_UPDATED) {
                //检测到链路改变
                //停止检测，并且进行delayMap结构的刷新
                changeId++;
                switch (type) {
                    case LINK_UPDATED:
                        log.info("link update "+event);
                        changeMsg="link update";
                        linkEvent=event.toString();
                        linkChangeSrc=event.subject().src().toString();
                        linkChangeDst=event.subject().dst().toString();
                        break;
                    case LINK_ADDED:
                        log.info("link added "+event);
                        linkEvent=event.toString();
                        changeMsg="link added";
                        linkChangeSrc=event.subject().src().toString();
                        linkChangeDst=event.subject().dst().toString();
                        break;
                    case LINK_REMOVED:
                        log.info("link removed "+event);
                        changeMsg="link removed";
                        linkEvent=event.toString();
                        linkChangeSrc=event.subject().src().toString();
                        linkChangeDst=event.subject().dst().toString();
                        break;
                }
                log.info("Stop Delay Detection");
                if(detecting){
                    //如果有监测链路时延的线程，那么终止然后重启
                    detecting = false;
                    curDelayThread.interrupt();

                    startDelayDetect();
                }
            }
        }
    }
}
