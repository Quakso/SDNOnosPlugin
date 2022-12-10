package com.synx.app1;

import java.util.Map;

public interface DelayService {
    /**
     * 获取时延检测包
     * @return
     */
    Map<String, Map<String,String>> getDelayService();

    /**
     * 开始时延检测
     */
    void startDelayDetect();

    /**
     * 停止时延检测
     */
    void stopDelayDetect();

    int sendTestPacket(String tgtDeviceId,Long dstPortNum,Long srcPortNum);
}
