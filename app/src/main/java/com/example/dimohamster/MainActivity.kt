package com.example.dimohamster

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
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
import com.example.dimohamster.papertoss.CVTarget
import com.example.dimohamster.papertoss.PaperTossView
import com.example.dimohamster.sensors.LocationSvc
import com.example.dimohamster.sensors.SensorBridge
import com.example.dimohamster.simulation.DeviceSimulator

class MainActivity : AppCompatActivity(), GameView.OnTouchInputListener, PaperTossView.GameEventListener {

    companion object {
        private const val TAG = "MainActivity"

        // Set to true to use simulated device data instead of real hardware
        var USE_SIMULATION = false
    }

    private lateinit var gameView: GameView
    private lateinit var paperTossView: PaperTossView
    private lateinit var locationSvc: LocationSvc
    private lateinit var sensorBridge: SensorBridge
    private lateinit var cameraService: CameraService
    private lateinit var deviceSimulator: DeviceSimulator

    // Camera state
    private var isCameraActive = false

    // Paper toss game state
    private var paperTossScore = 0
    private var paperTossHighScore = 0

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

        setContentView(R.layout.activity_main)
        val cameraPreviewView = findViewById<androidx.camera.view.PreviewView>(R.id.cameraPreview)
        cameraPreviewView.implementationMode = androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
        val gameContainer = findViewById<android.widget.FrameLayout>(R.id.gameContainer)

        // Initialize GameView
        gameView = GameView(this)
        gameView.setOnTouchInputListener(this)
//        setContentView(gameView)
        gameContainer.addView(gameView)

        // Initialize PaperTossView (from XML layout)
        paperTossView = findViewById(R.id.paperTossView)
        paperTossView.setGameEventListener(this)
        configurePaperTossPhysics()

        // Initialize services
        initializeServices()

        cameraService.setPreviewView(cameraPreviewView)

        // Request permissions
        requestPermissions()

        // Turn on Camera and request camera permissions
        setCameraEnabled(true)

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

            override fun onNoseDetected(normX: Float, normY: Float) {
                NativeRenderer.onNoseDetected(normX, normY)
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

    /**
     * Get the paper toss view for direct control
     */
    fun getPaperTossView(): PaperTossView = paperTossView

    // ========================================================================
    // PAPER TOSS CONFIGURATION
    // ========================================================================

    /**
     * Configure paper toss physics parameters.
     * Adjust these values to change the game feel.
     */
    private fun configurePaperTossPhysics() {
        paperTossView.physics.apply {
            // Gravity - how fast the paper ball falls (pixels per frame^2)
            gravity = 0.5f

            // Air resistance - velocity multiplier per frame (0.99 = 1% loss)
            airResistance = 0.985f

            // Depth drag - how quickly Z velocity decays
            depthDrag = 0.98f

            // Throw power - multiplier for swipe velocity
            throwPower = 1.2f

            // Target depth - how "deep" in AR space the target is (0.0-1.0)
            targetDepth = 0.85f

            // How much upward swipes push into depth
            depthFromUpwardVelocity = 0.025f
        }

        paperTossView.probability.apply {
            // Minimum success chance (even bad throws have some hope)
            minProbability = 0.05f

            // Maximum success chance (even perfect throws can fail for tension)
            maxProbability = 0.90f

            // Bonus per consecutive hit
            streakBonus = 0.025f

            // Max streak bonus
            maxStreakBonus = 0.12f
        }

        // Debug visualization (set to false for production)
        paperTossView.showDebugOverlay = true
        paperTossView.showTrajectoryPreview = true
    }

    /**
     * Update the paper toss target from CV detection results.
     * Call this when your ML Kit / CV system detects a target object.
     *
     * @param boundingBox The detected object's bounding box in screen coordinates
     * @param depth Estimated depth (0.0 = close, 1.0 = far)
     * @param confidence Detection confidence (0.0 - 1.0)
     * @param label Object label (e.g., "trash_can", "cup")
     */
    fun updatePaperTossTarget(
        boundingBox: RectF,
        depth: Float = 0.85f,
        confidence: Float = 1.0f,
        label: String = "target"
    ) {
        val screenWidth = paperTossView.width.toFloat()
        val screenHeight = paperTossView.height.toFloat()

        // Estimate base success rate from target size and depth
        val baseRate = paperTossView.probability.estimateBaseRate(
            targetWidth = boundingBox.width(),
            targetHeight = boundingBox.height(),
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            targetDepth = depth
        ) * confidence

        val target = CVTarget(
            boundingBox = boundingBox,
            estimatedDepth = depth,
            baseSuccessRate = baseRate,
            label = label
        )

        paperTossView.setTarget(target)
    }

    /**
     * Simulates a target for testing when no CV detection is available.
     * Remove this in production and use updatePaperTossTarget() with real CV data.
     */
    fun simulatePaperTossTarget() {
        val screenWidth = paperTossView.width.toFloat()
        val screenHeight = paperTossView.height.toFloat()

        if (screenWidth == 0f || screenHeight == 0f) return

        // Simulate a trash can in upper-center of screen
        val targetWidth = screenWidth * 0.3f
        val targetHeight = screenHeight * 0.15f
        val centerX = screenWidth / 2f
        val centerY = screenHeight * 0.25f

        val boundingBox = RectF(
            centerX - targetWidth / 2f,
            centerY - targetHeight / 2f,
            centerX + targetWidth / 2f,
            centerY + targetHeight / 2f
        )

        updatePaperTossTarget(boundingBox, depth = 0.85f, confidence = 1.0f, label = "trash_can")
    }

    /**
     * Clear the paper toss target (no target visible).
     */
    fun clearPaperTossTarget() {
        paperTossView.setTarget(null)
    }

    // ========================================================================
    // PAPER TOSS GAME EVENT CALLBACKS
    // ========================================================================

    override fun onTouchStart(x: Float, y: Float) {
        // Paper ball picked up - play pickup sound here
        Log.d(TAG, "Paper toss: Touch started at ($x, $y)")
    }

    override fun onThrow(velocityX: Float, velocityY: Float, velocityZ: Float, angle: Float) {
        // Paper thrown - play whoosh sound here
        Log.d(TAG, "Paper toss: Throw! velocity=($velocityX, $velocityY, $velocityZ), angle=$angle")
    }

    override fun onSuccess(hitZone: CVTarget.HitZone, probability: Float, consecutiveHits: Int) {
        // Calculate and add score
        val basePoints = when (hitZone) {
            CVTarget.HitZone.BULLSEYE -> 100
            CVTarget.HitZone.INNER -> 75
            CVTarget.HitZone.OUTER -> 50
        }
        val streakMultiplier = 1f + (consecutiveHits - 1) * 0.1f
        val points = (basePoints * streakMultiplier).toInt()

        paperTossScore += points
        if (paperTossScore > paperTossHighScore) {
            paperTossHighScore = paperTossScore
        }

        // Play success sound, trigger haptics, update UI here
        Log.i(TAG, "Paper toss: SUCCESS! +$points (streak: $consecutiveHits, total: $paperTossScore)")
    }

    override fun onBounceOut(hitZone: CVTarget.HitZone, probability: Float) {
        // Play bounce sound here
        Log.i(TAG, "Paper toss: Bounced out! (zone: $hitZone)")
    }

    override fun onMiss() {
        // Play miss sound here
        Log.i(TAG, "Paper toss: Miss! Score: $paperTossScore")
    }

    /**
     * Get current paper toss score.
     */
    fun getPaperTossScore(): Int = paperTossScore

    /**
     * Get paper toss high score.
     */
    fun getPaperTossHighScore(): Int = paperTossHighScore

    /**
     * Reset paper toss game.
     */
    fun resetPaperTossGame() {
        paperTossScore = 0
        paperTossView.resetStats()
    }

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

        // Initialize simulated target after layout is complete
        // Replace this with your actual CV detection in production
        paperTossView.post {
            simulatePaperTossTarget()
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

    // Touch input callbacks (native touch is handled by GameView.queueEvent on GL thread)
    override fun onTouchDown(x: Float, y: Float) {
    }

    override fun onTouchMove(x: Float, y: Float, deltaX: Float, deltaY: Float) {
    }

    override fun onTouchUp(x: Float, y: Float) {
    }
}
