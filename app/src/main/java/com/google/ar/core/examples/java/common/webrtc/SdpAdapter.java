package com.google.ar.core.examples.java.common.webrtc;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class SdpAdapter implements SdpObserver {


    private final String tag;

    public SdpAdapter(String tag) {
        this.tag = "WebRTC Sdp " + tag;
    }

    private void log(String s) {
        Log.d(tag, s);
    }

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        log("onCreateSuccess " + sessionDescription);
    }

    @Override
    public void onSetSuccess() {
        log("onSetSuccess ");
    }

    @Override
    public void onCreateFailure(String s) {
        log("onCreateFailure " + s);
    }

    @Override
    public void onSetFailure(String s) {
        log("onSetFailure " + s);
    }
}
