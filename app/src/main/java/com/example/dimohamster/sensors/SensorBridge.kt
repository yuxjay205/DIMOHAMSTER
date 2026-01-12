package com.example.dimohamster.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Sensor bridge for accelerometer and gyroscope data.
 * Passes sensor data to the native C++ engine for tilt controls.
 */
class SensorBridge(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "SensorBridge"
        private const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME
    }

    init {
        System.loadLibrary("dimohamster")
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var isActive = false

    // Current sensor values
    private val accelData = FloatArray(3)
    private val gyroData = FloatArray(3)

    // Listener for Kotlin-side consumption
    private var sensorListener: SensorDataListener? = null

    // JNI native method
    external fun nativeUpdateSensorData(
        accelX: Float, accelY: Float, accelZ: Float,
        gyroX: Float, gyroY: Float, gyroZ: Float
    )

    /**
     * Sensor data listener interface.
     */
    interface SensorDataListener {
        fun onAccelerometerData(x: Float, y: Float, z: Float)
        fun onGyroscopeData(x: Float, y: Float, z: Float)
    }

    /**
     * Initialize the sensor bridge.
     */
    fun init(): Boolean {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (accelerometer == null) {
            Log.w(TAG, "Accelerometer not available")
        }
        if (gyroscope == null) {
            Log.w(TAG, "Gyroscope not available")
        }

        val hasAnySensor = accelerometer != null || gyroscope != null
        if (hasAnySensor) {
            Log.i(TAG, "Sensor bridge initialized")
        } else {
            Log.e(TAG, "No motion sensors available")
        }

        return hasAnySensor
    }

    /**
     * Set a listener for sensor data events.
     */
    fun setSensorDataListener(listener: SensorDataListener?) {
        sensorListener = listener
    }

    /**
     * Start sensor updates.
     */
    fun start() {
        if (isActive) {
            Log.w(TAG, "Sensors already active")
            return
        }

        accelerometer?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY)
        }

        isActive = true
        Log.i(TAG, "Sensor updates started")
    }

    /**
     * Stop sensor updates.
     */
    fun stop() {
        if (!isActive) {
            return
        }

        sensorManager.unregisterListener(this)
        isActive = false
        Log.i(TAG, "Sensor updates stopped")
    }

    /**
     * Check if sensors are active.
     */
    fun isActive(): Boolean = isActive

    /**
     * Get current accelerometer data.
     */
    fun getAccelerometerData(): FloatArray = accelData.copyOf()

    /**
     * Get current gyroscope data.
     */
    fun getGyroscopeData(): FloatArray = gyroData.copyOf()

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelData[0] = event.values[0]
                accelData[1] = event.values[1]
                accelData[2] = event.values[2]
                sensorListener?.onAccelerometerData(accelData[0], accelData[1], accelData[2])
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroData[0] = event.values[0]
                gyroData[1] = event.values[1]
                gyroData[2] = event.values[2]
                sensorListener?.onGyroscopeData(gyroData[0], gyroData[1], gyroData[2])
            }
        }

        // Update native code with latest sensor data
        nativeUpdateSensorData(
            accelData[0], accelData[1], accelData[2],
            gyroData[0], gyroData[1], gyroData[2]
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor.name} -> $accuracy")
    }

    /**
     * Clean up resources.
     */
    fun shutdown() {
        stop()
        sensorListener = null
        Log.i(TAG, "Sensor bridge shutdown")
    }
}
