package com.google.ar.core.examples.java.common;

import com.huawei.hiar.ARPose;

public class FaceKalmanLowPassFilter {
    /*
       w,x,y,z
    */
    public Float[] P = {(float) 0, (float) 0, (float) 0, (float) 0};
    public Float[] X = {(float) 0, (float) 0, (float) 0, (float) 0};
    public Float[] K = {(float) 0, (float) 0, (float) 0, (float) 0};
    public Float[] Pos3D = {(float) 0, (float) 0, (float) 0, (float) 0};
    public Float[] Now3D = {(float) 0, (float) 0, (float) 0, (float) 0};
    public Float[][] PrevPos3D = {
            {(float) 0, (float) 0, (float) 0, (float) 0},
            {(float) 0, (float) 0, (float) 0, (float) 0},
            {(float) 0, (float) 0, (float) 0, (float) 0},
            {(float) 0, (float) 0, (float) 0, (float) 0},
            {(float) 0, (float) 0, (float) 0, (float) 0},
            {(float) 0, (float) 0, (float) 0, (float) 0}
    };

    public void setNow3D(ARPose pose){
        Now3D[0] = pose.qw();
        Now3D[1] = pose.qx();
        Now3D[2] = pose.qy();
        Now3D[3] = pose.qz();
    }

    public void setPos3D(Float w, Float x, Float y, Float z){
        Pos3D[0] = w;
        Pos3D[1] = x;
        Pos3D[2] = y;
        Pos3D[3] = z;
    }

    public void setX(Float w, Float x, Float y, Float z){
        X[0] = w;
        X[1] = x;
        X[2] = y;
        X[3] = z;
    }

    public void setP(Float w, Float x, Float y, Float z){
        P[0] = w;
        P[1] = x;
        P[2] = y;
        P[3] = z;
    }

    public void setK(Float w, Float x, Float y, Float z){
        K[0] = w;
        K[1] = x;
        K[2] = y;
        K[3] = z;
    }

}
