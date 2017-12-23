/**
 * Created by Fabrice Armisen (farmisen@gmail.com) on 1/3/16.
 */

package com.barcodescanner.zbar;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.TextureView;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.dm7.barcodescanner.zbar.BarcodeFormat;
import me.dm7.barcodescanner.zbar.Result;


class RCTCameraViewFinder extends TextureView implements TextureView.SurfaceTextureListener, Camera.PreviewCallback {
    private int _cameraType;
    private int _captureMode;
    private SurfaceTexture _surfaceTexture;
    private int _surfaceTextureWidth;
    private int _surfaceTextureHeight;
    private boolean _isStarting;
    private boolean _isStopping;
    private Camera _camera;
    private float mFingerSpacing;


    // concurrency lock for barcode scanner to avoid flooding the runtime
    public static volatile boolean barcodeScannerTaskLock = false;

    // reader instance for the barcode scanner
    private final ImageScanner mScanner = new ImageScanner();

    public RCTCameraViewFinder(Context context, int type) {
        super(context);
        this.setSurfaceTextureListener(this);
        this._cameraType = type;
        this.setupScanner();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        _surfaceTexture = surface;
        _surfaceTextureWidth = width;
        _surfaceTextureHeight = height;
        startCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        _surfaceTextureWidth = width;
        _surfaceTextureHeight = height;
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        _surfaceTexture = null;
        _surfaceTextureWidth = 0;
        _surfaceTextureHeight = 0;
        stopCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public double getRatio() {
        int width = RCTCamera.getInstance().getPreviewWidth(this._cameraType);
        int height = RCTCamera.getInstance().getPreviewHeight(this._cameraType);
        return ((float) width) / ((float) height);
    }

    public void setCameraType(final int type) {
        if (this._cameraType == type) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                stopPreview();
                _cameraType = type;
                startPreview();
            }
        }).start();
    }

    public void setCaptureMode(final int captureMode) {
        RCTCamera.getInstance().setCaptureMode(_cameraType, captureMode);
        this._captureMode = captureMode;
    }

    public void setCaptureQuality(String captureQuality) {
        RCTCamera.getInstance().setCaptureQuality(_cameraType, captureQuality);
    }

    public void setTorchMode(int torchMode) {
        RCTCamera.getInstance().setTorchMode(_cameraType, torchMode);
    }

    public void setFlashMode(int flashMode) {
        RCTCamera.getInstance().setFlashMode(_cameraType, flashMode);
    }

    private void startPreview() {
        if (_surfaceTexture != null) {
            startCamera();
        }
    }

    private void stopPreview() {
        if (_camera != null) {
            stopCamera();
        }
    }

    synchronized private void startCamera() {
        if (!_isStarting) {
            _isStarting = true;
            try {
                _camera = RCTCamera.getInstance().acquireCameraInstance(_cameraType);
                Camera.Parameters parameters = _camera.getParameters();

                final boolean isCaptureModeStill = (_captureMode == RCTZBarCameraModule.RCT_CAMERA_CAPTURE_MODE_STILL);
                final boolean isCaptureModeVideo = (_captureMode == RCTZBarCameraModule.RCT_CAMERA_CAPTURE_MODE_VIDEO);
                if (!isCaptureModeStill && !isCaptureModeVideo) {
                    throw new RuntimeException("Unsupported capture mode:" + _captureMode);
                }

                // Set auto-focus. Try to set to continuous picture/video, and fall back to general
                // auto if available.
                List<String> focusModes = parameters.getSupportedFocusModes();
                if (isCaptureModeStill && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (isCaptureModeVideo && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }

                // set picture size
                // defaults to max available size
                List<Camera.Size> supportedSizes;
                if (isCaptureModeStill) {
                    supportedSizes = parameters.getSupportedPictureSizes();
                } else if (isCaptureModeVideo) {
                    supportedSizes = RCTCamera.getInstance().getSupportedVideoSizes(_camera);
                } else {
                    throw new RuntimeException("Unsupported capture mode:" + _captureMode);
                }
                Camera.Size optimalPictureSize = RCTCamera.getInstance().getBestSize(
                        supportedSizes,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE
                );
                parameters.setPictureSize(optimalPictureSize.width, optimalPictureSize.height);

                _camera.setParameters(parameters);
                _camera.setPreviewTexture(_surfaceTexture);
                _camera.startPreview();
                // send previews to `onPreviewFrame`
                _camera.setPreviewCallback(this);
            } catch (NullPointerException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
                stopCamera();
            } finally {
                _isStarting = false;
            }
        }
    }

    synchronized private void stopCamera() {
        if (!_isStopping) {
            _isStopping = true;
            try {
                if (_camera != null) {
                    _camera.stopPreview();
                    // stop sending previews to `onPreviewFrame`
                    _camera.setPreviewCallback(null);
                    RCTCamera.getInstance().releaseCameraInstance(_cameraType);
                    _camera = null;
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                _isStopping = false;
            }
        }
    }

    /**
     * Parse barcodes as BarcodeFormat constants.
     *
     * Supports all iOS codes except [code39mod43, itf14]
     *
     * Additionally supports [codabar, maxicode, rss14, rssexpanded, upca, upceanextension]
     */
    private void setupScanner() {
        this.mScanner.setConfig(0, 256, 3);
        this.mScanner.setConfig(0, 257, 3);
        this.mScanner.setConfig(0, 0, 0);
        Iterator i$ = BarcodeFormat.ALL_FORMATS.iterator();

        while (i$.hasNext()) {
            BarcodeFormat format = (BarcodeFormat) i$.next();
            this.mScanner.setConfig(format.getId(), 0, 1);
        }
    }

    /**
     * Spawn a barcode reader task if
     *  - the barcode scanner is enabled (has a onBarCodeRead function)
     *  - one isn't already running
     *
     * See {Camera.PreviewCallback}
     */
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (RCTCamera.getInstance().isBarcodeScannerEnabled() && !RCTCameraViewFinder.barcodeScannerTaskLock) {
            RCTCameraViewFinder.barcodeScannerTaskLock = true;
            new ReaderAsyncTask(camera, data).execute();
        }
    }

    private class ReaderAsyncTask extends AsyncTask<Void, Void, Void> {
        private byte[] imageData;
        private final Camera camera;

        ReaderAsyncTask(Camera camera, byte[] imageData) {
            this.camera = camera;
            this.imageData = imageData;
        }

        @Override
        protected Void doInBackground(Void... ignored) {
            if (isCancelled()) {
                return null;
            }

            Camera.Size size = camera.getParameters().getPreviewSize();

            int width = size.width;
            int height = size.height;

            // rotate for zxing if orientation is portrait
            if (RCTCamera.getInstance().getActualDeviceOrientation() == 0) {
                byte[] rotated = new byte[imageData.length];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        rotated[x * height + height - y - 1] = imageData[x + y * width];
                    }
                }
                width = size.height;
                height = size.width;
                imageData = rotated;
            }

            try {
                Image barcode = new Image(width, height, "Y800");
                barcode.setData(imageData);
                int r = mScanner.scanImage(barcode);
                if (r != 0) {
                    SymbolSet syms = mScanner.getResults();
                    final Result result = new Result();
                    Iterator i$ = syms.iterator();

                    while (i$.hasNext()) {
                        Symbol sym = (Symbol) i$.next();
                        String symData;
                        if (Build.VERSION.SDK_INT >= 19) {
                            symData = new String(sym.getDataBytes(), StandardCharsets.UTF_8);
                        } else {
                            symData = sym.getData();
                        }

                        if (!TextUtils.isEmpty(symData)) {
                            result.setContents(symData);
                            result.setBarcodeFormat(BarcodeFormat.getFormatById(sym.getType()));
                            break;
                        }
                    }


                    if (result != null) {
                        ReactContext reactContext = RCTZBarCameraModule.getReactContextSingleton();
                        WritableMap event = Arguments.createMap();
                        event.putString("data", result.getContents());
                        event.putString("type", result.getBarcodeFormat().getName());
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("CameraBarCodeReadAndroid", event);

                    }

                }

            } catch (Throwable t) {
                // meh
            } finally {
                RCTCameraViewFinder.barcodeScannerTaskLock = false;
                return null;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Get the pointer ID
        Camera.Parameters params = _camera.getParameters();
        int action = event.getAction();


        if (event.getPointerCount() > 1) {
            // handle multi-touch events
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                mFingerSpacing = getFingerSpacing(event);
            } else if (action == MotionEvent.ACTION_MOVE && params.isZoomSupported()) {
                _camera.cancelAutoFocus();
                handleZoom(event, params);
            }
        } else {
            // handle single touch events
            if (action == MotionEvent.ACTION_UP) {
                handleFocus(event, params);
            }
        }
        return true;
    }

    private void handleZoom(MotionEvent event, Camera.Parameters params) {
        int maxZoom = params.getMaxZoom();
        int zoom = params.getZoom();
        float newDist = getFingerSpacing(event);
        if (newDist > mFingerSpacing) {
            //zoom in
            if (zoom < maxZoom)
                zoom++;
        } else if (newDist < mFingerSpacing) {
            //zoom out
            if (zoom > 0)
                zoom--;
        }
        mFingerSpacing = newDist;
        params.setZoom(zoom);
        _camera.setParameters(params);
    }

    /**
     * Handles setting focus to the location of the event.
     *
     * Note that this will override the focus mode on the camera to FOCUS_MODE_AUTO if available,
     * even if this was previously something else (such as FOCUS_MODE_CONTINUOUS_*; see also
     * {@link #startCamera()}. However, this makes sense - after the user has initiated any
     * specific focus intent, we shouldn't be refocusing and overriding their request!
     */
    public void handleFocus(MotionEvent event, Camera.Parameters params) {
        List<String> supportedFocusModes = params.getSupportedFocusModes();
        if (supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            // Ensure focus areas are enabled. If max num focus areas is 0, then focus area is not
            // supported, so we cannot do anything here.
            if (params.getMaxNumFocusAreas() == 0) {
                return;
            }

            // Cancel any previous focus actions.
            _camera.cancelAutoFocus();

            // Compute focus area rect.
            Camera.Area focusAreaFromMotionEvent;
            try {
                focusAreaFromMotionEvent = RCTCameraUtils.computeFocusAreaFromMotionEvent(event, _surfaceTextureWidth, _surfaceTextureHeight);
            } catch (final RuntimeException e) {
                e.printStackTrace();
                return;
            }

            // Set focus mode to auto.
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            // Set focus area.
            final ArrayList<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
            focusAreas.add(focusAreaFromMotionEvent);
            params.setFocusAreas(focusAreas);

            // Also set metering area if enabled. If max num metering areas is 0, then metering area
            // is not supported. We can usually safely omit this anyway, though.
            if (params.getMaxNumMeteringAreas() > 0) {
                params.setMeteringAreas(focusAreas);
            }

            // Set parameters before starting auto-focus.
            _camera.setParameters(params);

            // Start auto-focus now that focus area has been set. If successful, then can cancel
            // it afterwards. Wrap in try-catch to avoid crashing on merely autoFocus fails.
            try {
                _camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success) {
                            camera.cancelAutoFocus();
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** Determine the space between the first two fingers */
    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }
}
