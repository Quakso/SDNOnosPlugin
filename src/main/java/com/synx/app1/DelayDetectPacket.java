package com.synx.app1;

import org.onlab.packet.BasePacket;
import java.nio.ByteBuffer;

public class DelayDetectPacket extends BasePacket {

    private Long begin;
    private Integer srcDeviceId;

    private Long srcPortNum;

    private DelayDetectPacket() {
    }

    public DelayDetectPacket(Long begin, String srcDeviceId,Long srcPortNum){
        this.begin=begin;
        this.srcDeviceId= Integer.parseInt(srcDeviceId.substring(3),16);
        this.srcPortNum=srcPortNum;
    }

    @Override
    public byte[] serialize() {
        ByteBuffer buffer=ByteBuffer.allocate(Long.BYTES<<1+ Integer.BYTES);
        buffer.putLong(0,begin);
        buffer.putInt(srcDeviceId);
        buffer.putLong(srcPortNum);
        return buffer.array();
    }

    @Override
    public int hashCode() {
        int result=1;
        result=(result<<5-result)+((begin==null)?0:begin.hashCode());
        result=(result<<5-result)+((srcDeviceId==null)?0:srcDeviceId.hashCode());
        result=(result<<5-result)+((srcPortNum==null)?0:srcPortNum.hashCode());
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
        String tmp=Integer.toHexString(srcDeviceId);
        return "of:" + "0".repeat(16 - tmp.length()) +
                tmp;
    }

    public Long getSrcPortNum(){
        return srcPortNum;
    }

}
