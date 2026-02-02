package com.example.dimohamster.core

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10

/**
 * Custom GLSurfaceView for OpenGL ES 3.2 rendering.
 * Uses the native renderer via JNI for high-performance graphics.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    companion object {
        private const val TAG = "GameView"
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
        private const val EGL_OPENGL_ES3_BIT = 0x40
    }

    private var renderer: GameRenderer? = null
    private var touchListener: OnTouchInputListener? = null

    // Touch state for camera control
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    init {
        // Request OpenGL ES 3.2 context
        setEGLContextFactory(ContextFactory())
        setEGLConfigChooser(ConfigChooser())

        // Create and set renderer
        renderer = GameRenderer(context)
        setRenderer(renderer)

        // Render continuously for game loop
        renderMode = RENDERMODE_CONTINUOUSLY

        // Enable touch input
        isFocusable = true
        isFocusableInTouchMode = true

        Log.i(TAG, "GameView initialized with OpenGL ES 3.2")
    }

    fun setOnTouchInputListener(listener: OnTouchInputListener) {
        touchListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                // Queue native touch call on GL thread to avoid concurrent access
                queueEvent { NativeRenderer.nativeTouchDown(x, y) }
                touchListener?.onTouchDown(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = x - lastTouchX
                val deltaY = y - lastTouchY
                lastTouchX = x
                lastTouchY = y
                queueEvent { NativeRenderer.nativeTouchMove(x, y) }
                touchListener?.onTouchMove(x, y, deltaX, deltaY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                queueEvent { NativeRenderer.nativeTouchUp(x, y) }
                touchListener?.onTouchUp(x, y)
            }
        }

        return true
    }

    override fun onPause() {
        super.onPause()
        renderer?.onPause()
        Log.i(TAG, "GameView paused")
    }

    override fun onResume() {
        super.onResume()
        renderer?.onResume()
        Log.i(TAG, "GameView resumed")
    }

    fun shutdown() {
        renderer?.shutdown()
        Log.i(TAG, "GameView shutdown")
    }

    /**
     * Touch input listener interface for game input handling.
     */
    interface OnTouchInputListener {
        fun onTouchDown(x: Float, y: Float)
        fun onTouchMove(x: Float, y: Float, deltaX: Float, deltaY: Float)
        fun onTouchUp(x: Float, y: Float)
    }

    /**
     * Custom EGL context factory for OpenGL ES 3.2.
     */
    private inner class ContextFactory : EGLContextFactory {
        override fun createContext(
            egl: EGL10,
            display: EGLDisplay,
            config: EGLConfig
        ): EGLContext {
            val attribList = intArrayOf(
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL10.EGL_NONE
            )
            return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attribList)
        }

        override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
            egl.eglDestroyContext(display, context)
        }
    }

    /**
     * Custom EGL config chooser for OpenGL ES 3.2 with depth and stencil buffers.
     */
    private inner class ConfigChooser : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val configAttribs = intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_STENCIL_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL10.EGL_NONE
            )

            val numConfigs = IntArray(1)
            egl.eglChooseConfig(display, configAttribs, null, 0, numConfigs)

            val configs = arrayOfNulls<EGLConfig>(numConfigs[0])
            egl.eglChooseConfig(display, configAttribs, configs, numConfigs[0], numConfigs)

            return configs[0] ?: throw RuntimeException("No suitable EGL config found")
        }
    }

    /**
     * Native renderer wrapper implementing GLSurfaceView.Renderer.
     */
    private inner class GameRenderer(private val context: Context) : Renderer {
        private var initialized = false

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            if (!initialized) {
                NativeRenderer.nativeInit(context.assets)
                initialized = true
            }
            NativeRenderer.nativeOnSurfaceCreated()
            Log.i(TAG, "Surface created")
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            NativeRenderer.nativeOnSurfaceChanged(width, height)
            Log.i(TAG, "Surface changed: ${width}x${height}")
        }

        override fun onDrawFrame(gl: GL10?) {
            NativeRenderer.nativeOnDrawFrame()
        }

        fun onPause() {
            try {
                NativeAudio.pauseAll(true)
            } catch (_: UnsatisfiedLinkError) {
                // Audio not available (FMOD disabled)
            }
        }

        fun onResume() {
            try {
                NativeAudio.pauseAll(false)
            } catch (_: UnsatisfiedLinkError) {
                // Audio not available (FMOD disabled)
            }
        }

        fun shutdown() {
            if (initialized) {
                NativeRenderer.nativeShutdown()
                initialized = false
            }
        }
    }
}
