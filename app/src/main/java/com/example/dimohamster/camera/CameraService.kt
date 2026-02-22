package com.example.dimohamster.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.common.InputImage

/**
 * CameraService provides hardware camera access using CameraX.
 * Supports real-time frame analysis for AR/game features and photo capture.
 */
class CameraService(private val context: Context) {

    companion object {
        private const val TAG = "CameraService"
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
    }

    // Camera components
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    // Executor for camera operations
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Frame callback listener
    private var frameListener: FrameListener? = null

    // Camera state
    private var isInitialized = false
    private var isRunning = false
    private var currentLensFacing = CameraSelector.LENS_FACING_FRONT

    // Target resolution
    private var targetWidth = DEFAULT_WIDTH
    private var targetHeight = DEFAULT_HEIGHT
    private var previewView: androidx.camera.view.PreviewView? = null

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .build()

    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    /**
     * Listener interface for camera frame callbacks
     */
    interface FrameListener {
        /**
         * Called when a new camera frame is available
         * @param width Frame width in pixels
         * @param height Frame height in pixels
         * @param data YUV frame data (NV21 format)
         * @param timestamp Frame timestamp in nanoseconds
         */
        fun onFrameAvailable(width: Int, height: Int, data: ByteArray, timestamp: Long)

        fun onNoseDetected(normX: Float, normY: Float)

        /**
         * Called when a photo is captured
         * @param bitmap The captured image as a Bitmap
         */
        fun onPhotoCaptured(bitmap: Bitmap?)

        /**
         * Called when camera encounters an error
         * @param error Error message
         */
        fun onCameraError(error: String)
    }

    /**
     * Initialize the camera service
     */
    fun init() {
        if (isInitialized) {
            Log.w(TAG, "CameraService already initialized")
            return
        }

        Log.i(TAG, "Initializing CameraService")
        isInitialized = true
    }

    /**
     * Set the frame listener for receiving camera frames
     */
    fun setFrameListener(listener: FrameListener) {
        frameListener = listener
    }

    /**
     * Set target resolution for camera capture
     */
    fun setTargetResolution(width: Int, height: Int) {
        targetWidth = width
        targetHeight = height
        Log.d(TAG, "Target resolution set to ${width}x${height}")
    }

    /**
     * Check if camera permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start the camera with the specified lifecycle owner
     */
    fun startCamera(lifecycleOwner: LifecycleOwner) {
        if (!hasPermission()) {
            Log.e(TAG, "Camera permission not granted")
            frameListener?.onCameraError("Camera permission not granted")
            return
        }

        if (isRunning) {
            Log.w(TAG, "Camera already running")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner)
                isRunning = true
                Log.i(TAG, "Camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera: ${e.message}")
                frameListener?.onCameraError("Failed to start camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Bind camera use cases (image analysis and capture)
     */
    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner) {
        val cameraProvider = cameraProvider ?: return

        // Unbind any existing use cases
        cameraProvider.unbindAll()

        // Camera selector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(currentLensFacing)
            .build()

        // Image analysis for real-time frame processing
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(targetWidth, targetHeight))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }

        // Image capture for taking photos
        imageCapture = ImageCapture.Builder()
            .setTargetResolution(Size(targetWidth, targetHeight))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val preview = Preview.Builder().build()
        previewView?.let {
            preview.setSurfaceProvider(it.surfaceProvider)
        }

        try {
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer,
                imageCapture
            )
            Log.d(TAG, "Camera use cases bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases: ${e.message}")
            frameListener?.onCameraError("Failed to bind camera: ${e.message}")
        }
    }

    /**
     * Process a camera frame and notify listener
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val mediaImg = imageProxy.image
            if (mediaImg != null) {
                val img = InputImage.fromMediaImage(mediaImg, imageProxy.imageInfo.rotationDegrees)
                faceDetector.process(img)
                    .addOnSuccessListener { faces -> for (face in faces) {
                        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)

                        if (nose != null) {
                            var normX = nose.position.x / img.width.toFloat()
                            var normY = nose.position.y / img.height.toFloat()

                            if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) {
                                normX = 1.0f - normX
                            }

                            frameListener?.onNoseDetected(normX, normY)
                        }
                    }
                }.addOnCompleteListener {
                    imageProxy.close()
                }
            }

            else {
                imageProxy.close()
            }
//            val width = imageProxy.width
//            val height = imageProxy.height
//            val timestamp = imageProxy.imageInfo.timestamp
//
//            // Convert YUV_420_888 to NV21 format for native processing
//            val nv21Data = yuv420ToNv21(imageProxy)
//
//            // Notify listener
//            frameListener?.onFrameAvailable(width, height, nv21Data, timestamp)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame: ${e.message}")
            imageProxy.close()
        }
//        finally {
//            imageProxy.close()
//        }
    }

    /**
     * Convert YUV_420_888 to NV21 byte array
     */
    private fun yuv420ToNv21(imageProxy: ImageProxy): ByteArray {
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)

        // Interleave V and U for NV21 format (VU order)
        val uvPixelStride = uPlane.pixelStride
        val uvRowStride = uPlane.rowStride

        var uvIndex = ySize
        val uvHeight = imageProxy.height / 2
        val uvWidth = imageProxy.width / 2

        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vIndex = row * uvRowStride + col * uvPixelStride
                val uIndex = row * uvRowStride + col * uvPixelStride

                if (uvIndex < nv21.size - 1) {
                    vBuffer.position(vIndex)
                    nv21[uvIndex++] = vBuffer.get()
                    uBuffer.position(uIndex)
                    nv21[uvIndex++] = uBuffer.get()
                }
            }
        }

        // Reset buffer positions
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        return nv21
    }

    /**
     * Capture a photo
     */
    fun capturePhoto() {
        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "Image capture not initialized")
            frameListener?.onCameraError("Camera not ready for capture")
            return
        }

        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmap = imageProxyToBitmap(imageProxy)
                    imageProxy.close()

                    // Notify on main thread
                    ContextCompat.getMainExecutor(context).execute {
                        frameListener?.onPhotoCaptured(bitmap)
                    }
                    Log.d(TAG, "Photo captured successfully")
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}")
                    ContextCompat.getMainExecutor(context).execute {
                        frameListener?.onCameraError("Photo capture failed: ${exception.message}")
                    }
                }
            }
        )
    }

    /**
     * Convert ImageProxy to Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image to bitmap: ${e.message}")
            null
        }
    }

    /**
     * Switch between front and back camera
     */
    fun switchCamera(lifecycleOwner: LifecycleOwner) {
        currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        Log.d(TAG, "Switching to ${if (currentLensFacing == CameraSelector.LENS_FACING_BACK) "back" else "front"} camera")

        if (isRunning) {
            bindCameraUseCases(lifecycleOwner)
        }
    }

    /**
     * Get current lens facing direction
     */
    fun getCurrentLensFacing(): Int = currentLensFacing

    /**
     * Check if camera is currently running
     */
    fun isRunning(): Boolean = isRunning

    /**
     * Set camera zoom level (0.0 to 1.0)
     */
    fun setZoom(zoomRatio: Float) {
        camera?.cameraControl?.setLinearZoom(zoomRatio.coerceIn(0f, 1f))
    }

    /**
     * Enable/disable torch (flash)
     */
    fun setTorchEnabled(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun setPreviewView(view: androidx.camera.view.PreviewView) {
        previewView = view
    }

    /**
     * Stop the camera
     */
    fun stopCamera() {
        if (!isRunning) {
            Log.w(TAG, "Camera not running")
            return
        }

        cameraProvider?.unbindAll()
        isRunning = false
        Log.i(TAG, "Camera stopped")
    }

    /**
     * Shutdown the camera service and release resources
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down CameraService")

        stopCamera()
        cameraExecutor.shutdown()

        imageAnalyzer = null
        imageCapture = null
        camera = null
        cameraProvider = null
        frameListener = null

        isInitialized = false
    }
}
