package com.google.ar.core.examples.java.common;

import java.util.HashMap;

public class KalmanLowPassFilter{
    public HashMap<Integer, Filter> filterHashMap = new HashMap<>();

    public class Filter{
        public Float[] P = {(float) 0, (float) 0, (float) 0};
        public Float[] X = {(float) 0, (float) 0, (float) 0};
        public Float[] K = {(float) 0, (float) 0, (float) 0};
        public Float[] Pos3D = {(float) 0, (float) 0, (float) 0};
        public Float[] Now3D = {(float) 0, (float) 0, (float) 0};
        public Float[][] PrevPos3D = {
                {(float) 0, (float) 0, (float) 0},
                {(float) 0, (float) 0, (float) 0},
                {(float) 0, (float) 0, (float) 0},
                {(float) 0, (float) 0, (float) 0},
                {(float) 0, (float) 0, (float) 0},
                {(float) 0, (float) 0, (float) 0}
        };
    }

    public KalmanLowPassFilter(){
        init();
    }

    private void init() {
        for (int i = Constants.minBodyIndex + 1; i < Constants.maxBodyIndex; i++){
            filterHashMap.put(i, new Filter());
        }
    }

    public void setFilterHashMapNow3D(int i, Float x, Float y, Float z){
        Filter curFilter = filterHashMap.get(i);
        curFilter.Now3D[0] = x;
        curFilter.Now3D[1] = y;
        curFilter.Now3D[2] = z;
        filterHashMap.put(i, curFilter);
    }

    public void setFilterHashMapPos3D(int i, Float x, Float y, Float z){
        Filter curFilter = filterHashMap.get(i);
        curFilter.Pos3D[0] = x;
        curFilter.Pos3D[1] = y;
        curFilter.Pos3D[2] = z;
        filterHashMap.put(i, curFilter);
    }

    public void setK(int i, Float x, Float y, Float z){
        Filter curFilter = filterHashMap.get(i);
        curFilter.K[0] = x;
        curFilter.K[1] = y;
        curFilter.K[2] = z;
        filterHashMap.put(i, curFilter);
    }

    public void setP(int i, Float x, Float y, Float z){
        Filter curFilter = filterHashMap.get(i);
        curFilter.P[0] = x;
        curFilter.P[1] = y;
        curFilter.P[2] = z;
        filterHashMap.put(i, curFilter);
    }

    public void setX(int i, Float x, Float y, Float z){
        Filter curFilter = filterHashMap.get(i);
        curFilter.X[0] = x;
        curFilter.X[1] = y;
        curFilter.X[2] = z;
        filterHashMap.put(i, curFilter);
    }


}



