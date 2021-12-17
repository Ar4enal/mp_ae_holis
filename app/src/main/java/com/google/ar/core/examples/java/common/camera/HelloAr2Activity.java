/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.common.camera;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.ar.core.examples.java.common.Constants;
import com.google.ar.core.examples.java.common.HandKalmanFilter;
import com.google.ar.core.examples.java.common.LeftHandLandmark;
import com.google.ar.core.examples.java.common.LogUtil;
import com.google.ar.core.examples.java.common.PoseLandmark;
import com.google.ar.core.examples.java.common.RightHandLandmark;
import com.google.ar.core.examples.java.common.converter.BitmapConverter;
import com.google.ar.core.examples.java.common.converter.BmpProducer;
import com.google.ar.core.examples.java.common.egl.EglSurfaceView;
import com.google.ar.core.examples.java.common.helpers.CameraHelper;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.webrtc.PeerConnectionAdapter;
import com.google.ar.core.examples.java.common.webrtc.SdpAdapter;
import com.google.ar.core.examples.java.common.webrtc.SignalingClient;
import com.google.ar.core.examples.java.common.KalmanLowPassFilter;
import com.google.ar.core.examples.java.helloar.R;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;
import com.huawei.hiar.ARConfigBase;
import com.huawei.hiar.ARFaceTrackingConfig;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.exceptions.ARCameraNotAvailableException;

import org.json.JSONException;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3D model.
 */
public class HelloAr2Activity extends AppCompatActivity implements SignalingClient.Callback{

    public ARSession session;
    
    private boolean isOpenCameraOutside = true;
    private CameraHelper mCamera;
    private int textureId = -1;
    private ARConfigBase mArConfig;
    private Surface mPreViewSurface;
    private Surface mVgaSurface;
    private Surface mMetaDataSurface;
    private Surface mDepthSurface;
    public static String ServerIp;
    private static final int ServerPort = Constants.body_poseServerPort;
    private static final KalmanLowPassFilter kalmanLowPassFilter = new KalmanLowPassFilter();
    private static final HandKalmanFilter rightHandKalmanFilter = new HandKalmanFilter();
    private static final HandKalmanFilter leftHandKalmanFilter = new HandKalmanFilter();

    // ########## Begin Mediapipe ##########
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;
    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    //private ExternalTextureConverter converter;
    private BitmapConverter converter;
    // Handles camera access via the {@link CameraX} Jetpack support library.

    BmpProducer bitmapProducer;
    // ########## End Mediapipe ##########
    PeerConnectionFactory peerConnectionFactory;
    PeerConnection peerConnection;
    MediaStream mediaStream;

    private static final String TAG = HelloAr2Activity.class.getSimpleName();


    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private EglSurfaceView surfaceView;

    public DisplayRotationHelper displayRotationHelper;

    private CameraGLSurfaceRenderer glSurfaceRenderer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        surfaceView = findViewById(R.id.surfaceview);
        //bindView();
        Intent intent = getIntent();
        ServerIp = intent.getStringExtra("server_ip");
        setServerIp(ServerIp);

        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        EglBase.Context eglBaseContext = EglBase.create().getEglBaseContext();

        // create PeerConnectionFactory
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(this)
                .createInitializationOptions());
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory =
                new DefaultVideoEncoderFactory(eglBaseContext, true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory =
                new DefaultVideoDecoderFactory(eglBaseContext);
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();

        // create VideoCapturer
        //VideoCapturer videoCapturer = createCameraCapturer(true);
        //VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        // create VideoTrack
        //VideoTrack videoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        mediaStream = peerConnectionFactory.createLocalMediaStream("mediaStream");
        //mediaStream.addTrack(videoTrack);
        mediaStream.addTrack(audioTrack);

        SignalingClient.get().setCallback(this);
        webrtcCall();

        // ########## Begin Mediapipe ##########
        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();

        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);

        Log.i("eglGetCurrentContext-onCreate", EGL14.eglGetCurrentContext().toString());
        eglManager = new EglManager(null);
        processor = new FrameProcessor(
                this,
                eglManager.getNativeContext(),
                Constants.binaryGraphName,
                Constants.inputVideoStreamName,
                Constants.outputVideoStreamName);
        processor.getVideoSurfaceOutput().setFlipY(FLIP_FRAMES_VERTICALLY);

        //PermissionHelper.checkAndRequestCameraPermissions(this);

        processor.addPacketCallback(
                Constants.poseLandmarks,
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    try {
                        LandmarkProto.NormalizedLandmarkList PoseLandmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw);
                        String landmarks_list = getLandmarksListObject(PoseLandmarks, "pose");
                        send_UDP(landmarks_list, "pose");
                        //LandmarkProto.LandmarkList poseWorldLandmarks = LandmarkProto.LandmarkList.parseFrom(landmarksRaw);
                        //String landmarks_list = getWorldLandmarks(poseWorldLandmarks, "pose");
                        //send_UDP(landmarks_list, "pose");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        processor.addPacketCallback(
                Constants.leftHandLandmarks,
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    try {
                        //LandmarkProto.LandmarkList leftHandLandmarks = LandmarkProto.LandmarkList.parseFrom(landmarksRaw);
                        //String landmarks_list = getWorldLandmarks(leftHandLandmarks, "left_hand");
                        LandmarkProto.NormalizedLandmarkList leftHandLandmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw);
                        String landmarks_list = getLandmarksListObject(leftHandLandmarks, "left_hand");
                        send_UDP(landmarks_list, "left_hand");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        processor.addPacketCallback(
                Constants.rightHandLandmarks,
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    try {
                        //LandmarkProto.LandmarkList rightHandLandmarks = LandmarkProto.LandmarkList.parseFrom(landmarksRaw);
                        //String landmarks_list = getWorldLandmarks(rightHandLandmarks, "right_hand");
                        LandmarkProto.NormalizedLandmarkList rightHandLandmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw);
                        String landmarks_list = getLandmarksListObject(rightHandLandmarks, "right_hand");
                        send_UDP(landmarks_list, "right_hand");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        // ########## End Mediapipe ##########


        glSurfaceRenderer = new CameraGLSurfaceRenderer(this, this);
        glSurfaceRenderer.setServerIp(ServerIp);
        surfaceView.setSharedContext(eglManager.getContext());
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(glSurfaceRenderer);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);//默认情况下，出于性能考虑，会被设置成WILL_NOT_DROW,这样ondraw就不会被执行了，
    }

    private VideoCapturer createCameraCapturer(boolean isFront) {
        Camera1Enumerator enumerator = new Camera1Enumerator(false);
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (isFront ? enumerator.isFrontFacing(deviceName) : enumerator.isBackFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private void webrtcCall() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, new PeerConnectionAdapter("localconnection") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                SignalingClient.get().sendIceCandidate(iceCandidate);
            }
        });

        assert peerConnection != null;
        peerConnection.addStream(mediaStream);
    }


    @Override
    protected void onDestroy() {
        if (session != null) {
            session.stop();
            session = null;
        }

        super.onDestroy();
        Log.d(TAG, "onDestroy: ");
        SignalingClient.get().sendSignOut();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (surfaceView != null) {
            if (session == null) {

                session = new ARSession(this);
                mArConfig = new ARFaceTrackingConfig(session);
                mArConfig.setPowerMode(ARConfigBase.PowerMode.POWER_SAVING);
                if (isOpenCameraOutside) {
                    mArConfig.setImageInputMode(ARConfigBase.ImageInputMode.EXTERNAL_INPUT_ALL);
                }
                session.configure(mArConfig);
            }

            try {
                session.resume();
            } catch (ARCameraNotAvailableException e) {
                Toast.makeText(this, "Camera open failed, please restart the app", Toast.LENGTH_LONG).show();
                session = null;
                return;
            }

            if (surfaceView != null)
                surfaceView.onResume();
            displayRotationHelper.onResume();
        }
        setCamera();
        converter = new BitmapConverter(eglManager.getContext());
        converter.setConsumer(processor);
        startProducer();
        Log.d(TAG, "onResume: ");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }

        // ########## Begin Mediapipe ##########
        converter.close();
        bitmapProducer.close();
        Log.d(TAG, "onPause: ");

        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE);
        // ########## End Mediapipe ##########
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();

            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }

        // Mediapipe
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, results);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }


    // ########## Begin Mediapipe ##########
    private void startProducer() {
        bitmapProducer = new BmpProducer(this);
        glSurfaceRenderer.setBitmapProducer(bitmapProducer);
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                bitmapProducer.setCustomFrameAvailableListner(converter);
                                Log.d(TAG, "surfaceChanged: " + width + " " + height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }


    //打印輸出結果
    private String getWorldLandmarks(LandmarkProto.LandmarkList poseWorldLandmarks, String location) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        if (location.equals("pose")){
            List<PoseLandmark> result_landmarks = new ArrayList<>();
            int poseLandmarkIndex = 0;
            for (LandmarkProto.Landmark landmark : poseWorldLandmarks.getLandmarkList()) {
                if (poseLandmarkIndex > 10 && poseLandmarkIndex < 25) {
                    kalmanUpdateWorld(landmark, poseLandmarkIndex);
                    PoseLandmark current_landmarks = new PoseLandmark();
                    current_landmarks.setIndex(poseLandmarkIndex);
                    current_landmarks.setScore(landmark.getVisibility());
                    current_landmarks.setX(kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[0]);
                    current_landmarks.setY(kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[1]);
                    current_landmarks.setZ(kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[2]);
                    result_landmarks.add(current_landmarks);
                }
                ++poseLandmarkIndex;
            }
            return mapper.writeValueAsString(result_landmarks);
        }
        else if (location.equals("left_hand")){
            List<LeftHandLandmark> result_landmarks = new ArrayList<>();
            int leftHandLandmarkIndex = 0;
            for (LandmarkProto.Landmark landmark : poseWorldLandmarks.getLandmarkList()) {
                //leftHandKalmanUpdate(landmark, leftHandLandmarkIndex);
                LeftHandLandmark current_landmarks = new LeftHandLandmark();
                current_landmarks.setIndex(leftHandLandmarkIndex);
                current_landmarks.setX(leftHandKalmanFilter.getFilterHashMap().get(leftHandLandmarkIndex).Pos3D[0]);
                current_landmarks.setY(leftHandKalmanFilter.getFilterHashMap().get(leftHandLandmarkIndex).Pos3D[1]);
                current_landmarks.setZ(leftHandKalmanFilter.getFilterHashMap().get(leftHandLandmarkIndex).Pos3D[2]);
                result_landmarks.add(current_landmarks);
                ++leftHandLandmarkIndex;
            }
            return mapper.writeValueAsString(result_landmarks);
        }
        else if (location.equals("right_hand")){
            List<RightHandLandmark> result_landmarks = new ArrayList<>();
            int rightHandLandmarkIndex = 0;
            for (LandmarkProto.Landmark landmark : poseWorldLandmarks.getLandmarkList()) {
                //rightHandKalmanUpdate(landmark, rightHandLandmarkIndex);
                RightHandLandmark current_landmarks = new RightHandLandmark();
                current_landmarks.setIndex(rightHandLandmarkIndex);
                current_landmarks.setX(rightHandKalmanFilter.getFilterHashMap().get(rightHandLandmarkIndex).Pos3D[0]);
                current_landmarks.setY(rightHandKalmanFilter.getFilterHashMap().get(rightHandLandmarkIndex).Pos3D[0]);
                current_landmarks.setZ(rightHandKalmanFilter.getFilterHashMap().get(rightHandLandmarkIndex).Pos3D[0]);
                result_landmarks.add(current_landmarks);
                ++rightHandLandmarkIndex;
            }
            return mapper.writeValueAsString(result_landmarks);
        }
        else{
            return "null";
        }
    }

    private static String getLandmarksListObject(LandmarkProto.NormalizedLandmarkList landmarks, String location) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        if (location.equals("pose")){
            List<PoseLandmark> result_landmarks = new ArrayList<>();
            int poseLandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                if (poseLandmarkIndex > Constants.minBodyIndex && poseLandmarkIndex < Constants.maxBodyIndex){
                    kalmanUpdate(landmark, poseLandmarkIndex);
                    PoseLandmark current_landmarks = new PoseLandmark();
                    current_landmarks.setIndex(poseLandmarkIndex);
                    current_landmarks.setScore(landmark.getVisibility());
                    current_landmarks.setX(kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[0]);
                    current_landmarks.setY(kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[1]);
                    current_landmarks.setZ(kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[2]);
                    result_landmarks.add(current_landmarks);
                }
                ++poseLandmarkIndex;
            }
            return mapper.writeValueAsString(result_landmarks);
        }
        else if (location.equals("left_hand")){
            List<LeftHandLandmark> result_landmarks = new ArrayList<>();
            int leftHandLandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                leftHandKalmanUpdate(landmark, leftHandLandmarkIndex);
                LeftHandLandmark current_landmarks = new LeftHandLandmark();
                current_landmarks.setIndex(leftHandLandmarkIndex);
                current_landmarks.setX(leftHandKalmanFilter.getFilterHashMap().get(leftHandLandmarkIndex).Pos3D[0]);
                current_landmarks.setY(leftHandKalmanFilter.getFilterHashMap().get(leftHandLandmarkIndex).Pos3D[1]);
                current_landmarks.setZ(leftHandKalmanFilter.getFilterHashMap().get(leftHandLandmarkIndex).Pos3D[2]);
                result_landmarks.add(current_landmarks);
                ++leftHandLandmarkIndex;
            }
            return mapper.writeValueAsString(result_landmarks);
        }
        else if (location.equals("right_hand")){
            List<RightHandLandmark> result_landmarks = new ArrayList<>();
            int rightHandLandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                rightHandKalmanUpdate(landmark, rightHandLandmarkIndex);
                RightHandLandmark current_landmarks = new RightHandLandmark();
                current_landmarks.setIndex(rightHandLandmarkIndex);
                current_landmarks.setX(rightHandKalmanFilter.getFilterHashMap().get(rightHandLandmarkIndex).Pos3D[0]);
                current_landmarks.setY(rightHandKalmanFilter.getFilterHashMap().get(rightHandLandmarkIndex).Pos3D[1]);
                current_landmarks.setZ(rightHandKalmanFilter.getFilterHashMap().get(rightHandLandmarkIndex).Pos3D[2]);
                result_landmarks.add(current_landmarks);
                ++rightHandLandmarkIndex;
            }
            return mapper.writeValueAsString(result_landmarks);
        }
        else{
            return "null";
        }
    }


/*    private static List<PoseAngle> calculateAngle(List<PoseLandmark> result_landmarks) {
        HashMap<String, float[]> PoseAngleMap = setPoseLandmarks(result_landmarks);
        List<PoseAngle> poseAngle = new ArrayList<>();

        for (String name : PoseAngleMap.keySet()){
            PoseAngle current_angle = new PoseAngle();
            current_angle.setName(name);
            if (name.equals("left_elbow") || name.equals("right_elbow")){
                current_angle.setAngle(getElbowDegree(PoseAngleMap.get(name)));
            }
            else {
                current_angle.setAngle(getDegree(PoseAngleMap.get(name)));
            }
            current_angle.setScore(PoseAngleMap.get(name)[6]);
            poseAngle.add(current_angle);
        }

        return poseAngle;
    }

    private static HashMap<String, float[]> setPoseLandmarks(List<PoseLandmark> result_landmarks) {
        HashMap<String, float[]> PoseAngleMap = new HashMap<>();
        PoseAngleMap.put("left_wrist", new float[]{result_landmarks.get(5).getX(), result_landmarks.get(5).getY(), result_landmarks.get(11).getX(), result_landmarks.get(11).getY(), result_landmarks.get(3).getX(), result_landmarks.get(3).getY(), result_landmarks.get(5).getScore()});
        PoseAngleMap.put("left_elbow", new float[]{result_landmarks.get(3).getX(), result_landmarks.get(3).getY(), result_landmarks.get(5).getX(), result_landmarks.get(5).getY(), result_landmarks.get(1).getX(), result_landmarks.get(1).getY(), result_landmarks.get(3).getScore()});
        PoseAngleMap.put("left_shoulder", new float[]{result_landmarks.get(1).getX(), result_landmarks.get(1).getY(), result_landmarks.get(3).getX(), result_landmarks.get(3).getY(), result_landmarks.get(13).getX(), result_landmarks.get(13).getY(), result_landmarks.get(1).getScore()});
        PoseAngleMap.put("right_shoulder", new float[]{result_landmarks.get(0).getX(), result_landmarks.get(0).getY(), result_landmarks.get(2).getX(), result_landmarks.get(2).getY(), result_landmarks.get(12).getX(), result_landmarks.get(12).getY(), result_landmarks.get(0).getScore()});
        PoseAngleMap.put("right_elbow", new float[]{result_landmarks.get(2).getX(), result_landmarks.get(2).getY(), result_landmarks.get(4).getX(), result_landmarks.get(4).getY(), result_landmarks.get(0).getX(), result_landmarks.get(0).getY(), result_landmarks.get(2).getScore()});
        PoseAngleMap.put("right_wrist", new float[]{result_landmarks.get(4).getX(), result_landmarks.get(4).getY(), result_landmarks.get(10).getX(), result_landmarks.get(10).getY(), result_landmarks.get(2).getX(), result_landmarks.get(2).getY(), result_landmarks.get(4).getScore()});
        return PoseAngleMap;
    }

    private static int getDegree(float[] floats){
        float p1x = floats[0];
        float p1y = floats[1];
        float p2x = floats[2];
        float p2y = floats[3];
        float p3x = floats[4];
        float p3y = floats[5];
        float vector = (p2x-p1x)*(p3x-p1x) + (p2y-p1y)*(p3y-p1y);
        double sqrt = Math.sqrt(
                (Math.abs((p2x-p1x)*(p2x-p1x)) + Math.abs((p2y-p1y)*(p2y-p1y)))   *
                        (Math.abs((p3x-p1x)*(p3x-p1x)) + Math.abs((p3y-p1y)*(p3y-p1y)))
        );
        double radian = Math.acos(vector/sqrt);
        return (int) (180*radian/ Math.PI);
    }

    private static int getElbowDegree(float[] floats) {
        float p1x = floats[0];
        float p1y = floats[1];
        float p2x = floats[2];
        float p2y = floats[3];
        float p3x = floats[4];
        float p3y = floats[5];
        float k = (p1y - p3y) / (p1x - p3x);
        float b = p1y - k * p1x;
        int angle = 0;
        float vector = (p2x-p1x)*(p3x-p1x) + (p2y-p1y)*(p3y-p1y);
        double sqrt = Math.sqrt(
                (Math.abs((p2x-p1x)*(p2x-p1x)) + Math.abs((p2y-p1y)*(p2y-p1y)))   *
                        (Math.abs((p3x-p1x)*(p3x-p1x)) + Math.abs((p3y-p1y)*(p3y-p1y)))
        );
        double radian = Math.acos(vector/sqrt);
        if ((p2x * k + b) >= p2y){
            angle = (int) -(180*radian/ Math.PI);
        }
        else if ((p2x * k + b) < p2y){
            angle = (int) (180*radian/ Math.PI);
        }
        return angle;
    }*/

    private static void kalmanUpdateWorld(LandmarkProto.Landmark landmark, int poseLandmarkIndex){
        kalmanLowPassFilter.setFilterHashMapNow3D(poseLandmarkIndex, landmark.getX(), landmark.getY(), landmark.getZ());
        measurementUpdate(poseLandmarkIndex, kalmanLowPassFilter);
        kalmanLowPassFilter.setFilterHashMapPos3D(poseLandmarkIndex,
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[0] + (kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[0] - kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[0]) * kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).K[0],
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[1] + (kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[1] - kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[1]) * kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).K[1],
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[2] + (kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[2] - kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[2]) * kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).K[2]
        );
        kalmanLowPassFilter.setX(poseLandmarkIndex,
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[0],
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[1],
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[2]
        );
        lowPassUpdate(kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex));
    }

    private static void kalmanUpdate(LandmarkProto.NormalizedLandmark landmark, int poseLandmarkIndex){
        kalmanLowPassFilter.setFilterHashMapNow3D(poseLandmarkIndex, landmark.getX(), landmark.getY(), landmark.getZ());
        measurementUpdate(poseLandmarkIndex, kalmanLowPassFilter);
        kalmanLowPassFilter.setFilterHashMapPos3D(poseLandmarkIndex,
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[0] + (kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[0] - kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[0]) * kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).K[0],
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[1] + (kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[1] - kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[1]) * kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).K[1],
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[2] + (kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[2] - kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).X[2]) * kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).K[2]
                );
        kalmanLowPassFilter.setX(poseLandmarkIndex,
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[0],
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[1],
                kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[2]
                );
        lowPassUpdate(kalmanLowPassFilter.getFilterHashMap().get(poseLandmarkIndex));
    }

    private static void measurementUpdate(int poseLandmarkIndex, KalmanLowPassFilter measurement){
        Float kalmanParamQ = Constants.kalmanParamQ;
        Float kalmanParamR = Constants.kalmanParamR;
        measurement.setK(poseLandmarkIndex,
                (measurement.getFilterHashMap().get(poseLandmarkIndex).P[0] + kalmanParamQ)/(measurement.getFilterHashMap().get(poseLandmarkIndex).P[0] + kalmanParamQ + kalmanParamR),
                (measurement.getFilterHashMap().get(poseLandmarkIndex).P[1] + kalmanParamQ)/(measurement.getFilterHashMap().get(poseLandmarkIndex).P[1] + kalmanParamQ + kalmanParamR),
                (measurement.getFilterHashMap().get(poseLandmarkIndex).P[2] + kalmanParamQ)/(measurement.getFilterHashMap().get(poseLandmarkIndex).P[2] + kalmanParamQ + kalmanParamR)
                );
        measurement.setP(poseLandmarkIndex,
                kalmanParamR * (measurement.getFilterHashMap().get(poseLandmarkIndex).P[0] + kalmanParamQ) / (kalmanParamR + measurement.getFilterHashMap().get(poseLandmarkIndex).P[0] + kalmanParamQ),
                kalmanParamR * (measurement.getFilterHashMap().get(poseLandmarkIndex).P[1] + kalmanParamQ) / (kalmanParamR + measurement.getFilterHashMap().get(poseLandmarkIndex).P[1] + kalmanParamQ),
                kalmanParamR * (measurement.getFilterHashMap().get(poseLandmarkIndex).P[2] + kalmanParamQ) / (kalmanParamR + measurement.getFilterHashMap().get(poseLandmarkIndex).P[2] + kalmanParamQ)
                );
    }

    private static void lowPassUpdate(KalmanLowPassFilter.Filter jp){
        jp.PrevPos3D[0] = jp.Pos3D;
        Float lowPassParam = Constants.lowPassParam;
        for (int i = 1; i < jp.PrevPos3D.length; i++)
        {
            jp.PrevPos3D[i][0] = jp.PrevPos3D[i][0] * lowPassParam + jp.PrevPos3D[i - 1][0] * (1f - lowPassParam);
            jp.PrevPos3D[i][1] = jp.PrevPos3D[i][1] * lowPassParam + jp.PrevPos3D[i - 1][1] * (1f - lowPassParam);
            jp.PrevPos3D[i][2] = jp.PrevPos3D[i][2] * lowPassParam + jp.PrevPos3D[i - 1][2] * (1f - lowPassParam);
        }
        jp.Pos3D = jp.PrevPos3D[jp.PrevPos3D.length - 1];
    }


    //hand filter
    private static void leftHandKalmanUpdate(LandmarkProto.NormalizedLandmark landmark, int poseLandmarkIndex){
        leftHandKalmanFilter.setFilterHashMapNow3D(poseLandmarkIndex, landmark.getX(), landmark.getY(), landmark.getZ());
        handMeasurementUpdate(poseLandmarkIndex, leftHandKalmanFilter);
        leftHandKalmanFilter.setFilterHashMapPos3D(poseLandmarkIndex,
                leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[0] + (leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[0] - leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[0]) * leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).K[0],
                leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[1] + (leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[1] - leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[1]) * leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).K[1],
                leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[2] + (leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[2] - leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[2]) * leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).K[2]
        );
        leftHandKalmanFilter.setX(poseLandmarkIndex,
                leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[0],
                leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[1],
                leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[2]
        );
        handLowPassUpdate(leftHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex));
    }

    private static void rightHandKalmanUpdate(LandmarkProto.NormalizedLandmark landmark, int poseLandmarkIndex){
        rightHandKalmanFilter.setFilterHashMapNow3D(poseLandmarkIndex, landmark.getX(), landmark.getY(), landmark.getZ());
        handMeasurementUpdate(poseLandmarkIndex, rightHandKalmanFilter);
        rightHandKalmanFilter.setFilterHashMapPos3D(poseLandmarkIndex,
                rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[0] + (rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[0] - rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[0]) * rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).K[0],
                rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[1] + (rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[1] - rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[1]) * rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).K[1],
                rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[2] + (rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Now3D[2] - rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).X[2]) * rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).K[2]
        );
        rightHandKalmanFilter.setX(poseLandmarkIndex,
                rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[0],
                rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[1],
                rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex).Pos3D[2]
        );
        handLowPassUpdate(rightHandKalmanFilter.getFilterHashMap().get(poseLandmarkIndex));
    }

    private static void handMeasurementUpdate(int poseLandmarkIndex, HandKalmanFilter measurement){
        Float kalmanParamQ = Constants.kalmanParamQ;
        Float kalmanParamR = Constants.kalmanParamR;
        measurement.setK(poseLandmarkIndex,
                (measurement.getFilterHashMap().get(poseLandmarkIndex).P[0] + kalmanParamQ)/(measurement.getFilterHashMap().get(poseLandmarkIndex).P[0] + kalmanParamQ + kalmanParamR),
                (measurement.getFilterHashMap().get(poseLandmarkIndex).P[1] + kalmanParamQ)/(measurement.getFilterHashMap().get(poseLandmarkIndex).P[1] + kalmanParamQ + kalmanParamR),
                (measurement.getFilterHashMap().get(poseLandmarkIndex).P[2] + kalmanParamQ)/(measurement.getFilterHashMap().get(poseLandmarkIndex).P[2] + kalmanParamQ + kalmanParamR)
        );
        measurement.setP(poseLandmarkIndex,
                kalmanParamR * (measurement.getFilterHashMap().get(poseLandmarkIndex).P[0] + kalmanParamQ) / (kalmanParamR + measurement.getFilterHashMap().get(poseLandmarkIndex).P[0] + kalmanParamQ),
                kalmanParamR * (measurement.getFilterHashMap().get(poseLandmarkIndex).P[1] + kalmanParamQ) / (kalmanParamR + measurement.getFilterHashMap().get(poseLandmarkIndex).P[1] + kalmanParamQ),
                kalmanParamR * (measurement.getFilterHashMap().get(poseLandmarkIndex).P[2] + kalmanParamQ) / (kalmanParamR + measurement.getFilterHashMap().get(poseLandmarkIndex).P[2] + kalmanParamQ)
        );
    }

    private static void handLowPassUpdate(HandKalmanFilter.Filter jp){
        jp.PrevPos3D[0] = jp.Pos3D;
        Float lowPassParam = Constants.lowPassParam;
        for (int i = 1; i < jp.PrevPos3D.length; i++)
        {
            jp.PrevPos3D[i][0] = jp.PrevPos3D[i][0] * lowPassParam + jp.PrevPos3D[i - 1][0] * (1f - lowPassParam);
            jp.PrevPos3D[i][1] = jp.PrevPos3D[i][1] * lowPassParam + jp.PrevPos3D[i - 1][1] * (1f - lowPassParam);
            jp.PrevPos3D[i][2] = jp.PrevPos3D[i][2] * lowPassParam + jp.PrevPos3D[i - 1][2] * (1f - lowPassParam);
        }
        jp.Pos3D = jp.PrevPos3D[jp.PrevPos3D.length - 1];
    }


    // ########## End Mediapipe ##########

    private void setCamera() {
        if (isOpenCameraOutside && mCamera == null) {
            LogUtil.info(TAG, "new Camera");
            DisplayMetrics dm = new DisplayMetrics();
            mCamera = new CameraHelper(this);
            mCamera.setupCamera(dm.widthPixels, dm.heightPixels);
        }

        // Check whether setCamera is called for the first time.
        if (isOpenCameraOutside) {
            if (textureId == -1) {
                //int[] textureIds = new int[1];
                //GLES20.glGenTextures(1, textureIds, 0);
                textureId = glSurfaceRenderer.getmTexture();
            }
            session.setCameraTextureName(textureId);
            initSurface();
            SurfaceTexture surfaceTexture = new SurfaceTexture(textureId);
            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.setPreViewSurface(mPreViewSurface);
            mCamera.setVgaSurface(mVgaSurface);
            mCamera.setDepthSurface(mDepthSurface);
            if (!mCamera.openCamera()) {
                String showMessage = "Open camera filed!";
                LogUtil.error(TAG, showMessage);
                Toast.makeText(this, showMessage, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initSurface() {
        List<ARConfigBase.SurfaceType> surfaceTypeList = mArConfig.getImageInputSurfaceTypes();
        List<Surface> surfaceList = mArConfig.getImageInputSurfaces();

        LogUtil.info(TAG, "surfaceList size : " + surfaceList.size());
        int size = surfaceTypeList.size();
        for (int i = 0; i < size; i++) {
            ARConfigBase.SurfaceType type = surfaceTypeList.get(i);
            Surface surface = surfaceList.get(i);
            if (ARConfigBase.SurfaceType.PREVIEW.equals(type)) {
                mPreViewSurface = surface;
            } else if (ARConfigBase.SurfaceType.VGA.equals(type)) {
                mVgaSurface = surface;
            } else if (ARConfigBase.SurfaceType.METADATA.equals(type)) {
                mMetaDataSurface = surface;
            } else if (ARConfigBase.SurfaceType.DEPTH.equals(type)) {
                mDepthSurface = surface;
            } else {
                LogUtil.info(TAG, "Unknown type.");
            }
            LogUtil.info(TAG, "list[" + i + "] get surface : " + surface + ", type : " + type);
        }
    }

    private void send_UDP(String data, String location) throws IOException {
        int port;
        switch (location) {
            case "pose":
                port = Constants.body_poseServerPort;
                break;
            case "left_hand":
                port = Constants.body_lefthandServerPort;
                break;
            case "right_hand":
                port = Constants.body_righthandServerPort;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + location);
        }
        //DatagramPacket packet = new DatagramPacket(data.getBytes(), data.getBytes().length, InetAddress.getByName(ServerIp), ServerPort);
        DatagramPacket packet = new DatagramPacket(data.getBytes(), data.getBytes().length, InetAddress.getByName(ServerIp), port);
        DatagramSocket socket = new DatagramSocket();
        socket.send(packet);
        Log.d("send--body", data);
    }

    private void setServerIp(String ip){
        ServerIp = ip;
    }

    @Override
    public void onCreateRoom() {

    }

    @Override
    public void onPeerJoined() {

    }

    @Override
    public void onSelfJoined() {
        peerConnection.createOffer(new SdpAdapter("local offer sdp") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                peerConnection.setLocalDescription(new SdpAdapter("local set local"), sessionDescription);
                try {
                    SignalingClient.get().sendOfferSessionDescription(sessionDescription);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new MediaConstraints());
    }

    @Override
    public void onPeerLeave(String msg) {

    }

    @Override
    public void onSelfLeave() {
        SignalingClient.get().sendSignOut();
    }

    @Override
    public void onOfferReceived(JSONObject data) {
        runOnUiThread(() -> {
            peerConnection.setRemoteDescription(new SdpAdapter("localSetRemote"),
                    new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
            peerConnection.createAnswer(new SdpAdapter("localAnswerSdp") {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    super.onCreateSuccess(sdp);
                    peerConnection.setLocalDescription(new SdpAdapter("localSetLocal"), sdp);
                    try {
                        SignalingClient.get().sendAnswerSessionDescription(sdp);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new MediaConstraints());

        });
    }

    @Override
    public void onAnswerReceived(JSONObject data) {
        peerConnection.setRemoteDescription(new SdpAdapter("localSetRemote"),
                new SessionDescription(SessionDescription.Type.ANSWER, data.getString("sdp")));
    }

    @Override
    public void onIceCandidateReceived(JSONObject data) {
        peerConnection.addIceCandidate(new IceCandidate(
                data.getString("sdpMid"),
                data.getInteger("sdpMLineIndex"),
                data.getString("candidate")
        ));
    }
}

