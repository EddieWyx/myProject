package edu.buffalo.cse.cse486586.groupmessenger2;

import java.io.Serializable;

/**
 * Created by yixiangwu on 3/5/15.
 */
public class Message implements Serializable {
    private  String m;
    private int f;
    private int c;
    private int I;
    private double s;
    private int k;
    private int aNumber;

    public void setaNumber(int aNumber) {
        this.aNumber = aNumber;
    }

    public void setK(int k) {
        this.k = k;
    }

    public void setS(double s) {
        this.s = s;
    }

    public void setC(int c) {
        this.c = c;
    }

    public void setF(int f) {
        this.f = f;
    }

    public void setI(int i) {
        I = i;
    }

    public void setM(String m) {
        this.m = m;
    }

    public int getC() {
        return c;
    }

    public int getF() {
        return f;
    }

    public int getI() {
        return I;
    }

    public double getS() {
        return s;
    }

    public String getM() {
        return m;
    }

    public int getK() {
        return k;
    }

    public int getaNumber() {
        return aNumber;
    }
}
