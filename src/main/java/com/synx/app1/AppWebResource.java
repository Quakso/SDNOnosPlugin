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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.onlab.rest.BaseResource;
import org.onosproject.rest.AbstractWebResource;

import java.security.cert.CertPathBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

/**
 * Sample web resource.
 */
@Path("sample")
public class AppWebResource extends AbstractWebResource {
    private final DeviceAndHostService deviceAndHostService=get(DeviceAndHostService.class);

    /**
     * Get Device andHost
     *
     * @return 200 OK
     */
    @GET
    @Path("")
    public Response getGreeting() {
        ObjectNode node = mapper().createObjectNode();
        ArrayNode anode=mapper().createArrayNode();
        Map<String,List<String>> map=deviceAndHostService.getDeviceAndHost();
        for(Map.Entry<String,List<String>> entry: map.entrySet()){
            ArrayNode device = node.putArray(entry.getKey());
            for(String host : entry.getValue()){
                device.add(host);
            }
        }
        return BaseResource.ok(node).build();
    }

    private final DelayService delayService=get(DelayService.class);

    /**
     * Get delay for links
     *
     * @return 200 OK
     */
    @GET
    @Path("/delay/get")
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
        delayService.startDelayDetect();
        return Response.ok().build();
    }

    /**
     * end delay detect
     * @return
     */
    @GET
    @Path("/delay/stop")
    public Response stopDelay(){
        delayService.stopDelayDetect();
        return Response.ok().build();
    }

    /**
     * Send a test packet with vlan id 4094
     *
     * @return 200 OK
     */
    @GET
    @Path("/testDelay")
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
        Integer changedId= linkChangeService.checkLinkChanged();
        node.put("changeId",changedId);
        return BaseResource.ok(node).build();
    }

}
