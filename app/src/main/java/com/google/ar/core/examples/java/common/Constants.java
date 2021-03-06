package com.google.ar.core.examples.java.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Constants {

    public static int mediapipeWidth = 720;
    public static int mediapipeHeight = 1280;

    public static long previousTimestamp = 0;//解決activity退出前台再次進入，送入mediapipe時間戳不對的問題

    public static String udpServerIp = "192.168.0.1";

    public static int audioServerPort = 8001;
    public static int blend_shapeServerPort = 8002;
    public static int body_poseServerPort = 8003;
    public static int body_lefthandServerPort = 8004;
    public static int body_righthandServerPort = 8005;

    public static String WebRTCServerIp = "http://192.168.0.1:8888";
    //public static String peerName = "xlabs@DESKTOP-5VECIQN";
    public static String peerName = "unity";

    //public static String binaryGraphName = "holistic_tracking_gpu_hand.binarypb";
    public static String binaryGraphName = "holistic_tracking_gpu.binarypb";

    public static String inputVideoStreamName = "input_video";
    public static String outputVideoStreamName = "output_video";
    //public static String poseLandmarks = "pose_world_landmarks";
    //public static String leftHandLandmarks = "left_hand_world_landmarks";
    //public static String rightHandLandmarks = "right_hand_world_landmarks";

    public static String poseLandmarks = "pose_landmarks";
    public static String leftHandLandmarks = "left_hand_landmarks";
    public static String rightHandLandmarks = "right_hand_landmarks";

    public static int minBodyIndex = 10;
    public static int maxBodyIndex = 25;
    //public static Float kalmanParamQ = 0.001f;
    //public static Float kalmanParamR = 0.0015f;
    public static Float kalmanParamQ = 0.0005f;
    public static Float kalmanParamR = 0.0025f;
    public static Float kalmanHandParamQ = 0.001f;
    public static Float kalmanHandParamR = 0.0015f;
    public static Float lowPassParam = 0.1f;
}
