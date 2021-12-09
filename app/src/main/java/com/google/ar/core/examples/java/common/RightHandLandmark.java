package com.google.ar.core.examples.java.common;

public class RightHandLandmark {
    private int rightHandLandmark;
    private Float x;
    private Float y;
    private Float z;
    private String whichHand = "rightHand";

    public void setIndex(int i){
        this.rightHandLandmark = i;
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
        return rightHandLandmark;
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
