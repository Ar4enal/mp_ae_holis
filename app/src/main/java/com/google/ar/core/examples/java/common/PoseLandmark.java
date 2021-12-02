package com.google.ar.core.examples.java.common;

public class PoseLandmark {
    private int poseLandmark;
    private Float score;
    private Float x;
    private Float y;
    private Float z;

    public void setIndex(int i){
        this.poseLandmark = i;
    }

    public void setScore(Float data){
        this.score = data;
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
        return poseLandmark;
    }

    public Float getScore(){
        return score;
    }

    public Float getX() { return x; }
    public Float getY(){
        return y;
    }
    public Float getZ(){
        return z;
    }
}
