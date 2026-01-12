package com.example.dimohamster

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.dimohamster.camera.CameraService
import com.example.dimohamster.core.GameView
import com.example.dimohamster.core.NativeRenderer
import com.example.dimohamster.sensors.LocationSvc
import com.example.dimohamster.sensors.SensorBridge
import com.example.dimohamster.simulation.DeviceSimulator

class MainActivity : AppCompatActivity(), GameView.OnTouchInputListener {

    companion object {
        private const val TAG = "MainActivity"

        // Set to true to use simulated device data instead of real hardware
        var USE_SIMULATION = false
    }

    private lateinit var gameView: GameView
    private lateinit var locationSvc: LocationSvc
    private lateinit var sensorBridge: SensorBridge
    private lateinit var cameraService: CameraService
    private lateinit var deviceSimulator: DeviceSimulator

    // Camera state
    private var isCameraActive = false

    // Permission launcher for location
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            Log.i(TAG, "Location permissions granted")
            locationSvc.startTracking()
        } else {
            Log.w(TAG, "Location permissions denied")
        }
    }

    // Permission launcher for camera
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i(TAG, "Camera permission granted")
            startCameraIfNeeded()
        } else {
            Log.w(TAG, "Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable immersive fullscreen
        setupFullscreen()

        // Keep screen on during gameplay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize GameView
        gameView = GameView(this)
        gameView.setOnTouchInputListener(this)
        setContentView(gameView)

        // Initialize services
        initializeServices()

        // Request permissions
        requestPermissions()

        Log.i(TAG, "MainActivity created")
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    private fun initializeServices() {
        // Initialize location service
        locationSvc = LocationSvc(this)
        locationSvc.init()
        locationSvc.setLocationListener(object : LocationSvc.LocationListener {
            override fun onLocationUpdate(latitude: Double, longitude: Double, accuracy: Float, altitude: Double) {
                Log.d(TAG, "Location: $latitude, $longitude (accuracy: ${accuracy}m)")
                // Pass location to game logic here
            }

            override fun onLocationError(error: String) {
                Log.e(TAG, "Location error: $error")
            }
        })

        // Initialize sensor bridge
        sensorBridge = SensorBridge(this)
        sensorBridge.init()
        sensorBridge.setSensorDataListener(object : SensorBridge.SensorDataListener {
            override fun onAccelerometerData(x: Float, y: Float, z: Float) {
                // Accelerometer data is automatically passed to native code
            }

            override fun onGyroscopeData(x: Float, y: Float, z: Float) {
                // Gyroscope data is automatically passed to native code
            }
        })

        // Initialize camera service
        cameraService = CameraService(this)
        cameraService.init()
        cameraService.setFrameListener(object : CameraService.FrameListener {
            override fun onFrameAvailable(width: Int, height: Int, data: ByteArray, timestamp: Long) {
                // Pass camera frame to native code for processing
                NativeRenderer.updateCameraFrame(width, height, data, timestamp)
            }

            override fun onPhotoCaptured(bitmap: Bitmap?) {
                bitmap?.let {
                    Log.d(TAG, "Photo captured: ${it.width}x${it.height}")
                    // Handle captured photo
                }
            }

            override fun onCameraError(error: String) {
                Log.e(TAG, "Camera error: $error")
            }
        })

        // Initialize device simulator
        deviceSimulator = DeviceSimulator()
        deviceSimulator.init()
        setupSimulator()
    }

    /**
     * Setup device simulator with listeners
     */
    private fun setupSimulator() {
        deviceSimulator.setSensorListener(object : DeviceSimulator.SensorSimulationListener {
            override fun onSimulatedAccelerometer(x: Float, y: Float, z: Float) {
                if (USE_SIMULATION) {
                    // Pass simulated accelerometer to native code
                    NativeRenderer.updateSensorData(0, x, y, z)
                }
            }

            override fun onSimulatedGyroscope(x: Float, y: Float, z: Float) {
                if (USE_SIMULATION) {
                    // Pass simulated gyroscope to native code
                    NativeRenderer.updateSensorData(1, x, y, z)
                }
            }
        })

        deviceSimulator.setLocationListener(object : DeviceSimulator.LocationSimulationListener {
            override fun onSimulatedLocation(latitude: Double, longitude: Double, accuracy: Float, altitude: Double) {
                if (USE_SIMULATION) {
                    Log.d(TAG, "Simulated location: $latitude, $longitude")
                    // Pass simulated location to game logic
                }
            }
        })

        deviceSimulator.setCameraListener(object : DeviceSimulator.CameraSimulationListener {
            override fun onSimulatedFrame(width: Int, height: Int, data: ByteArray, timestamp: Long) {
                if (USE_SIMULATION) {
                    // Pass simulated camera frame to native code
                    NativeRenderer.updateCameraFrame(width, height, data, timestamp)
                }
            }

            override fun onSimulatedBitmap(bitmap: Bitmap) {
                // Optional: Use bitmap for UI preview
            }
        })
    }

    /**
     * Start the camera if permission is granted and camera is needed
     */
    private fun startCameraIfNeeded() {
        if (cameraService.hasPermission() && isCameraActive) {
            cameraService.startCamera(this)
        }
    }

    /**
     * Enable or disable the camera
     */
    fun setCameraEnabled(enabled: Boolean) {
        isCameraActive = enabled
        if (enabled) {
            if (cameraService.hasPermission()) {
                cameraService.startCamera(this)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            cameraService.stopCamera()
        }
    }

    /**
     * Switch between front and back camera
     */
    fun switchCamera() {
        cameraService.switchCamera(this)
    }

    /**
     * Capture a photo from the camera
     */
    fun capturePhoto() {
        cameraService.capturePhoto()
    }

    /**
     * Enable simulation mode
     */
    fun enableSimulation(
        sensorMode: DeviceSimulator.SensorSimulationMode = DeviceSimulator.SensorSimulationMode.STATIONARY,
        locationMode: DeviceSimulator.LocationSimulationMode = DeviceSimulator.LocationSimulationMode.STATIONARY,
        cameraMode: DeviceSimulator.CameraSimulationMode = DeviceSimulator.CameraSimulationMode.PATTERN
    ) {
        USE_SIMULATION = true
        deviceSimulator.setSensorMode(sensorMode)
        deviceSimulator.setLocationMode(locationMode)
        deviceSimulator.setCameraMode(cameraMode)
        deviceSimulator.start()

        // Stop real hardware when using simulation
        sensorBridge.stop()
        locationSvc.stopTracking()
        cameraService.stopCamera()

        Log.i(TAG, "Simulation mode enabled")
    }

    /**
     * Disable simulation mode and use real hardware
     */
    fun disableSimulation() {
        USE_SIMULATION = false
        deviceSimulator.stop()

        // Restart real hardware
        sensorBridge.start()
        if (locationSvc.hasPermissions()) {
            locationSvc.startTracking()
        }
        if (isCameraActive && cameraService.hasPermission()) {
            cameraService.startCamera(this)
        }

        Log.i(TAG, "Simulation mode disabled")
    }

    /**
     * Get the device simulator for direct configuration
     */
    fun getSimulator(): DeviceSimulator = deviceSimulator

    /**
     * Get the camera service for direct control
     */
    fun getCameraService(): CameraService = cameraService

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permissions already granted
            locationSvc.startTracking()
        }
    }

    override fun onResume() {
        super.onResume()
        gameView.onResume()

        if (USE_SIMULATION) {
            deviceSimulator.start()
        } else {
            sensorBridge.start()
            if (locationSvc.hasPermissions()) {
                locationSvc.startTracking()
            }
            if (isCameraActive && cameraService.hasPermission()) {
                cameraService.startCamera(this)
            }
        }

        Log.i(TAG, "MainActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        gameView.onPause()

        if (USE_SIMULATION) {
            deviceSimulator.stop()
        } else {
            sensorBridge.stop()
            locationSvc.stopTracking()
            cameraService.stopCamera()
        }

        Log.i(TAG, "MainActivity paused")
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.shutdown()
        sensorBridge.shutdown()
        locationSvc.shutdown()
        cameraService.shutdown()
        deviceSimulator.shutdown()

        Log.i(TAG, "MainActivity destroyed")
    }

    // Touch input callbacks
    override fun onTouchDown(x: Float, y: Float) {
        Log.d(TAG, "Touch down: $x, $y")
    }

    override fun onTouchMove(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        // Use touch delta for camera rotation
        // NativeRenderer.setCameraRotation can be called here
    }

    override fun onTouchUp(x: Float, y: Float) {
        Log.d(TAG, "Touch up: $x, $y")
    }
}
