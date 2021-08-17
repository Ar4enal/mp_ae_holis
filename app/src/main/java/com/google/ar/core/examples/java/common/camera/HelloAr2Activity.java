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

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.examples.java.common.Constants;
import com.google.ar.core.examples.java.common.converter.BitmapConverter;
import com.google.ar.core.examples.java.common.converter.BmpProducer;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.helloar.R;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketCreator;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3D model.
 */
public class HelloAr2Activity extends AppCompatActivity {

    // ########## Begin Mediapipe ##########
    private static final String BINARY_GRAPH_NAME = "hand_tracking_mobile_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "hand_landmarks";
    private static final String INPUT_NUM_HANDS_SIDE_PACKET_NAME = "num_hands";
    private static final int NUM_HANDS = 1;
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.BACK;
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    private static class HandLandmarks {
        public static int WRIST = 0;
        public static int THUMB_TIP = 4;
        public static int INDEX_FINGER_TIP = 8;
        public static int PINKY_MCP = 17;
    }

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
    private GLSurfaceView surfaceView;

    public Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    public DisplayRotationHelper displayRotationHelper;

    private CameraGLSurfaceRenderer glSurfaceRenderer = null;

    public PlaneRenderer planeRenderer = new PlaneRenderer();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

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
                BINARY_GRAPH_NAME,
                INPUT_VIDEO_STREAM_NAME,
                OUTPUT_VIDEO_STREAM_NAME);
        processor.getVideoSurfaceOutput().setFlipY(FLIP_FRAMES_VERTICALLY);

        PermissionHelper.checkAndRequestCameraPermissions(this);

        AndroidPacketCreator packetCreator = processor.getPacketCreator();
        Map<String, Packet> inputSidePackets = new HashMap<>();
        inputSidePackets.put(INPUT_NUM_HANDS_SIDE_PACKET_NAME, packetCreator.createInt32(NUM_HANDS));
        processor.setInputSidePackets(inputSidePackets);


        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME,
                (packet) -> {
                    //Log.v("MediaPipe", "Received multi-hand landmarks packet.");
                    List<NormalizedLandmarkList> multiHandLandmarks =
                            PacketGetter.getProtoVector(packet, NormalizedLandmarkList.parser());

                    Log.v(TAG, "[TS:" + packet.getTimestamp() + "] " + getWristLandmarksDistance(multiHandLandmarks));

                });



        startProducer();
        // ########## End Mediapipe ##########


        glSurfaceRenderer = new CameraGLSurfaceRenderer(this, bitmapProducer);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(glSurfaceRenderer);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);//默认情况下，出于性能考虑，会被设置成WILL_NOT_DROW,这样ondraw就不会被执行了，

        //surfaceView.setVisibility(View.GONE);
        //surfaceView = null;

    }


    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (surfaceView != null) {
            if (session == null) {
                Exception exception = null;
                String message = null;
                try {
                    // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                    // permission on Android M and above, now is a good time to ask the user for it.
                    if (!CameraPermissionHelper.hasCameraPermission(this)) {
                        CameraPermissionHelper.requestCameraPermission(this);
                        return;
                    }

                    // Create the session.
                    session = new Session(this);
                } catch (UnavailableArcoreNotInstalledException e) {
                    message = "Please install ARCore";
                    exception = e;
                } catch (UnavailableApkTooOldException e) {
                    message = "Please update ARCore";
                    exception = e;
                } catch (UnavailableSdkTooOldException e) {
                    message = "Please update this app";
                    exception = e;
                } catch (UnavailableDeviceNotCompatibleException e) {
                    message = "This device does not support AR";
                    exception = e;
                } catch (Exception e) {
                    message = "Failed to create AR session";
                    exception = e;
                }

                if (message != null) {
                    messageSnackbarHelper.showError(this, message);
                    Log.e(TAG, "Exception creating session", exception);
                    return;
                }
            }

            // Note that order matters - see the note in onPause(), the reverse applies here.
            try {
                configureSession();

                session.resume();
            } catch (CameraNotAvailableException e) {
                messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
                session = null;
                Log.d(TAG, "onResume: " + "Camera not available. Try restarting the app.");
                return;
            }

            if (surfaceView != null)
                surfaceView.onResume();
            displayRotationHelper.onResume();
        }

        converter = new BitmapConverter(eglManager.getContext());
        converter.setConsumer(processor);
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
                                //Constants.imageWidth = width;
                                //Constants.imageHeight = height;
                                Log.d(TAG, "surfaceChanged: " + width + " " + height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    private String getMultiHandLandmarksDebugString(List<NormalizedLandmarkList> multiHandLandmarks) {
        if (multiHandLandmarks.isEmpty()) {
            return "No hand landmarks";
        }
        String multiHandLandmarksStr = "Number of hands detected: " + multiHandLandmarks.size() + "\n";
        int handIndex = 0;
        for (NormalizedLandmarkList landmarks : multiHandLandmarks) {
            multiHandLandmarksStr +=
                    "\t#Hand landmarks for hand[" + handIndex + "]: " + landmarks.getLandmarkCount() + "\n";
            int landmarkIndex = 0;
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {


/*                float x_px = (float) Math.min(Math.floor(landmark.getX() * Constants.imageWidth), Constants.imageWidth - 1);
                float y_px = (float) Math.min(Math.floor(landmark.getY() * Constants.imageHeight), Constants.imageHeight - 1);
                float z_px = (float) Math.min(landmark.getZ() * Constants.imageWidth , Constants.imageWidth - 1);*/

                float x_px = (float) landmark.getX() * Constants.imageWidth;
                float y_px = (float) landmark.getY() * Constants.imageHeight;
                float z_px = (float) landmark.getZ() * Constants.imageWidth;


                multiHandLandmarksStr +=
                        "\t\tLandmark ["
                                + landmarkIndex
                                + "]: ("
                                + x_px
                                + ", "
                                + y_px
                                + ", "
                                + z_px
                                + ")\n";
                ++landmarkIndex;
            }
            ++handIndex;
        }
        return multiHandLandmarksStr;
    }

    private float getWristLandmarksDistance(List<NormalizedLandmarkList> multiHandLandmarks) {
        if (!multiHandLandmarks.isEmpty()) {
            NormalizedLandmarkList hand = multiHandLandmarks.get(0);
            NormalizedLandmark thumbTip = hand.getLandmark(HandLandmarks.THUMB_TIP);
            NormalizedLandmark indexTip = hand.getLandmark(HandLandmarks.INDEX_FINGER_TIP);
            NormalizedLandmark wrist = hand.getLandmark(HandLandmarks.WRIST);
            NormalizedLandmark pinkyMCP = hand.getLandmark(HandLandmarks.PINKY_MCP);

         /*   // u = thumbTip - wrist
            // v = indexTip - wrist
            float ux = thumbTip.getX()-wrist.getX();
            float uy = thumbTip.getY()-wrist.getY();
            float uz = thumbTip.getZ()-wrist.getZ();

            float vx = indexTip.getX()-wrist.getX();
            float vy = indexTip.getY()-wrist.getY();
            float vz = indexTip.getZ()-wrist.getZ();

            float dot = ux*vx + uy*vy + uz*vz;

            float angle = (float) Math.acos(dot / (Math.sqrt(ux*ux+uy*uy+uz*uz) * Math.sqrt(vx*vx+vy*vy+vz*vz)));*/

            if(Constants.imageWidth == 0 || Constants.imageHeight == 0 || Constants.intrinsicsFocalLength == 0) {
                return -1;
            }

            // t = indexTip - thumbTip
            float tx = pinkyMCP.getX() * Constants.imageWidth - wrist.getX() * Constants.imageWidth;
            float ty = pinkyMCP.getY() * Constants.imageHeight - wrist.getY() * Constants.imageHeight;
            float tz = pinkyMCP.getZ() * Constants.imageWidth - wrist.getZ() * Constants.imageWidth;
            float distance_px = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);

            float distance = (float) (0.06 * Constants.intrinsicsFocalLength / distance_px);//mate 20 X
            //float distance = (float) (0.06 * 1108.0687 / distance_px);//mate 30 pro
            //float distance = (float) (0.06 * 474.89273 / distance_px);//sanxing s20


            //Log.d(TAG, "onCreate: Distance = "+distance);
            return distance;
        }

        return -1;
    }
    // ########## End Mediapipe ##########


    /**
     * Configures the session with feature settings.
     */
    private void configureSession() {
        Config config = session.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
      /*  CameraConfigFilter cameraConfigFilter =
                new CameraConfigFilter(session)
                        .setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60, CameraConfig.TargetFps.TARGET_FPS_30));
        List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
        session.setCameraConfig(cameraConfigs.get(cameraConfigs.size() - 2));//选择分辨率最高的
        for (int i = 0; i < cameraConfigs.size(); i++) {
            Log.d(TAG, "initSession: setCameraConfig" + cameraConfigs.get(i).getFpsRange() + " " + cameraConfigs.get(i).getImageSize());
        }*/
        session.configure(config);
    }
}
