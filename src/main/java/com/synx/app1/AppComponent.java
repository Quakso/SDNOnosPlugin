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
package com.synx.app1;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.onlab.packet.*;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.DeviceEvent.Type;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.packet.*;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.dmt.MetaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent implements DeviceAndHostService, DelayService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;
    private final Logger log = LoggerFactory.getLogger(getClass());

    private PacketProcessor processor = new DelayPacketProcessor();

    private final Short delayEtherType = (short) 0x0802;

    private final Short delayVlanId = 4094;

    /**
     * 决定时延检测线程是否结束
     */
    private boolean detecting = true;

    private Map<String, Map<String, String>> delayMap = new HashMap<>();

    /**
     * 监听链路改变
     */
    private final InternalLinkListener linkListener = new InternalLinkListener();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    private final DeviceListener deviceListener = new InternalDeviceListener();
    private final HostListener hostListener = new InternalHostListener();

    @Activate
    protected void activate() {
        log.info("Started");
        packetService.addProcessor(processor, PacketProcessor.director(1));
        packetService.requestPackets(
                //流表匹配对应vlanId
                DefaultTrafficSelector.builder().matchVlanId(VlanId.vlanId(delayVlanId)).build(),
                PacketPriority.MAX,
                new DefaultApplicationId(0, "org.onosproject.core")
        );
        log.info("packetService:" + packetService.getRequests());
        initMap();
        linkService.addListener(linkListener);

        deviceService.addListener(deviceListener);
        hostService.addListener(hostListener);
    }

    @Deactivate
    protected void deactivate() {
        deviceService.removeListener(deviceListener);
        hostService.removeListener(hostListener);

        linkService.removeListener(linkListener);
        packetService.removeProcessor(processor);
        packetService.cancelPackets(
                //流表匹配对应vlanId
                DefaultTrafficSelector.builder().matchVlanId(VlanId.vlanId(delayVlanId)).build(),
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

    @Override
    public Map<String, List<String>> getDeviceAndHost() {
        Map<String, List<String>> map = new HashMap<>();
        Iterable<Device> devices = deviceService.getDevices();
        for (Device device : devices) {
            log.info(device.id().toString());
            Set<Host> hosts = hostService.getConnectedHosts(device.id());
            List<String> hostsList = new ArrayList<>();
            for (Host host : hosts) {
                log.info(host.mac().toString() + host.ipAddresses());
                hostsList.add(host.mac().toString() + host.ipAddresses());
            }
            map.put(device.id().toString(), hostsList);
        }
        return map;
    }

    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceEvent.Type type = event.type();
            if (type == DeviceEvent.Type.DEVICE_ADDED || type == Type.DEVICE_REMOVED) {
                getDeviceAndHost();
            }
        }
    }

    private class InternalHostListener implements HostListener {
        @Override
        public void event(HostEvent event) {
            getDeviceAndHost();
        }
    }

    private class DelayPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            try {
                if (ethPkt.getVlanID() == delayVlanId) {
                    log.info("Get a delay PACKET_IN");
                    //如果收到包的vlanId是时延探测包的vlanId才进行处理
                    //按照设计的PDU进行解析
//                    if (ethPkt.getEtherType() == delayEtherType) {
//                        log.info("Get a delay PACKET_IN IPv4");
//                        IPv4 iPv4 = (IPv4) ethPkt.getPayload();
//                        if (iPv4.getProtocol() == IPv4.PROTOCOL_UDP) {
//                            log.info("Get a delay PACKET_IN UDP");
//                            UDP udp = (UDP) iPv4.getPayload();
//                            IPacket packet =udp.getPayload();
//                            log.info(packet.toString());
                    DelayDetectPacket packet=(DelayDetectPacket) ethPkt.getPayload();
                    //算出时间差
                    long deltaDelay = System.currentTimeMillis() - packet.getBeginTime();
                    //目的
                    DeviceId dstDeviceId = pkt.receivedFrom().deviceId();
                    //TODO:修改portNum为收到的接口
                    PortNumber dstPortNum = pkt.receivedFrom().port();
                    //源
                    DeviceId srcDeviceId = DeviceId.deviceId(packet.getSrcDeviceId());
                    PortNumber srcPortNum = PortNumber.portNumber(packet.getSrcPortNum());
                    //构建ConnectPoint
                    ConnectPoint srcPoint = new ConnectPoint(srcDeviceId, srcPortNum);
                    ConnectPoint dstPoint = new ConnectPoint(dstDeviceId, dstPortNum);
                    //往时延map中put值
                    log.info("Put into MAP " + srcPoint + ":{" + dstPoint + ":" + deltaDelay + "}");
                    delayMap.get(srcPoint.toString()).put(dstPoint.toString(), Long.toString(deltaDelay));
                    //阻断该包继续传播
                    context.block();
                }


            } catch (Exception e) {
                log.error(e.toString());
            }
        }
    }

    private void SendDelayPackets(DeviceId srcDevId, PortNumber outputPortNum) {
        Ethernet toSend = new Ethernet();//创建以太网帧
        DelayDetectPacket packet = new DelayDetectPacket(System.currentTimeMillis(), srcDevId.toString(), outputPortNum.toLong());
        log.info("packet:" + packet);
        toSend.setDestinationMACAddress("99:99:99:99:99:99");
        toSend.setSourceMACAddress("66:66:66:66:66:66");
        toSend.setEtherType(delayEtherType);
        IPv4 iPv4 = new IPv4();
        iPv4.setPayload(packet);
        toSend.setPayload(iPv4);
        log.info("eth pkt:" + toSend);
        OutboundPacket pkt = new DefaultOutboundPacket(srcDevId,
                DefaultTrafficTreatment.builder().setOutput(outputPortNum).build(),
                ByteBuffer.wrap(toSend.serialize()));
        log.info("send pkt:" + pkt);
        packetService.emit(pkt);
    }

    /**
     * 初始化时延Map
     * 会设置检测标志detecting为true
     */
    private void initMap() {
        delayMap.clear();
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
        public DelayThread() {
        }

        @Override
        public void run() {
//            while (detecting) {
//                try {
            Iterable<Link> links = linkService.getLinks();
            links.forEach(link -> {
                log.info("send PACKET_OUT to " + link.src().deviceId().toString() + "/" + link.src().port().toString());
                SendDelayPackets(link.src().deviceId(), link.src().port());
            });
//                    //每隔10秒发送时延探测包
//                    sleep(10000);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
        }
    }

    @Override
    public Map<String, Map<String, String>> getDelayService() {
        if (detecting) {
            //正在时延检测
            detecting = false;
            //停止时延检测
            return delayMap;
        } else {
            //不在时延检测，返回null,并开始检测
            initMap();
            detecting = true;
            DelayThread newDelayThread = new DelayThread();
            newDelayThread.start();
            return null;
        }
    }

    @Override
    public int sendTestPacket(String tgtDeviceId, Long portNum) {
        Ethernet toSend = new Ethernet();//创建以太网帧
        DelayDetectPacket packet = new DelayDetectPacket(System.currentTimeMillis(), tgtDeviceId, portNum);
        log.info("packet:" + packet);
        toSend.setDestinationMACAddress("99:99:99:99:99:99");
        toSend.setSourceMACAddress("66:66:66:66:66:66");
        toSend.setVlanID(delayVlanId);//设置特殊的VlanID加以区分
        toSend.setEtherType(delayEtherType);
        toSend.setPayload(packet);
        log.info("eth pkt:" + toSend);
        OutboundPacket pkt = new DefaultOutboundPacket(DeviceId.deviceId(tgtDeviceId),
                DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(portNum)).build(),
                ByteBuffer.wrap(toSend.serialize()));
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
                detecting = false;
                initMap();
                //表示可以进行时延检测了
                detecting = true;
            }
        }
    }
}
