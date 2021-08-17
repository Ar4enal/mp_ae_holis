package com.google.ar.core.examples.java.common.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.SurfaceHolder;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.Constants;
import com.google.ar.core.examples.java.common.converter.BitmapConverter;
import com.google.ar.core.examples.java.common.converter.BmpProducer;
import com.google.mediapipe.components.FrameProcessor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class CameraGLSurfaceRenderer implements GLSurfaceView.Renderer {
    //zicamera 渲染器单目 无畸变

    private static final String TAG = "GLSurfaceRenderer";
    public HelloAr2Activity mainActivity;
    private int viewWidth = 0;
    private int viewHeight = 0;

    private float[] projmtx = new float[16];

    private int mTexture;
    private String vertShader;
    private String fragShader_Pre;
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
    //    private final int COORDS_PER_VERTEX = 2;
//    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex
    private final short drawOrder[] = {0, 1, 2, 0, 2, 3}; // order to draw vertices
    private int mvpMatrixHandle;
    private final float[] displayMatrix = new float[16];

    BmpProducer bitmapProducer;


    public CameraGLSurfaceRenderer(HelloAr2Activity mainActivity, BmpProducer mapProducer) {
        this.mainActivity = mainActivity;
        this.bitmapProducer = mapProducer;
    }

    private final float squareVertices[] = { // in counterclockwise order:
            -1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f,
            1.0f, 1.0f
    };
    private final float textureVerticesPreview[] = { // in counterclockwise order:
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f
    };

    private final float[] VERTEX_DATA = new float[]{
            -1, -1, 1, 1.0f,
            1, -1, 1, 0.0f,
            -1, 1, 0, 1,
            1, 1, 0, 0
    };
    private FloatBuffer vertexBuffer = createBuffer(VERTEX_DATA);

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated: ");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Create the texture and pass it to ARCore session to be filled during update().
        // backgroundRenderer.createOnGlThread(context);
        mTexture = createVideoTexture();


        // Prepare the other rendering objects.
        try {
            mainActivity.planeRenderer.createOnGlThread(mainActivity, "models/trigrid.png");
        } catch (IOException e) {
            Log.e(TAG, "Failed to read plane texture");
        }

        //mPointCloud.createOnGlThread(context);
        initTexture();

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (width <= 0 || height <= 0) {
            Log.d(TAG, "onSurfaceChanged(), <= 0");
            return;
        }
        Log.d(TAG, "onSurfaceChanged()");

        mainActivity.displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
        viewWidth = width;
        viewHeight = height;

    }

    @Override
    public void onDrawFrame(GL10 gl) {
        //Log.d(TAG, "onDrawFrame(GL10 gl)");
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (viewWidth == 0 || viewWidth == 0) {
            return;
        }
        if (mainActivity.session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        if (mainActivity.session != null) {
            mainActivity.displayRotationHelper.updateSessionIfNeeded(mainActivity.session);
            mainActivity.session.setCameraTextureName(mTexture);
        }


        try {
            if (mainActivity.session != null) {
                // Obtain the current frame from ARSession. When the configuration is set to
                Frame frame = mainActivity.session.update();
                Camera camera = frame.getCamera();


                Image image = frame.acquireCameraImage();
                byte[] nv21;
                // Get the three planes.
                ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
                ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
                ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

                int ySize = yBuffer.remaining();
                int uSize = uBuffer.remaining();
                int vSize = vBuffer.remaining();
                //Log.d(TAG, "onDrawFrame: " + ySize + " " + uSize + " " + vSize);

                nv21 = new byte[ySize + uSize + vSize];

                //U and V are swapped
                yBuffer.get(nv21, 0, ySize);
                vBuffer.get(nv21, ySize, vSize);
                uBuffer.get(nv21, ySize + vSize, uSize);

                int width = image.getWidth();
                int height = image.getHeight();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
                yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
                byte[] byteArray = out.toByteArray();
                Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);

                android.graphics.Matrix mat = new android.graphics.Matrix();//旋转90度
                mat.postRotate(90);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mat, true);

                bitmap = Bitmap.createScaledBitmap(bitmap, 720, 1280, true);//好像要缩放到这个尺寸送进去计算的距离才正常
                Log.d(TAG, "onDrawFrame: " + bitmap.getWidth()+" "+bitmap.getHeight());

                bitmapProducer.setBitmapData(bitmap);

                //bitmap.recycle();
                image.close();

                if(Constants.intrinsicsFocalLength == 0) {
                    Constants.intrinsicsFocalLength = camera.getTextureIntrinsics().getFocalLength()[0];
                    Constants.imageWidth = bitmap.getWidth();
                    Constants.imageHeight = bitmap.getHeight();
                    Log.d(TAG, "Constants change intrinsicsFocalLength " + Constants.intrinsicsFocalLength+" size "+Constants.imageWidth+" "+Constants.imageHeight);
                }


                //Log.d(TAG, "onDrawFrame: ************ " + Arrays.toString(camera.getImageIntrinsics().getFocalLength()));
                //Log.d(TAG, "onDrawFrame: ************ " + Arrays.toString(camera.getTextureIntrinsics().getFocalLength()));

                // Draw background.
                // backgroundRenderer.draw(frame);
                draw();

                // If not tracking, don't draw 3d objects. 如果获取的camera状态不对，不绘制3D 物体，需要注释
                if (camera.getTrackingState() == TrackingState.PAUSED) {
                    return;
                }


                // Visualize tracked points.

                // Get camera matrix  and projection matrix.
                //camera.getViewMatrix(viewmtx, 0);
                camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);


                // Visualize planes.
                mainActivity.planeRenderer.drawPlanes(mainActivity.session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);


            }

            //Log.d(TAG, "camera pose matrix2: " + " " + mTranslation[0] + " " + mTranslation[1] + " " + mTranslation[2] + "   " + finalQuaternion.ToEulerAngles().pitch*57.29578 + " " + finalQuaternion.ToEulerAngles().yaw*57.29578 + " " + finalQuaternion.ToEulerAngles().roll*57.29578);
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    private void initTexture() {
        verticesBuffer = IVCGLLib.glToFloatBuffer(squareVertices);
        textureVerticesPreviewBuffer = IVCGLLib.glToFloatBuffer(textureVerticesPreview);

        vertShader = IVCGLLib.loadFromAssetsFile("IVC_VShader_Preview.sh", mainActivity.getResources());
        fragShader_Pre = IVCGLLib.loadFromAssetsFile("IVC_FShader_Camera.sh", mainActivity.getResources());
        programHandle = IVCGLLib.glCreateProgram(vertShader, fragShader_Pre);
        // IVCGLLib.glCheckGlError("glCreateProgram");
        mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix");
        // IVCGLLib.glCheckGlError("glGetUniformLocation");
        mPositionHandle = GLES20.glGetAttribLocation(programHandle, "position");
        mTextureCoordHandle = GLES20.glGetAttribLocation(programHandle, "inputTextureCoordinate");
    }

    private void draw() {
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
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexture);
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

}
