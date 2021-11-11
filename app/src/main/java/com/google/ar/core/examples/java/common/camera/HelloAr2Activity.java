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

import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.ar.core.examples.java.common.Constants;
import com.google.ar.core.examples.java.common.PoseLandmark;
import com.google.ar.core.examples.java.common.converter.BitmapConverter;
import com.google.ar.core.examples.java.common.converter.BmpProducer;
import com.google.ar.core.examples.java.common.egl.EglSurfaceView;
import com.google.ar.core.examples.java.common.helpers.AudioRecordUtil;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.helloar.R;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketCreator;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;
import com.google.protobuf.InvalidProtocolBufferException;
import com.huawei.hiar.ARConfigBase;
import com.huawei.hiar.ARFaceTrackingConfig;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.exceptions.ARCameraNotAvailableException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3D model.
 */
public class HelloAr2Activity extends AppCompatActivity implements View.OnClickListener{

    public ARSession session;
    private ARConfigBase mArConfig;

    private static String ServerIp = Constants.udpServerIp;
    private static final int ServerPort = Constants.body_poseServerPort;

    private Button enter_ip;
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

    private static final String TAG = HelloAr2Activity.class.getSimpleName();


    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private EglSurfaceView surfaceView;

    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    public DisplayRotationHelper displayRotationHelper;

    private CameraGLSurfaceRenderer glSurfaceRenderer = null;


    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        surfaceView = findViewById(R.id.surfaceview);
        bindView();
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
        AudioRecordUtil.getInstance().start();

        // ########## Begin Mediapipe ##########
        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();

        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);

        try {
            applicationInfo =
                    getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }

        Log.i("eglGetCurrentContext-onCreate", EGL14.eglGetCurrentContext().toString());
        eglManager = new EglManager(null);
        processor = new FrameProcessor(
                this,
                eglManager.getNativeContext(),
                applicationInfo.metaData.getString("binaryGraphName"),
                applicationInfo.metaData.getString("inputVideoStreamName"),
                applicationInfo.metaData.getString("outputVideoStreamName"));
        processor.getVideoSurfaceOutput().setFlipY( applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));

        PermissionHelper.checkAndRequestCameraPermissions(this);

        processor.addPacketCallback(
                applicationInfo.metaData.getString("poseLandmarks"),
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    Log.d(TAG, "Received pose landmarks packet.");
                    try {
                        LandmarkProto.NormalizedLandmarkList multiFaceLandmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw);
                        //JSONObject landmarks_json_object = getLandmarksJsonObject(multiFaceLandmarks, "pose");
                        String landmarks_list = getLandmarksListObject(multiFaceLandmarks, "pose");
                        //Log.d(TAG, landmarks_list);
                        send_UDP(landmarks_list);
                    } catch (InvalidProtocolBufferException | JSONException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        // ########## End Mediapipe ##########


        glSurfaceRenderer = new CameraGLSurfaceRenderer(this, this);
        surfaceView.setSharedContext(eglManager.getContext());
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(glSurfaceRenderer);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);//默认情况下，出于性能考虑，会被设置成WILL_NOT_DROW,这样ondraw就不会被执行了，

    }


    @Override
    protected void onDestroy() {
        if (session != null) {
            session.stop();
            session = null;
        }

        super.onDestroy();
        Log.d(TAG, "onDestroy: ");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (surfaceView != null) {
            if (session == null) {
                Exception exception = null;
                String message = null;

                session = new ARSession(this);
                mArConfig = new ARFaceTrackingConfig(session);
                mArConfig.setPowerMode(ARConfigBase.PowerMode.POWER_SAVING);
                session.configure(mArConfig);

                if (message != null) {
                    messageSnackbarHelper.showError(this, message);
                    Log.e(TAG, "Exception creating session", exception);
                    return;
                }
            }

            //session.resume();
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
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
    private static JSONObject getLandmarksJsonObject(LandmarkProto.NormalizedLandmarkList landmarks, String location) throws JSONException {
        JSONObject landmarks_json_object = new JSONObject();
        if (location == "face"){
            int landmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()){
                List<String> list = new ArrayList<String>();
                list.add(String.format("%.8f", landmark.getX()));
                list.add(String.format("%.8f", landmark.getY()));
                list.add(String.format("%.8f", landmark.getZ()));
                String tag = "face_landmark[" + landmarkIndex + "]";
                landmarks_json_object.put(tag, list);
                ++landmarkIndex;
            }
        }
        else if(location == "right_hand"){
            int rlandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                List<String> list = new ArrayList<String>();
                list.add(String.format("%.8f", landmark.getX()));
                list.add(String.format("%.8f", landmark.getY()));
                list.add(String.format("%.8f", landmark.getZ()));
                String tag = "right_hand_landmark[" + rlandmarkIndex + "]";
                landmarks_json_object.put(tag, list);
                ++rlandmarkIndex;
            }
        }
        else if(location == "left_hand"){
            int llandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                List<String> list = new ArrayList<String>();
                list.add(String.format("%.8f", landmark.getX()));
                list.add(String.format("%.8f", landmark.getY()));
                list.add(String.format("%.8f", landmark.getZ()));
                String tag = "left_hand_landmark[" + llandmarkIndex + "]";
                landmarks_json_object.put(tag, list);
                ++llandmarkIndex;
            }
        }
        else if(location == "pose"){
            int plandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                List<String> list = new ArrayList<String>();
                list.add(String.format("%.8f", landmark.getX()));
                list.add(String.format("%.8f", landmark.getY()));
                list.add(String.format("%.8f", landmark.getZ()));
                String tag = "pose_landmark[" + plandmarkIndex + "]";
                landmarks_json_object.put(tag, list);
                ++plandmarkIndex;
            }
        }
        return landmarks_json_object;
    }

/*    private static List<Float> getLandmarksListObject(LandmarkProto.NormalizedLandmarkList landmarks, String location){
        List<Float> result_landmarks = new ArrayList<Float>();
        if (location == "pose"){
            int plandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                if (plandmarkIndex > 10 && plandmarkIndex < 25){
                    result_landmarks.add(landmark.getVisibility());
                    result_landmarks.add(landmark.getX());
                    result_landmarks.add(landmark.getY());
                    result_landmarks.add(landmark.getZ());
                }
                ++plandmarkIndex;
            }
        }
        return result_landmarks;
    }*/

    private static String getLandmarksListObject(LandmarkProto.NormalizedLandmarkList landmarks, String location) throws JSONException, JsonProcessingException {
        //JSONObject result_landmarks = new JSONObject();
        List<PoseLandmark> result_landmarks = new ArrayList<PoseLandmark>();
        ObjectMapper mapper = new ObjectMapper();
        if (location == "pose"){
            int plandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                if (plandmarkIndex > 10 && plandmarkIndex < 25){
                    PoseLandmark current_landmarks = new PoseLandmark();
                    current_landmarks.setIndex(plandmarkIndex);
                    current_landmarks.setScore(landmark.getVisibility());
                    current_landmarks.setX(landmark.getX());
                    current_landmarks.setY(landmark.getY());
                    current_landmarks.setZ(landmark.getZ());
                    result_landmarks.add(current_landmarks);
                }
                ++plandmarkIndex;
            }
        }
        String jsonList = mapper.writeValueAsString(result_landmarks);
        return jsonList;
    }

    // ########## End Mediapipe ##########
    private void send_UDP(String data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data.getBytes(), data.getBytes().length, InetAddress.getByName(ServerIp), ServerPort);
        DatagramSocket socket = new DatagramSocket();
        socket.send(packet);
        Log.d("send--body", String.valueOf(data));
    }
/*    private void send_UDP(List<Float> list) throws IOException {
        DatagramPacket packet = new DatagramPacket(list.toString().getBytes(), list.toString().getBytes().length, InetAddress.getByName(ServerIp), ServerPort);
        DatagramSocket socket = new DatagramSocket();
        socket.send(packet);
        //Log.d("body", String.valueOf(list.size()));
        Log.d("send--body", String.valueOf(list));
    }*/


    private void setServerIp(String ip){
        ServerIp = ip;
    }

    private void bindView(){
        enter_ip = (Button) findViewById(R.id.enter_ip);
        enter_ip.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.enter_ip){
            AlertDialog.Builder dialog = new AlertDialog.Builder(HelloAr2Activity.this);
            dialog.setTitle("Please enter UDP server IP address");
            final View view = View.inflate(HelloAr2Activity.this, R.layout.udpserver_ip, null);
            EditText et = view.findViewById(R.id.server_ip);
            dialog.setView(view);
            dialog.setPositiveButton("confirm", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    String ip = et.getText().toString();
                    Toast.makeText(HelloAr2Activity.this, "UDP server ip:" + ip, Toast.LENGTH_SHORT).show();
                    //serveraddress = ip;
                    AudioRecordUtil.getInstance().setServerIp(ip);
                    glSurfaceRenderer.setServerIp(ip);
                    setServerIp(ip);
                    dialogInterface.cancel();
                }
            });

            dialog.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                }
            });
            dialog.show();
        }
    }
}

