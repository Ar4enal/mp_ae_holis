package com.google.ar.core.examples.java.common;

public class LeftHandLandmark {
    private int leftHandLandmark;
    private Float x;
    private Float y;
    private Float z;
    private String whichHand = "leftHand";

    public void setIndex(int i){
        this.leftHandLandmark = i;
    }

    public void setX(Float data){
        this.x = data;
    }

    public void setY(Float data){
        this.y = data;
    }

    public void setZ(Float data){
        this.z = data;
    }

    public int getIndex(){
        return leftHandLandmark;
    }

    public Float getX() { return x; }
    public Float getY(){
        return y;
    }
    public Float getZ(){
        return z;
    }
    public String getWhichHand(){ return whichHand;}
}
