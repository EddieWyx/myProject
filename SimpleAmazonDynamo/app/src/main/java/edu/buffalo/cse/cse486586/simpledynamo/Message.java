package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by yixiangwu on 4/29/15.
 */
public class Message implements Serializable {

    private String reqType;
    String key, value, version;
    private HashMap<String, VV> KVVMap = null;

    public Message(String reqType,String key, String value, String version,
                         HashMap<String,VV> KVVMap){
        this.reqType = reqType;
        this.key = key;
        this.value = value;
        this.version = version;
        if(KVVMap!=null){
            this.KVVMap = new HashMap<String,VV>();
            this.KVVMap.putAll(KVVMap);
        }
    }

    public String getReqType() {
        return reqType;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String getVersion() {
        return version;
    }

    public HashMap<String, VV> getKVVMap() {
        return KVVMap;
    }

    public  static class VV implements Serializable{
        String val;
        String ver;
        public VV(String val, String ver){
            this.val = val;
            this.ver = ver;
        }

        public String getVer() {
            return ver;
        }

        public String getVal() {
            return val;
        }
    }
}
