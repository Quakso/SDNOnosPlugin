package com.quakso.app;

import java.util.Map;

interface LinkChangeService {
        /**
         * 获取链路changeId
         * @return
         */
        Map<String,String> checkLinkChanged();
}
