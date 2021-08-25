package com.google.ar.core.examples.java.common.converter;

import android.graphics.Bitmap;


public interface CustomFrameAvailableListner {

    public void onFrame(long timestamp, int destinationTextureId, int viewWidth, int viewHeight);
}
