package com.synx.app1;

import org.onlab.packet.BasePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class DelayDetectPacket extends BasePacket {

    private Long begin;
    private Long srcDeviceId;

    private Long dstPortNum;

    private Long srcPortNum;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private DelayDetectPacket() {
    }

    public DelayDetectPacket(Long begin, String srcDeviceId, Long dstPortNum, Long srcPortNum){
        this.begin=begin;
        this.srcDeviceId= Long.parseLong(srcDeviceId.substring(3),16);
        this.dstPortNum =dstPortNum;
        this.srcPortNum=srcPortNum;
    }

    @Override
    public byte[] serialize() {
        //注意<<运算符的优先级
        ByteBuffer buffer=ByteBuffer.allocate(Long.BYTES<<2);
        buffer.putLong(this.begin);
        //log.info("packet buffer begin "+buffer);
        buffer.putLong(this.srcDeviceId);
        //log.info("packet buffer srcDeviceId "+buffer);
        buffer.putLong(this.srcPortNum);
        buffer.putLong(this.dstPortNum);
        //log.info("packet buffer srcPortNum "+buffer.toString());
        //log.info(Arrays.toString(buffer.array()));
        return buffer.array();
    }

    @Override
    public int hashCode() {
        int result=1;
        result=((result<<5)-result)+((begin==null)?0:begin.hashCode());
        result=((result<<5)-result)+((srcDeviceId==null)?0:srcDeviceId.hashCode());
        result=((result<<5)-result)+((srcPortNum==null)?0:srcPortNum.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj.getClass()!=this.getClass()){
            throw new RuntimeException("can't use equals between "+this.getClass()+" and "+obj.getClass());
        }
        return this.hashCode()==obj.hashCode();
    }

    @Override
    public String toString() {
        String result="";
        result+=getSrcDeviceId();
        result+="/"+srcPortNum.toString();
        result+="/"+begin.toString();
        return result;
    }


    public Long getBeginTime(){
        return begin;
    }

    public String getSrcDeviceId(){
        String tmp=Long.toHexString(srcDeviceId);
        return "of:" + "0".repeat(16 - tmp.length()) +
                tmp;
    }

    public static String getSrcDeviceId(Long srcDeviceId){
        String tmp=Long.toHexString(srcDeviceId);
        return "of:" + "0".repeat(16 - tmp.length()) +
                tmp;
    }

    public Long getSrcPortNum(){
        return srcPortNum;
    }

}
