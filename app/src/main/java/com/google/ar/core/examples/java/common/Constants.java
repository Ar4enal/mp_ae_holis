package com.google.ar.core.examples.java.common;

public class Constants {

    public static int mediapipeWidth = 720;
    public static int mediapipeHeight = 1280;

    public static long previousTimestamp = 0;//解決activity退出前台再次進入，送入mediapipe時間戳不對的問題

    public static String udpServerIp = "192.168.0.1";

    public static int audioServerPort = 8001;
    public static int blend_shapeServerPort = 8002;
    public static int body_poseServerPort = 8003;

    public static String WebRTCServerIp = "http://192.168.1.108:8888";
    //public static String peerName = "xlabs@DESKTOP-5VECIQN";
    public static String peerName = "unity";

    public static String binaryGraphName = "holistic_tracking_gpu.binarypb";
    public static String inputVideoStreamName = "input_video";
    public static String outputVideoStreamName = "output_video";
    public static String poseLandmarks = "pose_landmarks";

    public static int minBodyIndex = 10;
    public static int maxBodyIndex = 25;
    public static Float kalmanParamQ = 0.001f;
    public static Float kalmanParamR = 0.0015f;
    public static Float lowPassParam = 0.1f;
}
