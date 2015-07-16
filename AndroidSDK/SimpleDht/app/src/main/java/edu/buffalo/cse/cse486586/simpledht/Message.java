package edu.buffalo.cse.cse486586.simpledht;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by yixiangwu on 3/31/15.
 */
public class Message implements Serializable {
    private String request;
    private String origin_avd;
    private String destination_acd;
    private String suc;
    private String pre;
    private String key;
    private String value;
    private HashMap<String,String> qbHash;

    public void setKey(String key) {
        this.key = key;
    }

    public void setQbHash(HashMap<String, String> qbHash) {
        this.qbHash = new HashMap<String,String>();
        this.qbHash.putAll(qbHash);
    }

    public void setDestination_acd(String destination_acd) {
        this.destination_acd = destination_acd;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setPre(String pre) {
        this.pre = pre;
    }

    public void setSuc(String suc) {
        this.suc = suc;
    }

    public void setOrigin_avd(String origin_avd) {
        this.origin_avd = origin_avd;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public String getDestination_acd() {
        return destination_acd;
    }

    public String getOrigin_avd() {
        return origin_avd;
    }

    public String getRequest() {
        return request;
    }

    public HashMap<String, String> getQbHash() {
        return qbHash;
    }

    public String getPre() {
        return pre;
    }

    public String getSuc() {
        return suc;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
