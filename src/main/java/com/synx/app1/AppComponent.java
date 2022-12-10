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
import java.util.*;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent implements DeviceAndHostService, DelayService, LinkChangeService {

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

    private Integer changeId = 0;

    private String changeMsg = "";

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

    @Override
    public Integer checkLinkChanged() {
        return changeId;
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
                    for(String key : delayMap.get(srcPoint.toString()).keySet()){
                        delayMap.get(srcPoint.toString()).put(key,Long.toString(deltaDelay));
                    }
                    //阻断该包继续传播
                    context.block();
                }
            } catch (Exception e) {
                log.error(e.toString());
            }
        }
    }

    private void SendDelayPacket(String tgtDeviceId, Long srcPortNum,Long dstPortNum) {
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
            while (detecting) {
                try {
                    Iterable<Link> links = linkService.getLinks();
                    links.forEach(link -> {
                        log.info("send PACKET_OUT to " + link.src().deviceId().toString() + "/" + link.src().port().toString());
                        SendDelayPacket(link.src().deviceId().toString(), link.src().port().toLong(),link.dst().port().toLong());
                    });
                    //每隔10秒发送时延探测包
                    sleep(10000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public Map<String, Map<String, String>> getDelayService() {
        return delayMap;
    }

    @Override
    public void startDelayDetect(){
        initMap();
        detecting=true;
        DelayThread newDelayThread = new DelayThread();
        newDelayThread.start();
    }

    @Override
    public void stopDelayDetect(){
        detecting=false;
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
                log.info("links changed");
                changeId++;
                switch (type) {
                    case LINK_UPDATED:
                        log.info("link update");
                        break;
                    case LINK_ADDED:
                        log.info("link added");
                        break;
                    case LINK_REMOVED:
                        log.info("link removed");
                }
                detecting = false;
                //停止时延检测，并重新初始化map
                initMap();
            }
        }
    }


}
