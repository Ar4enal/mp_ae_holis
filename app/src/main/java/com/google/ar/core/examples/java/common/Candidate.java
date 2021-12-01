package com.google.ar.core.examples.java.common;

public class Candidate {
    private String candidate;
    private int sdpMLineIndex;
    private String sdpMid;

    public Candidate(){

    }
    public Candidate(String candidate, int sdpMLineIndex, String sdpMid){
        this.candidate = candidate;
        this.sdpMLineIndex = sdpMLineIndex;
        this.sdpMid = sdpMid;
    }

    public String getCandidate() {
        return candidate;
    }
    public void setCandidate(String candidate) {
        this.candidate = candidate;
    }
    public int getsdpMLineIndex() {
        return sdpMLineIndex;
    }
    public void setsdpMLineIndex(int name) {
        this.sdpMLineIndex = sdpMLineIndex;
    }
    public String getsdpMid() {
        return sdpMid;
    }
    public void setsdpMid(String sdpMid) {
        this.sdpMid = sdpMid;
    }


}
