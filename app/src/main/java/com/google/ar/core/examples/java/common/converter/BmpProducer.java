package com.google.ar.core.examples.java.common.converter;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;


public class BmpProducer extends Thread {
    public static final String TAG="BmpProducer";

    CustomFrameAvailableListner customFrameAvailableListner;

    public int height = 513,width = 513;
    Bitmap bmp;
    private boolean running = false;

    public BmpProducer(Context context){
        //bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.img2);
        //bmp = Bitmap.createScaledBitmap(bmp,480,640,true);
        //bmp = null;
        //height = bmp.getHeight();
        //width = bmp.getWidth();
        start();
        running = true;
    }

    BmpProducer(Bitmap bitmap,CustomFrameAvailableListner customFrameAvailableListner){
        bmp = bitmap;
        this.customFrameAvailableListner = customFrameAvailableListner;
        height = bmp.getHeight();
        width = bmp.getWidth();
        start();
    }

    public void setCustomFrameAvailableListner(CustomFrameAvailableListner customFrameAvailableListner){
        this.customFrameAvailableListner = customFrameAvailableListner;
    }

    public void setBitmapData(Bitmap bitmap) {
        bmp = bitmap;
        Log.d(TAG, "setBitmapData: ");
    }

    @Override
    public void run() {
        super.run();
        while ((running)){
            if(bmp == null || customFrameAvailableListner == null)
                continue;
            Log.d(TAG,"Writing frame");
            customFrameAvailableListner.onFrame(bmp);
            /*OTMainActivity.imageView.post(new Runnable() {
                @Override
                public void run() {
                    OTMainActivity.imageView.setImageBitmap(bg);
                }
            });*/
            try{
                Thread.sleep(17);
            }catch (Exception e){
                Log.d(TAG,e.toString());
            }
        }
    }

    public void close(){
        running = false;
    }
}
