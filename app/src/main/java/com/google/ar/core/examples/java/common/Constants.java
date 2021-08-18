package com.google.ar.core.examples.java.common;

public class Constants {

    public static int imageWidth = 0;
    public static int imageHeight = 0;

    public static int textureWidth = 720;
    public static int textureHeight = 1280;

    public static float intrinsicsFocalLength = 0;

    public static long previousTimestamp = 0;//解決activity退出前台再次進入，送入mediapipe時間戳不對的問題
}
