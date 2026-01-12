package com.example.dimohamster.simulation

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * DeviceSimulator provides simulated device sensor data, camera frames, and location
 * for testing and development without physical hardware.
 *
 * Useful for:
 * - Testing on emulators without sensors
 * - Debugging sensor-dependent features
 * - Creating reproducible test scenarios
 * - Demo/presentation modes
 */
class DeviceSimulator {

    companion object {
        private const val TAG = "DeviceSimulator"

        // Default simulation update rate (60 FPS)
        const val DEFAULT_UPDATE_INTERVAL_MS = 16L

        // Default camera simulation resolution
        const val DEFAULT_CAMERA_WIDTH = 640
        const val DEFAULT_CAMERA_HEIGHT = 480
    }

    // Simulation state
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())

    // Update interval
    private var updateIntervalMs = DEFAULT_UPDATE_INTERVAL_MS

    // Simulated sensor values
    private var accelerometerX = 0f
    private var accelerometerY = 0f
    private var accelerometerZ = 9.81f // Gravity

    private var gyroscopeX = 0f
    private var gyroscopeY = 0f
    private var gyroscopeZ = 0f

    // Simulated location
    private var latitude = 37.4219983  // Google HQ default
    private var longitude = -122.084
    private var altitude = 10.0
    private var locationAccuracy = 5.0f

    // Simulated camera
    private var cameraWidth = DEFAULT_CAMERA_WIDTH
    private var cameraHeight = DEFAULT_CAMERA_HEIGHT
    private var frameCounter = 0L

    // Simulation modes
    private var sensorSimulationMode = SensorSimulationMode.STATIONARY
    private var locationSimulationMode = LocationSimulationMode.STATIONARY
    private var cameraSimulationMode = CameraSimulationMode.PATTERN

    // Animation time
    private var simulationTime = 0.0

    // Listeners
    private var sensorListener: SensorSimulationListener? = null
    private var locationListener: LocationSimulationListener? = null
    private var cameraListener: CameraSimulationListener? = null

    /**
     * Sensor simulation modes
     */
    enum class SensorSimulationMode {
        STATIONARY,      // No movement, gravity only
        WALKING,         // Periodic walking motion
        RUNNING,         // Faster periodic motion
        SHAKING,         // Random shake
        ROTATING,        // Slow rotation
        CUSTOM           // User-defined values
    }

    /**
     * Location simulation modes
     */
    enum class LocationSimulationMode {
        STATIONARY,      // Stay at current position
        WALKING_PATH,    // Follow a walking path
        DRIVING_PATH,    // Follow a driving path (faster)
        RANDOM_WALK,     // Random movement
        CIRCLE,          // Move in a circle
        CUSTOM           // User-defined values
    }

    /**
     * Camera simulation modes
     */
    enum class CameraSimulationMode {
        PATTERN,         // Color pattern for testing
        NOISE,           // Random noise
        GRADIENT,        // Moving gradient
        CHECKERBOARD,    // Checkerboard pattern
        SOLID,           // Solid color
        CUSTOM           // External frame source
    }

    /**
     * Listener for simulated sensor data
     */
    interface SensorSimulationListener {
        fun onSimulatedAccelerometer(x: Float, y: Float, z: Float)
        fun onSimulatedGyroscope(x: Float, y: Float, z: Float)
    }

    /**
     * Listener for simulated location data
     */
    interface LocationSimulationListener {
        fun onSimulatedLocation(latitude: Double, longitude: Double, accuracy: Float, altitude: Double)
    }

    /**
     * Listener for simulated camera frames
     */
    interface CameraSimulationListener {
        fun onSimulatedFrame(width: Int, height: Int, data: ByteArray, timestamp: Long)
        fun onSimulatedBitmap(bitmap: Bitmap)
    }

    // Runnable for simulation updates
    private val simulationRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                simulationTime += updateIntervalMs / 1000.0
                updateSimulation()
                handler.postDelayed(this, updateIntervalMs)
            }
        }
    }

    /**
     * Initialize the simulator
     */
    fun init() {
        Log.i(TAG, "DeviceSimulator initialized")
    }

    /**
     * Set update interval in milliseconds
     */
    fun setUpdateInterval(intervalMs: Long) {
        updateIntervalMs = intervalMs.coerceAtLeast(1L)
    }

    /**
     * Set sensor simulation listener
     */
    fun setSensorListener(listener: SensorSimulationListener) {
        sensorListener = listener
    }

    /**
     * Set location simulation listener
     */
    fun setLocationListener(listener: LocationSimulationListener) {
        locationListener = listener
    }

    /**
     * Set camera simulation listener
     */
    fun setCameraListener(listener: CameraSimulationListener) {
        cameraListener = listener
    }

    /**
     * Set sensor simulation mode
     */
    fun setSensorMode(mode: SensorSimulationMode) {
        sensorSimulationMode = mode
        Log.d(TAG, "Sensor simulation mode: $mode")
    }

    /**
     * Set location simulation mode
     */
    fun setLocationMode(mode: LocationSimulationMode) {
        locationSimulationMode = mode
        Log.d(TAG, "Location simulation mode: $mode")
    }

    /**
     * Set camera simulation mode
     */
    fun setCameraMode(mode: CameraSimulationMode) {
        cameraSimulationMode = mode
        Log.d(TAG, "Camera simulation mode: $mode")
    }

    /**
     * Set camera resolution for simulation
     */
    fun setCameraResolution(width: Int, height: Int) {
        cameraWidth = width
        cameraHeight = height
    }

    /**
     * Set custom accelerometer values (for CUSTOM mode)
     */
    fun setAccelerometer(x: Float, y: Float, z: Float) {
        accelerometerX = x
        accelerometerY = y
        accelerometerZ = z
    }

    /**
     * Set custom gyroscope values (for CUSTOM mode)
     */
    fun setGyroscope(x: Float, y: Float, z: Float) {
        gyroscopeX = x
        gyroscopeY = y
        gyroscopeZ = z
    }

    /**
     * Set custom location values (for CUSTOM mode)
     */
    fun setLocation(lat: Double, lon: Double, alt: Double = 0.0, accuracy: Float = 5.0f) {
        latitude = lat
        longitude = lon
        altitude = alt
        locationAccuracy = accuracy
    }

    /**
     * Start the simulation
     */
    fun start() {
        if (isRunning) {
            Log.w(TAG, "Simulator already running")
            return
        }

        isRunning = true
        simulationTime = 0.0
        frameCounter = 0
        handler.post(simulationRunnable)
        Log.i(TAG, "Simulator started")
    }

    /**
     * Stop the simulation
     */
    fun stop() {
        if (!isRunning) {
            Log.w(TAG, "Simulator not running")
            return
        }

        isRunning = false
        handler.removeCallbacks(simulationRunnable)
        Log.i(TAG, "Simulator stopped")
    }

    /**
     * Check if simulator is running
     */
    fun isRunning(): Boolean = isRunning

    /**
     * Update all simulations
     */
    private fun updateSimulation() {
        updateSensorSimulation()
        updateLocationSimulation()
        updateCameraSimulation()
    }

    /**
     * Update sensor simulation based on current mode
     */
    private fun updateSensorSimulation() {
        when (sensorSimulationMode) {
            SensorSimulationMode.STATIONARY -> {
                accelerometerX = 0f
                accelerometerY = 0f
                accelerometerZ = 9.81f
                gyroscopeX = 0f
                gyroscopeY = 0f
                gyroscopeZ = 0f
            }
            SensorSimulationMode.WALKING -> {
                // Simulate walking motion
                val walkFreq = 2.0 // Steps per second
                accelerometerX = (sin(simulationTime * walkFreq * Math.PI * 2) * 0.5f).toFloat()
                accelerometerY = (cos(simulationTime * walkFreq * Math.PI * 2) * 1.0f).toFloat()
                accelerometerZ = 9.81f + (sin(simulationTime * walkFreq * Math.PI * 4) * 0.3f).toFloat()
                gyroscopeX = (sin(simulationTime * walkFreq * Math.PI * 2) * 0.1f).toFloat()
                gyroscopeY = 0f
                gyroscopeZ = (cos(simulationTime * walkFreq * Math.PI * 2) * 0.05f).toFloat()
            }
            SensorSimulationMode.RUNNING -> {
                // Faster motion
                val runFreq = 3.5
                accelerometerX = (sin(simulationTime * runFreq * Math.PI * 2) * 1.5f).toFloat()
                accelerometerY = (cos(simulationTime * runFreq * Math.PI * 2) * 2.5f).toFloat()
                accelerometerZ = 9.81f + (sin(simulationTime * runFreq * Math.PI * 4) * 1.0f).toFloat()
                gyroscopeX = (sin(simulationTime * runFreq * Math.PI * 2) * 0.3f).toFloat()
                gyroscopeY = (cos(simulationTime * runFreq * Math.PI) * 0.1f).toFloat()
                gyroscopeZ = (cos(simulationTime * runFreq * Math.PI * 2) * 0.2f).toFloat()
            }
            SensorSimulationMode.SHAKING -> {
                // Random shake
                accelerometerX = (Random.nextFloat() - 0.5f) * 20f
                accelerometerY = (Random.nextFloat() - 0.5f) * 20f
                accelerometerZ = 9.81f + (Random.nextFloat() - 0.5f) * 10f
                gyroscopeX = (Random.nextFloat() - 0.5f) * 5f
                gyroscopeY = (Random.nextFloat() - 0.5f) * 5f
                gyroscopeZ = (Random.nextFloat() - 0.5f) * 5f
            }
            SensorSimulationMode.ROTATING -> {
                // Slow rotation
                val rotSpeed = 0.5
                accelerometerX = (sin(simulationTime * rotSpeed) * 9.81f).toFloat()
                accelerometerY = 0f
                accelerometerZ = (cos(simulationTime * rotSpeed) * 9.81f).toFloat()
                gyroscopeX = 0f
                gyroscopeY = rotSpeed.toFloat()
                gyroscopeZ = 0f
            }
            SensorSimulationMode.CUSTOM -> {
                // Use user-set values, no modification
            }
        }

        sensorListener?.onSimulatedAccelerometer(accelerometerX, accelerometerY, accelerometerZ)
        sensorListener?.onSimulatedGyroscope(gyroscopeX, gyroscopeY, gyroscopeZ)
    }

    /**
     * Update location simulation based on current mode
     */
    private fun updateLocationSimulation() {
        when (locationSimulationMode) {
            LocationSimulationMode.STATIONARY -> {
                // No change
            }
            LocationSimulationMode.WALKING_PATH -> {
                // Move at ~1.4 m/s (walking speed)
                val speed = 0.000015 // degrees per update at ~60fps
                latitude += sin(simulationTime * 0.1) * speed
                longitude += cos(simulationTime * 0.1) * speed
                locationAccuracy = 5.0f + Random.nextFloat() * 3f
            }
            LocationSimulationMode.DRIVING_PATH -> {
                // Move at ~15 m/s (driving speed)
                val speed = 0.00015
                latitude += sin(simulationTime * 0.05) * speed
                longitude += cos(simulationTime * 0.05) * speed
                locationAccuracy = 3.0f + Random.nextFloat() * 2f
            }
            LocationSimulationMode.RANDOM_WALK -> {
                val step = 0.00001
                latitude += (Random.nextFloat() - 0.5f) * step
                longitude += (Random.nextFloat() - 0.5f) * step
                locationAccuracy = 10.0f + Random.nextFloat() * 10f
            }
            LocationSimulationMode.CIRCLE -> {
                // Move in a circle
                val radius = 0.001 // ~100m radius
                val speed = 0.5
                latitude = 37.4219983 + sin(simulationTime * speed) * radius
                longitude = -122.084 + cos(simulationTime * speed) * radius
                locationAccuracy = 5.0f
            }
            LocationSimulationMode.CUSTOM -> {
                // Use user-set values
            }
        }

        locationListener?.onSimulatedLocation(latitude, longitude, locationAccuracy, altitude)
    }

    /**
     * Update camera simulation based on current mode
     */
    private fun updateCameraSimulation() {
        frameCounter++
        val timestamp = System.nanoTime()

        val bitmap = when (cameraSimulationMode) {
            CameraSimulationMode.PATTERN -> generatePatternFrame()
            CameraSimulationMode.NOISE -> generateNoiseFrame()
            CameraSimulationMode.GRADIENT -> generateGradientFrame()
            CameraSimulationMode.CHECKERBOARD -> generateCheckerboardFrame()
            CameraSimulationMode.SOLID -> generateSolidFrame()
            CameraSimulationMode.CUSTOM -> null
        }

        bitmap?.let {
            cameraListener?.onSimulatedBitmap(it)

            // Also generate YUV data for native processing
            val yuvData = bitmapToNv21(it)
            cameraListener?.onSimulatedFrame(cameraWidth, cameraHeight, yuvData, timestamp)
        }
    }

    /**
     * Generate a test pattern frame
     */
    private fun generatePatternFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(cameraWidth, cameraHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(cameraWidth * cameraHeight)

        val offset = (simulationTime * 50).toInt()

        for (y in 0 until cameraHeight) {
            for (x in 0 until cameraWidth) {
                val r = ((x + offset) % 256)
                val g = ((y + offset) % 256)
                val b = (((x + y) / 2 + offset) % 256)
                pixels[y * cameraWidth + x] = Color.rgb(r, g, b)
            }
        }

        bitmap.setPixels(pixels, 0, cameraWidth, 0, 0, cameraWidth, cameraHeight)
        return bitmap
    }

    /**
     * Generate a noise frame
     */
    private fun generateNoiseFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(cameraWidth, cameraHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(cameraWidth * cameraHeight)

        for (i in pixels.indices) {
            val gray = Random.nextInt(256)
            pixels[i] = Color.rgb(gray, gray, gray)
        }

        bitmap.setPixels(pixels, 0, cameraWidth, 0, 0, cameraWidth, cameraHeight)
        return bitmap
    }

    /**
     * Generate a moving gradient frame
     */
    private fun generateGradientFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(cameraWidth, cameraHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(cameraWidth * cameraHeight)

        val phase = (simulationTime * 100).toInt()

        for (y in 0 until cameraHeight) {
            for (x in 0 until cameraWidth) {
                val value = ((x + y + phase) % 512)
                val intensity = if (value < 256) value else 511 - value
                pixels[y * cameraWidth + x] = Color.rgb(intensity, intensity / 2, 255 - intensity)
            }
        }

        bitmap.setPixels(pixels, 0, cameraWidth, 0, 0, cameraWidth, cameraHeight)
        return bitmap
    }

    /**
     * Generate a checkerboard frame
     */
    private fun generateCheckerboardFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(cameraWidth, cameraHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(cameraWidth * cameraHeight)

        val squareSize = 32
        val offset = ((simulationTime * 10).toInt() / squareSize) % 2

        for (y in 0 until cameraHeight) {
            for (x in 0 until cameraWidth) {
                val xSquare = x / squareSize
                val ySquare = y / squareSize
                val isWhite = ((xSquare + ySquare + offset) % 2) == 0
                pixels[y * cameraWidth + x] = if (isWhite) Color.WHITE else Color.BLACK
            }
        }

        bitmap.setPixels(pixels, 0, cameraWidth, 0, 0, cameraWidth, cameraHeight)
        return bitmap
    }

    /**
     * Generate a solid color frame
     */
    private fun generateSolidFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(cameraWidth, cameraHeight, Bitmap.Config.ARGB_8888)

        // Cycle through colors
        val hue = ((simulationTime * 30) % 360).toFloat()
        val color = Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.9f))

        bitmap.eraseColor(color)
        return bitmap
    }

    /**
     * Convert Bitmap to NV21 format
     */
    private fun bitmapToNv21(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        var yIndex = 0
        var uvIndex = ySize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Convert RGB to Y
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                nv21[yIndex++] = y.coerceIn(0, 255).toByte()

                // Sample UV at half rate
                if (j % 2 == 0 && i % 2 == 0 && uvIndex < nv21.size - 1) {
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    nv21[uvIndex++] = v.coerceIn(0, 255).toByte()
                    nv21[uvIndex++] = u.coerceIn(0, 255).toByte()
                }
            }
        }

        return nv21
    }

    /**
     * Get current simulated accelerometer values
     */
    fun getAccelerometer(): FloatArray = floatArrayOf(accelerometerX, accelerometerY, accelerometerZ)

    /**
     * Get current simulated gyroscope values
     */
    fun getGyroscope(): FloatArray = floatArrayOf(gyroscopeX, gyroscopeY, gyroscopeZ)

    /**
     * Get current simulated location
     */
    fun getLocation(): DoubleArray = doubleArrayOf(latitude, longitude, altitude)

    /**
     * Shutdown the simulator
     */
    fun shutdown() {
        stop()
        sensorListener = null
        locationListener = null
        cameraListener = null
        Log.i(TAG, "DeviceSimulator shutdown")
    }
}
