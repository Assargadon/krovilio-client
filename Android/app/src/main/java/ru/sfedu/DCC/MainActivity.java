package ru.sfedu.DCC;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class MainActivity extends Activity {
    public static final String EXTRA_USERINFO = "EXTRA_USERINFO";
    private static final String TAG = "MainActivity";

    private final boolean enableCam2 = true;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    public Bundle mMetadata = new Bundle();
    public String mUserID = "PPG";

    private Button startStopButton;
    private TextureView[] mTextureViews = new TextureView[2];
    private String[] mCameraIds = new String[2];
    private CameraDevice[] mCameraDevices = new CameraDevice[2];
    private CameraCaptureSession[] mCameraCaptureSessions = new CameraCaptureSession[2];
    private MediaRecorder[] mMediaRecorders = new MediaRecorder[2];
    private CaptureRequest.Builder[] mCaptureRequestBuilders = new CaptureRequest.Builder[2];
    private String[] mNextVideoAbsolutePaths = new String[2];
    private String mNextMetadataAbsolutePath;
    private ArrayList<Surface>[] mSurfaces = new ArrayList[2];

    private Size[] mPreviewSizes = new Size[2];
    private Size[] mVideoSizes = new Size[2];
    private boolean isRecording = false;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mMetadata = extras.getBundle(EXTRA_USERINFO);
            if (mMetadata != null) mUserID = mMetadata.getString("user_id", mUserID);
        }

        textureListeners[0] = this.textureListener1;
        textureListeners[1] = this.textureListener2;
        stateCallback[0] = this.stateCallback1;
        stateCallback[1] = this.stateCallback2;

        TextureView textureView1;
        TextureView textureView2;

        textureView1 = (TextureView) findViewById(R.id.texture1);
        assert textureView1 != null;

        textureView2 = (TextureView) findViewById(R.id.texture2);
        assert textureView2 != null;

        startStopButton = (Button) findViewById(R.id.btn_takepicture);
        assert startStopButton != null;
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startStopHandler();
            }
        });

        mTextureViews[0] = textureView1;
        mTextureViews[1] = textureView2;

        setSurfaceTextureListener(0);
        if (enableCam2) setSurfaceTextureListener(1);
    }

    void setSurfaceTextureListener(int index) {
        mTextureViews[index].setSurfaceTextureListener(textureListeners[index]);
    }

    TextureView.SurfaceTextureListener textureListener1 = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera(0);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    TextureView.SurfaceTextureListener textureListener2 = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera(1);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    TextureView.SurfaceTextureListener[] textureListeners = new TextureView.SurfaceTextureListener[2];

    private final CameraDevice.StateCallback stateCallback1 = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            mCameraDevices[0] = camera;
            startPreview(0);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraDevices[0].close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            //cameraDevice[0].close();
            mCameraDevices[0] = null;
            Toast.makeText(MainActivity.this, "Unfortunately, dual camera preview is not supported on this device", Toast.LENGTH_LONG).show();
        }
    };
    private final CameraDevice.StateCallback stateCallback2 = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            mCameraDevices[1] = camera;
            startPreview(1);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraDevices[1].close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            //cameraDevice[1].close();
            mCameraDevices[1] = null;
            Toast.makeText(MainActivity.this, "Unfortunately, dual camera preview is not supported on this device", Toast.LENGTH_LONG).show();
        }
    };
    private CameraDevice.StateCallback[] stateCallback = new CameraDevice.StateCallback[2];

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void startStopHandler() {
        if (isRecording) {
            mMetadata.putLong("stop_timestamp", System.currentTimeMillis() / 1000);
            mMetadata.putInt("finger_width", mVideoSizes[0].getWidth());
            mMetadata.putInt("finger_height", mVideoSizes[0].getHeight());
            mMetadata.putInt("face_width", mVideoSizes[1].getWidth());
            mMetadata.putInt("face_height", mVideoSizes[1].getHeight());
            mMetadata.putString("finger_video", mNextVideoAbsolutePaths[0]);
            mMetadata.putString("face_video", mNextVideoAbsolutePaths[1]);
            stopRecording(0);
            if (enableCam2) stopRecording(1);
            startStopButton.setText(R.string.btn_record_video);
            saveMetadata();
        } else {
            mMetadata.putLong("start_timestamp", System.currentTimeMillis() / 1000);
            getNextFileGroupPaths();
            startRecording(0);
            if (enableCam2) startRecording(1);
            startStopButton.setText(R.string.btn_stop_recording);
        }
        isRecording = !isRecording;
        /*
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice[0].getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView1.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice[0].createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            final File file = new File(Environment.getExternalStorageDirectory() + "/pic.jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Toast.makeText(MainActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
                    createCameraPreview(0);
                }
            };
            cameraDevice[0].createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }*/
    }

    protected void startPreview(final int index) {
        if (null == mCameraDevices[index] || !mTextureViews[index].isAvailable()) {
            return;
        }
        try {
            SurfaceTexture texture = mTextureViews[index].getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSizes[index].getWidth(), mPreviewSizes[index].getHeight());
            Surface surface = new Surface(texture);
            mCaptureRequestBuilders[index] = mCameraDevices[index].createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilders[index].addTarget(surface);

            if (index == 0) mCaptureRequestBuilders[index].set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);

            mCameraDevices[index].createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                    // When the session is ready, we start displaying the preview.
                    mCameraCaptureSessions[index] = cameraCaptureSession;
                    updatePreview(index);
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession(int index) {
        if (mCameraCaptureSessions[index] != null) {
            mCameraCaptureSessions[index].close();
            mCameraCaptureSessions[index] = null;
        }
    }

    private void startRecording(final int index) {
        if (null == mCameraDevices[index] || !mTextureViews[index].isAvailable()) {
            return;
        }
        try {
            closePreviewSession(index);
            setUpMediaRecorder(index);
            SurfaceTexture texture = mTextureViews[index].getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSizes[index].getWidth(), mPreviewSizes[index].getHeight());
            mCaptureRequestBuilders[index] = mCameraDevices[index].createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            mSurfaces[index] = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            mCaptureRequestBuilders[index].addTarget(previewSurface);
            mSurfaces[index].add(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorders[index].getSurface();
            mCaptureRequestBuilders[index].addTarget(recorderSurface);
            mSurfaces[index].add(recorderSurface);
            boolean b = recorderSurface.isValid();

                if (index == 0) mCaptureRequestBuilders[index].set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);

            //new Handler().postDelayed(new Runnable() {
            //    @Override
            //    public void run() {

                    try {
                        // Start a capture session
                        // Once the session starts, we can update the UI and start recording
                        mCameraDevices[index].createCaptureSession(mSurfaces[index], new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                mCameraCaptureSessions[index] = cameraCaptureSession;
                                updatePreview(index);

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Start recording
                                        mMediaRecorders[index].start();
                                    }
                                });
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                Toast.makeText(MainActivity.this, "Failed", Toast.LENGTH_SHORT).show();

                            }
                        }, mBackgroundHandler);

                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
            //    }
            //}, 500);


        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

    }

    private void stopRecording(int index) {
        // Stop recording
        try {
            mMediaRecorders[index].stop();
            mMediaRecorders[index].reset();
            Toast.makeText(this, "Video saved: " + mNextVideoAbsolutePaths[index], Toast.LENGTH_SHORT).show();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }

        startPreview(index);
    }

    private void saveMetadata() {
        JSONObject json = new JSONObject();
        Set<String> keys = mMetadata.keySet();
        for (String key : keys) {
            try {
                json.put(key, JSONObject.wrap(mMetadata.get(key)));
            } catch(JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            Writer output = null;
            File file = new File(mNextMetadataAbsolutePath);
            output = new BufferedWriter(new FileWriter(file));
            output.write(json.toString());
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int index) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return;
        //Log.e(TAG, "is camera open");
        try {
            mCameraIds[index] = manager.getCameraIdList()[index];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraIds[index]);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
            mPreviewSizes[index] = previewSizes[0];
            Size[] videoSizes = map.getOutputSizes(MediaRecorder.class);
            if (index == 1) {
                mVideoSizes[index] = videoSizes[5];
            } else {
                mVideoSizes[index] = videoSizes[videoSizes.length - 1];
            }
            // Add permission for camera and let user grant the permission
            if (
                    checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        //Manifest.permission.FLASHLIGHT,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(mCameraIds[index], stateCallback[index], null);
            mMediaRecorders[index] = new MediaRecorder();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        //Log.e(TAG, "openCamera X");
    }

    private void getNextFileGroupPaths() {
        //final File dir = this.getExternalFilesDir(null);
        final File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        long t = System.currentTimeMillis();
        String d = (dir == null ? "" : (dir.getAbsolutePath() + "/"));
        String userIDEscaped = mUserID.replaceAll("\\W+", "_");
        mNextVideoAbsolutePaths[0] = d + userIDEscaped + "_" + t + "_FINGER.mp4";
        mNextVideoAbsolutePaths[1] = d + userIDEscaped + "_" + t + "_FACE.mp4";
        mNextMetadataAbsolutePath =  d + userIDEscaped + "_" + t + "_METADATA.json";
    }

    private void setUpMediaRecorder(int index) throws IOException {
        //mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorders[index].setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorders[index].setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mMediaRecorders[index].setOutputFile(mNextVideoAbsolutePaths[index]);
        mMediaRecorders[index].setVideoEncodingBitRate(10000000);
        mMediaRecorders[index].setVideoFrameRate(30);
        mMediaRecorders[index].setVideoSize(mVideoSizes[index].getWidth(), mVideoSizes[index].getHeight());
        mMediaRecorders[index].setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //mMediaRecorders[index].setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        mMediaRecorders[index].prepare();
    }

    protected void updatePreview(int index) {
        if (null == mCameraDevices[index]) {
            Log.e(TAG, "updatePreview error, return");
        }

        if (index == 0 || enableCam2)
        {
            mCaptureRequestBuilders[index].set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            try {
                mCameraCaptureSessions[index].setRepeatingRequest(mCaptureRequestBuilders[index].build(), null, mBackgroundHandler);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Unfortunately, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    void openOrListen(int index) {
        if (mTextureViews[index].isAvailable()) {
            openCamera(index);
        } else {
            mTextureViews[index].setSurfaceTextureListener(textureListeners[index]);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();

        openOrListen(0);
        if (enableCam2) openOrListen(1);
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
}