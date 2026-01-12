package com.example.dimohamster

import android.Manifest
import android.content.pm.PackageManager
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
import com.example.dimohamster.core.GameView
import com.example.dimohamster.sensors.LocationSvc
import com.example.dimohamster.sensors.SensorBridge

class MainActivity : AppCompatActivity(), GameView.OnTouchInputListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var gameView: GameView
    private lateinit var locationSvc: LocationSvc
    private lateinit var sensorBridge: SensorBridge

    // Permission launcher
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
        sensorBridge.start()

        if (locationSvc.hasPermissions()) {
            locationSvc.startTracking()
        }

        Log.i(TAG, "MainActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        gameView.onPause()
        sensorBridge.stop()
        locationSvc.stopTracking()

        Log.i(TAG, "MainActivity paused")
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.shutdown()
        sensorBridge.shutdown()
        locationSvc.shutdown()

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
