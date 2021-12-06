package com.google.ar.core.examples.java.common.camera;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.examples.java.common.Constants;
import com.google.ar.core.examples.java.common.FaceKalmanLowPassFilter;
import com.google.ar.core.examples.java.common.converter.BmpProducer;
import com.google.ar.core.examples.java.common.egl.EglSurfaceView;
import com.huawei.hiar.ARCamera;
import com.huawei.hiar.ARFace;
import com.huawei.hiar.ARFaceBlendShapes;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARPose;
import com.huawei.hiar.ARTrackable;

import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collection;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class CameraGLSurfaceRenderer implements EglSurfaceView.Renderer {
    //zicamera 渲染器单目 无畸变

    private static final String TAG = "GLSurfaceRenderer";
    public HelloAr2Activity mainActivity;
    private int viewWidth = 0;
    private int viewHeight = 0;

    private int mTexture;
    private int programHandle;
    private int mPositionHandle;
    private int mTextureCoordHandle;
    FloatBuffer verticesBuffer, textureVerticesPreviewBuffer;
    // number of coordinates per vertex in this array
    private static final int BYTES_PER_FLOAT = 4;

    private static final int POSITION_COORDS_PER_VERTEX = 2; // X, Y
    private static final int TEXTURE_COORDS_PER_VERTEX = 2;
    private static final int CPV = POSITION_COORDS_PER_VERTEX + TEXTURE_COORDS_PER_VERTEX;
    private static final int VERTEX_STRIDE_BYTES = CPV * BYTES_PER_FLOAT;
    // order to draw vertices
    private int mvpMatrixHandle;
    private final float[] displayMatrix = new float[16];
    BmpProducer bitmapProducer;

    private final FaceGeometryDisplay mFaceGeometryDisplay = new FaceGeometryDisplay();
    private final Context mContext;
    private static String ServerIp = Constants.udpServerIp;
    private static final int ServerPort = Constants.blend_shapeServerPort;
    private static final FaceKalmanLowPassFilter faceKalmanLowPassFilter = new FaceKalmanLowPassFilter();

    public CameraGLSurfaceRenderer(Context context, HelloAr2Activity mainActivity) {
        mContext = context;
        this.mainActivity = mainActivity;
    }

    public void setBitmapProducer(BmpProducer mapProducer) {
        bitmapProducer = mapProducer;
    }

    private final float[] squareVertices = { // in counterclockwise order:
            -1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f,
            1.0f, 1.0f
    };
    private final float[] textureVerticesPreview = { // in counterclockwise order:
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f
    };

    private final float[] VERTEX_DATA_FBO = new float[]{
            -1, -1, 1, 1.0f,
            1, -1, 1, 0.0f,
            -1, 1, 0, 1,
            1, 1, 0, 0
    };

    private final float[] VERTEX_DATA = new float[]{
            -1, -1, 0, 1.0f,
            1, -1, 0, 0.0f,
            -1, 1, 1, 1,
            1, 1, 1, 0
    };

    private final FloatBuffer vertexBuffer = createBuffer(VERTEX_DATA);

    private final FloatBuffer vertexBufferFBO = createBuffer(VERTEX_DATA_FBO);

    private int mOffscreenTexture;

    int destinationTextureId;

    // 帧缓冲对象 - 颜色、深度、模板附着点，纹理对象可以连接到帧缓冲区对象的颜色附着点
    private int mFrameBuffer;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated: ");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        initTexture();
        mFaceGeometryDisplay.init(mContext);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (width <= 0 || height <= 0) {
            Log.d(TAG, "onSurfaceChanged(), <= 0");
            return;
        }
        Log.d(TAG, "onSurfaceChanged() " + width + " " + height);

        mainActivity.displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
        viewWidth = width;
        viewHeight = height;

        Constants.mediapipeWidth = width;
        Constants.mediapipeHeight = height;

        mTexture = createVideoTexture();

        createFBO();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        //Log.d(TAG, "onDrawFrame(GL10 gl)");
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (viewWidth == 0 || viewHeight == 0) {
            return;
        }
        if (mainActivity.session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        mainActivity.displayRotationHelper.updateSessionIfNeeded(mainActivity.session);
        mainActivity.session.setCameraTextureName(mTexture);


        try {
            if (mainActivity.session != null) {
                // Obtain the current frame from ARSession. When the configuration is set to
                ARFrame frame = mainActivity.session.update();
                ARCamera camera = frame.getCamera();
                //Log.d(TAG, "onDrawFrame: width"+camera.getCameraImageIntrinsics().getImageDimensions()[0]+" "+camera.getCameraImageIntrinsics().getImageDimensions()[1]);

                Collection<ARFace> faces = mainActivity.session.getAllTrackables(ARFace.class);
                Log.d(TAG, "Face number: " + faces.size());
                // Draw background.
                drawFrameBuffer();
                draw(mOffscreenTexture);
                for (ARFace face : faces) {
                    if (face.getTrackingState() == ARTrackable.TrackingState.TRACKING){
                        mFaceGeometryDisplay.onDrawFrame(camera, face);
                        ARFaceBlendShapes blendShapes = face.getFaceBlendShapes();
                        JSONObject faceBlendShapes = new JSONObject(blendShapes.getBlendShapeDataMapKeyString());
                        kalmanUpdate(face.getPose());
                        faceBlendShapes.put("qx", faceKalmanLowPassFilter.Pos3D[1]);
                        faceBlendShapes.put("qy", faceKalmanLowPassFilter.Pos3D[2]);
                        faceBlendShapes.put("qz", faceKalmanLowPassFilter.Pos3D[3]);
                        faceBlendShapes.put("qw", faceKalmanLowPassFilter.Pos3D[0]);
                        faceBlendShapes.put("face_detected", true);
                        send_UDP(faceBlendShapes);
                    }
                    else if (face.getTrackingState() == ARTrackable.TrackingState.PAUSED){
                        JSONObject faceBlendShapes = new JSONObject();
                        faceBlendShapes.put("face_detected", false);
                        send_UDP(faceBlendShapes);
                    }
                }

            }

            } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }
    private void kalmanUpdate(ARPose pose) {
        faceKalmanLowPassFilter.setNow3D(pose);
        measurementUpdate(faceKalmanLowPassFilter);
        faceKalmanLowPassFilter.setPos3D(
            faceKalmanLowPassFilter.X[0] + (faceKalmanLowPassFilter.Now3D[0] - faceKalmanLowPassFilter.X[0]) * faceKalmanLowPassFilter.K[0],
                faceKalmanLowPassFilter.X[1] + (faceKalmanLowPassFilter.Now3D[1] - faceKalmanLowPassFilter.X[1]) * faceKalmanLowPassFilter.K[1],
                faceKalmanLowPassFilter.X[2] + (faceKalmanLowPassFilter.Now3D[2] - faceKalmanLowPassFilter.X[2]) * faceKalmanLowPassFilter.K[2],
                faceKalmanLowPassFilter.X[3] + (faceKalmanLowPassFilter.Now3D[3] - faceKalmanLowPassFilter.X[3]) * faceKalmanLowPassFilter.K[3]
        );
        faceKalmanLowPassFilter.setX(
                faceKalmanLowPassFilter.Pos3D[0],
                faceKalmanLowPassFilter.Pos3D[1],
                faceKalmanLowPassFilter.Pos3D[2],
                faceKalmanLowPassFilter.Pos3D[3]
                );
        lowPassUpdate(faceKalmanLowPassFilter);
    }

    private void measurementUpdate(FaceKalmanLowPassFilter faceKalmanLowPassFilter) {
        Float kalmanParamQ = Constants.kalmanParamQ;
        Float kalmanParamR = Constants.kalmanParamR;
        faceKalmanLowPassFilter.setK(
                (faceKalmanLowPassFilter.P[0] + kalmanParamQ) / (faceKalmanLowPassFilter.P[0] + kalmanParamQ + kalmanParamR),
                (faceKalmanLowPassFilter.P[1] + kalmanParamQ) / (faceKalmanLowPassFilter.P[1] + kalmanParamQ + kalmanParamR),
                (faceKalmanLowPassFilter.P[2] + kalmanParamQ) / (faceKalmanLowPassFilter.P[2] + kalmanParamQ + kalmanParamR),
                (faceKalmanLowPassFilter.P[3] + kalmanParamQ) / (faceKalmanLowPassFilter.P[3] + kalmanParamQ + kalmanParamR)
        );
        faceKalmanLowPassFilter.setP(
                kalmanParamR * (faceKalmanLowPassFilter.P[0] + kalmanParamQ) / (faceKalmanLowPassFilter.P[0] + kalmanParamQ + kalmanParamR),
                kalmanParamR * (faceKalmanLowPassFilter.P[1] + kalmanParamQ) / (faceKalmanLowPassFilter.P[1] + kalmanParamQ + kalmanParamR),
                kalmanParamR * (faceKalmanLowPassFilter.P[2] + kalmanParamQ) / (faceKalmanLowPassFilter.P[2] + kalmanParamQ + kalmanParamR),
                kalmanParamR * (faceKalmanLowPassFilter.P[3] + kalmanParamQ) / (faceKalmanLowPassFilter.P[3] + kalmanParamQ + kalmanParamR)
        );
    }

    private void lowPassUpdate(FaceKalmanLowPassFilter faceKalmanLowPassFilter) {
        faceKalmanLowPassFilter.PrevPos3D[0] = faceKalmanLowPassFilter.Pos3D;
        Float lowPassParam = Constants.lowPassParam;
        for (int i = 1; i < faceKalmanLowPassFilter.PrevPos3D.length; i++){
            faceKalmanLowPassFilter.PrevPos3D[i][0] = faceKalmanLowPassFilter.PrevPos3D[i][0] * lowPassParam + faceKalmanLowPassFilter.PrevPos3D[i-1][0] * (1f - lowPassParam);
            faceKalmanLowPassFilter.PrevPos3D[i][1] = faceKalmanLowPassFilter.PrevPos3D[i][1] * lowPassParam + faceKalmanLowPassFilter.PrevPos3D[i-1][1] * (1f - lowPassParam);
            faceKalmanLowPassFilter.PrevPos3D[i][2] = faceKalmanLowPassFilter.PrevPos3D[i][2] * lowPassParam + faceKalmanLowPassFilter.PrevPos3D[i-1][2] * (1f - lowPassParam);
            faceKalmanLowPassFilter.PrevPos3D[i][3] = faceKalmanLowPassFilter.PrevPos3D[i][3] * lowPassParam + faceKalmanLowPassFilter.PrevPos3D[i-1][3] * (1f - lowPassParam);
        }
        faceKalmanLowPassFilter.Pos3D = faceKalmanLowPassFilter.PrevPos3D[faceKalmanLowPassFilter.PrevPos3D.length - 1];
    }

    private void send_UDP(JSONObject data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data.toString().getBytes(), data.toString().getBytes().length, InetAddress.getByName(ServerIp), ServerPort);
        DatagramSocket socket = new DatagramSocket();
        socket.send(packet);
        Log.d("send--face", String.valueOf(data));
    }

    private void initTexture() {
        verticesBuffer = IVCGLLib.glToFloatBuffer(squareVertices);
        textureVerticesPreviewBuffer = IVCGLLib.glToFloatBuffer(textureVerticesPreview);

        String vertShader = IVCGLLib.loadFromAssetsFile("IVC_VShader_Preview.sh", mainActivity.getResources());
        String fragShader_Pre = IVCGLLib.loadFromAssetsFile("IVC_FShader_Camera.sh", mainActivity.getResources());
        programHandle = IVCGLLib.glCreateProgram(vertShader, fragShader_Pre);
        mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix");
        mPositionHandle = GLES20.glGetAttribLocation(programHandle, "position");
        mTextureCoordHandle = GLES20.glGetAttribLocation(programHandle, "inputTextureCoordinate");
    }


    private void drawFrameBuffer() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);


        //使用程序
        GLES20.glUseProgram(programHandle);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);

        Matrix.setIdentityM(displayMatrix, 0);
        //Matrix.scaleM(displayMatrix,0,720/viewWidth,1280/viewHeight,1);

        // GLES20.gl
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexture);//绑定渲染纹理
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programHandle, "sampler2d1"), 0);
        //
        vertexBufferFBO.position(0);
        GLES20.glVertexAttribPointer(//设置顶点位置值
                mPositionHandle,
                POSITION_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                vertexBufferFBO);

        // Load texture data.
        vertexBufferFBO.position(POSITION_COORDS_PER_VERTEX);
        GLES20.glVertexAttribPointer(
                mTextureCoordHandle,
                TEXTURE_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                vertexBufferFBO);

        GLES20.glUniform1i(GLES20.glGetUniformLocation(programHandle, "stereoARMode"), 2);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programHandle, "eyeA"), 0.0f);


        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, displayMatrix, 0);
        GLES20.glViewport(0, 0, Constants.mediapipeWidth, Constants.mediapipeHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_DATA.length / CPV);


        //GLES20.glDeleteTextures(1, new int[]{destinationTextureId}, 0);
        destinationTextureId = createRgbaTexture(Constants.mediapipeWidth, Constants.mediapipeHeight);
        //GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, destinationTextureId);
        GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, Constants.mediapipeWidth, Constants.mediapipeHeight);
        //destinationTextureId = ShaderUtil.createRgbaTexture(bitmap);
        bitmapProducer.setBitmapData(0, destinationTextureId, Constants.mediapipeWidth, Constants.mediapipeHeight);
        //GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        Log.d(TAG, "destinationTextureId: " + destinationTextureId );


        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordHandle);
    }


    private void draw(int texture) {
        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        GLES20.glUseProgram(programHandle);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);

        Matrix.setIdentityM(displayMatrix, 0);

        // GLES20.gl
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programHandle, "sampler2d1"), 0);
        //
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(
                mPositionHandle,
                POSITION_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                vertexBuffer);
        //   Utils.checkGlError();

        // Load texture data.
        vertexBuffer.position(POSITION_COORDS_PER_VERTEX);
        GLES20.glVertexAttribPointer(
                mTextureCoordHandle,
                TEXTURE_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                vertexBuffer);

        GLES20.glUniform1i(GLES20.glGetUniformLocation(programHandle, "stereoARMode"), 2);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programHandle, "eyeA"), 0.0f);


        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, displayMatrix, 0);
        GLES20.glViewport(0, 0, viewWidth, viewHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_DATA.length / CPV);

        //VR模式不执行draw操作

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordHandle);

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

    }


    public FloatBuffer createBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * BYTES_PER_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer buffer = bb.asFloatBuffer();
        buffer.put(data);
        buffer.position(0);

        return buffer;
    }

    private int createVideoTexture() {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        return texture[0];
    }

    /**
     * 创建fbo
     */
    private void createFBO() {
        int[] values = new int[1];

        GLES20.glGenTextures(1, values, 0);
        mOffscreenTexture = values[0];   // expected > 0
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOffscreenTexture);

        // Create texture storage.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, viewWidth, viewHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        //ShaderUtil.checkGlError("glTexImage2D");

        // Set parameters.  We're probably using non-power-of-two dimensions, so
        // some values may not be available for use.
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);


        //1. 创建FBO
        GLES20.glGenFramebuffers(1, values, 0);
        mFrameBuffer = values[0];    // expected > 0
        //2. 绑定FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mOffscreenTexture, 0);


        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.i("", "Framebuffer error");
        }

        //7. 解绑纹理和FBO
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    public int createRgbaTexture(int width, int height) {
        int[] textureName = new int[]{0};
        GLES20.glGenTextures(1, textureName, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureName[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, (Buffer) null);
        //ShaderUtil.checkGlError("glTexImage2D");
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        //ShaderUtil.checkGlError("texture setup");
        return textureName[0];
    }

    public void setServerIp(String ip) {
        ServerIp = ip;
    }
}
