package com.synx.app1;

import java.util.Map;

public interface DelayService {
    Map<String, Map<String,String>> getDelayService();
    int sendTestPacket(String tgtDeviceId,Long portNum);
}
