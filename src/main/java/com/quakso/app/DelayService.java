package com.quakso.app;

import java.util.Map;

interface DelayService {
    /**
     * 获取时延检测包
     * @return
     */
    Map<String, Map<String,String>> getDelayService();

    /**
     * 开始时延检测
     */
    Map<String,String> startDelayDetect();

    /**
     * 停止时延检测
     */
    Map<String,String> stopDelayDetect();

    int sendTestPacket(String tgtDeviceId,Long dstPortNum,Long srcPortNum);
}
