package com.google.ar.core.examples.java.common;

public class Constants {

    public static int imageWidth = 0;
    public static int imageHeight = 0;

    public static int mediapipeWidth = 720;
    public static int mediapipeHeight = 1280;

    public static float intrinsicsFocalLength = 0;

    public static long previousTimestamp = 0;//解決activity退出前台再次進入，送入mediapipe時間戳不對的問題

    public static String udpServerIp = "192.168.0.1";

    public static int audioServerPort = 8001;
    public static int blend_shapeServerPort = 8002;
    public static int body_poseServerPort = 8003;

    public static String WebRTCServerIp = "http://192.168.1.106:8888";
    public static String peerName = "xlabs@DESKTOP-5VECIQN";

    public static String cameraFacingFront = "false";
    public static String binaryGraphName = "holistic_tracking_gpu.binarypb";
    public static String inputVideoStreamName = "input_video";
    public static String outputVideoStreamName = "output_video";
    public static String poseLandmarks = "pose_landmarks";
    public static String flipFramesVertically = "true";
    public static String converterNumBuffers = "3";
}
