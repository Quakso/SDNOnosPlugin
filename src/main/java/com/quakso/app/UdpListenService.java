package com.quakso.app;

import java.util.Map;

interface UdpListenService {
    /**
     * 获取未转发的Udp包信息
     * @return
     */
    Map<String,String> getUdpListenMsg();
}
