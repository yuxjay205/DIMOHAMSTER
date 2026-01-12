package com.example.dimohamster.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * High-accuracy location service using Google Play Services Fused Location Provider.
 * Provides 5-10m accuracy location updates for location-based gameplay.
 */
class LocationSvc(private val context: Context) {

    companion object {
        private const val TAG = "LocationSvc"

        // Location request parameters
        private const val UPDATE_INTERVAL_MS = 1000L       // 1 second
        private const val FASTEST_INTERVAL_MS = 500L       // 500ms
        private const val MAX_WAIT_TIME_MS = 2000L         // 2 seconds batch

        // Required permissions
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationListener: LocationListener? = null
    private var isTracking = false

    // Last known location
    private var lastLocation: Location? = null

    /**
     * Location update listener interface.
     */
    interface LocationListener {
        fun onLocationUpdate(latitude: Double, longitude: Double, accuracy: Float, altitude: Double)
        fun onLocationError(error: String)
    }

    /**
     * Initialize the location service.
     */
    fun init() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        Log.i(TAG, "Location service initialized")
    }

    /**
     * Set the location update listener.
     */
    fun setLocationListener(listener: LocationListener) {
        locationListener = listener
    }

    /**
     * Check if location permissions are granted.
     */
    fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Start high-accuracy location tracking.
     */
    fun startTracking() {
        if (!hasPermissions()) {
            locationListener?.onLocationError("Location permissions not granted")
            return
        }

        if (isTracking) {
            Log.w(TAG, "Location tracking already started")
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMaxUpdateDelayMillis(MAX_WAIT_TIME_MS)
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    lastLocation = location
                    locationListener?.onLocationUpdate(
                        location.latitude,
                        location.longitude,
                        location.accuracy,
                        location.altitude
                    )
                }
            }
        }

        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isTracking = true
            Log.i(TAG, "Location tracking started (high accuracy)")
        } catch (e: SecurityException) {
            locationListener?.onLocationError("Security exception: ${e.message}")
            Log.e(TAG, "Security exception starting location updates", e)
        }
    }

    /**
     * Stop location tracking.
     */
    fun stopTracking() {
        if (!isTracking) {
            return
        }

        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        locationCallback = null
        isTracking = false
        Log.i(TAG, "Location tracking stopped")
    }

    /**
     * Get the last known location.
     */
    fun getLastLocation(): Location? = lastLocation

    /**
     * Request a single location update.
     */
    fun requestSingleUpdate(callback: (Location?) -> Unit) {
        if (!hasPermissions()) {
            callback(null)
            return
        }

        try {
            fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                lastLocation = location
                callback(location)
            }?.addOnFailureListener {
                callback(null)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting last location", e)
            callback(null)
        }
    }

    /**
     * Check if tracking is active.
     */
    fun isTracking(): Boolean = isTracking

    /**
     * Clean up resources.
     */
    fun shutdown() {
        stopTracking()
        fusedLocationClient = null
        locationListener = null
        Log.i(TAG, "Location service shutdown")
    }
}
