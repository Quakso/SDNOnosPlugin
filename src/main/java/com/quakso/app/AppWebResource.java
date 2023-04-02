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

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.onlab.rest.BaseResource;
import org.onosproject.rest.AbstractWebResource;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

/**
 * Sample web resource.
 */
@Path("quakso")
public class AppWebResource extends AbstractWebResource {
//    private final DeviceAndHostService deviceAndHostService=get(DeviceAndHostService.class);
//
//    /**
//     * Get Device andHost
//     *
//     * @return 200 OK
//     */
//    @GET
//    @Path("/deviceAndHost")
//    public Response getGreeting() {
//        ObjectNode node = mapper().createObjectNode();
//        ArrayNode anode=mapper().createArrayNode();
//        Map<String,List<String>> map=deviceAndHostService.getDeviceAndHost();
//        for(Map.Entry<String,List<String>> entry: map.entrySet()){
//            ArrayNode device = node.putArray(entry.getKey());
//            for(String host : entry.getValue()){
//                device.add(host);
//            }
//        }
//        return BaseResource.ok(node).build();
//    }

    private final DelayService delayService=get(DelayService.class);

    /**
     * Get delay for links
     *
     * @return 200 OK
     */
    @GET
    @Path("/delay/getMap")
    public Response getDelay() {
        ObjectNode node = mapper().createObjectNode();
        Map<String, Map<String,String>> map=delayService.getDelayService();
        if(map==null){
            return BaseResource.ok(node).build();
        }
        for(Map.Entry<String, Map<String,String>> entry : map.entrySet()){
            ObjectNode src2dst=node.putObject(entry.getKey());
            for(Map.Entry<String,String> entry1 :entry.getValue().entrySet()){
                src2dst.put(entry1.getKey(),entry1.getValue());
            }
        }
        return BaseResource.ok(node).build();
    }

    /**
     * start delay detect
     *
     * @return
     */
    @GET
    @Path("/delay/start")
    public Response startDelay(){
        ObjectNode node = mapper().createObjectNode();
        Map<String,String> map=delayService.startDelayDetect();
        for(Map.Entry<String,String> entry: map.entrySet()){
            node.put(entry.getKey(),map.get(entry.getKey()));
        }
        return BaseResource.ok(node).build();
    }

    /**
     * end delay detect
     * @return
     */
    @GET
    @Path("/delay/stop")
    public Response stopDelay(){
        ObjectNode node = mapper().createObjectNode();
        Map<String,String> map=delayService.stopDelayDetect();
        for(Map.Entry<String,String> entry: map.entrySet()){
            node.put(entry.getKey(),map.get(entry.getKey()));
        }
        return BaseResource.ok(node).build();
    }

    /**
     * Send a test delay detect packet
     * @return 200 OK
     */
    @GET
    @Path("/delay/test")
    public Response getTest(){
        delayService.sendTestPacket("of:0000000000000001",1L,3L);
        return Response.ok().build();
    }


    private final LinkChangeService linkChangeService=get(LinkChangeService.class);
    /**
     * Check If Link State Changed
     * @return
     */
    @GET
    @Path("/checkLinkChange")
    public Response checkLinkChange(){
        ObjectNode node = mapper().createObjectNode();
        Map<String,String > map= linkChangeService.checkLinkChanged();
        for(Map.Entry<String,String> entry: map.entrySet()){
            node.put(entry.getKey(),map.get(entry.getKey()));
        }
        return BaseResource.ok(node).build();
    }

    private final UdpListenService udpListenService=get(UdpListenService.class);

    /**
     * Get udp
     * @return
     */
    @GET
    @Path("/udpMsg")
    public Response getUdpListenMsg(){
        ObjectNode node = mapper().createObjectNode();
        Map<String,String> map=udpListenService.getUdpListenMsg();
        for(Map.Entry<String,String> entry: map.entrySet()){
            node.put(entry.getKey(),map.get(entry.getKey()));
        }
        return BaseResource.ok(node).build();
    }
}
