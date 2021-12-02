package com.google.ar.core.examples.java.common;

public class PoseAngle {
    private String Name;
    private int Angle;
    private Float Score;

    public void setName(String name){
        this.Name = name;
    }

    public void setAngle(int angle){
        this.Angle = angle;
    }

    public void setScore(Float data){
        this.Score = data;
    }

    public String getName(){
        return Name;
    }

    public int getAngle(){
        return Angle;
    }

    public Float getScore(){
        return Score;
    }

}
