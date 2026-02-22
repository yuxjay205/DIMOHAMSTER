package com.example.dimohamster.papertoss

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * ============================================================================
 * PaperTossView - Main Game View with Physics and Rendering
 * ============================================================================
 *
 * A custom SurfaceView that implements the paper toss game mechanics:
 * - Touch input tracking for swipe-to-throw gestures
 * - 60fps game loop with physics simulation
 * - Pseudo-3D projectile rendering with depth scaling
 * - Collision detection with CV-provided target bounding boxes
 * - Probability-based success/failure system
 *
 * Usage:
 * 1. Add this view to your layout (overlays camera preview)
 * 2. Call setTarget() to provide the current CV-detected bounding box
 * 3. Handle game events via the GameEventListener interface
 *
 * Replace Canvas primitives with your own sprites by overriding the draw methods.
 */
class PaperTossView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "PaperTossView"

        // Target frame rate (60 FPS)
        private const val TARGET_FPS = 60
        private const val FRAME_TIME_MS = 1000L / TARGET_FPS
    }

    // ========================================================================
    // GAME COMPONENTS
    // ========================================================================

    /** Physics engine for projectile simulation */
    val physics = ProjectilePhysics()

    /** Probability calculator for success/failure rolls */
    val probability = ProbabilityCalculator()

    /** Current projectile state */
    private var projectile = Projectile(0f, 0f)

    /** Current CV-detected target (null if no target detected) */
    private var currentTarget: CVTarget? = null

    /** Game event listener for external callbacks */
    private var eventListener: GameEventListener? = null

    // ========================================================================
    // TOUCH TRACKING STATE
    // ========================================================================

    /** Is the user currently touching the screen? */
    private var isTouching = false

    /** Starting X coordinate of the current touch */
    private var touchStartX = 0f

    /** Starting Y coordinate of the current touch */
    private var touchStartY = 0f

    /** Timestamp (ms) when touch started */
    private var touchStartTimeMs = 0L

    /** Current touch X (for drag visualization) */
    private var touchCurrentX = 0f

    /** Current touch Y (for drag visualization) */
    private var touchCurrentY = 0f

    // ========================================================================
    // GAME STATE
    // ========================================================================

    /** Number of consecutive successful throws */
    private var consecutiveHits = 0

    /** Total successful throws this session */
    private var totalHits = 0

    /** Total throws attempted this session */
    private var totalThrows = 0

    /** Is the game loop running? */
    @Volatile
    private var isRunning = false

    /** The game loop thread */
    private var gameThread: GameThread? = null

    // ========================================================================
    // RENDERING PAINTS (Replace these with your own bitmap sprites)
    // ========================================================================

    /** Paint for the paper ball projectile */
    private val projectilePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        // Add shadow for depth effect
        setShadowLayer(8f, 2f, 2f, Color.argb(100, 0, 0, 0))
    }

    /** Paint for the paper ball outline */
    private val projectileOutlinePaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    /** Paint for the target bounding box (debug visualization) */
    private val targetPaint = Paint().apply {
        color = Color.argb(100, 0, 255, 0)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    /** Paint for the bullseye zone */
    private val bullseyePaint = Paint().apply {
        color = Color.argb(80, 255, 215, 0)  // Gold
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /** Paint for trajectory preview */
    private val trajectoryPaint = Paint().apply {
        color = Color.argb(150, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    /** Paint for UI text */
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    /** Paint for success/fail feedback text */
    private val feedbackPaint = Paint().apply {
        color = Color.GREEN
        textSize = 72f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
        setShadowLayer(6f, 3f, 3f, Color.BLACK)
    }

    // ========================================================================
    // FEEDBACK STATE
    // ========================================================================

    /** Message to display as feedback */
    private var feedbackMessage = ""

    /** Timestamp when feedback should disappear */
    private var feedbackExpireTime = 0L

    /** Duration to show feedback messages (ms) */
    private var feedbackDurationMs = 1500L

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    init {
        // Make the surface transparent so camera preview shows through
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
        holder.addCallback(this)

        // Enable touch input
        isFocusable = true
        isFocusableInTouchMode = true

        Log.i(TAG, "PaperTossView initialized")
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Sets the current CV-detected target for collision detection.
     * Call this whenever the CV system detects a new target position.
     *
     * @param target The detected target, or null if no target visible
     */
    fun setTarget(target: CVTarget?) {
        currentTarget = target

        // Update physics target depth to match CV target depth
        target?.let {
            physics.targetDepth = it.estimatedDepth
        }
    }

    /**
     * Convenience method to set target from raw bounding box data.
     *
     * @param left Left edge of bounding box
     * @param top Top edge of bounding box
     * @param right Right edge of bounding box
     * @param bottom Bottom edge of bounding box
     * @param depth Estimated depth (0.0 - 1.0)
     * @param baseRate Base success rate (0.0 - 1.0)
     */
    fun setTarget(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        depth: Float = 0.85f,
        baseRate: Float = 0.6f
    ) {
        setTarget(
            CVTarget(
                boundingBox = RectF(left, top, right, bottom),
                estimatedDepth = depth,
                baseSuccessRate = baseRate
            )
        )
    }

    /**
     * Sets the game event listener for receiving callbacks.
     */
    fun setGameEventListener(listener: GameEventListener) {
        eventListener = listener
    }

    /**
     * Gets the current game stats.
     */
    fun getStats(): GameStats {
        return GameStats(
            totalThrows = totalThrows,
            totalHits = totalHits,
            consecutiveHits = consecutiveHits,
            accuracy = if (totalThrows > 0) totalHits.toFloat() / totalThrows else 0f
        )
    }

    /**
     * Resets the game stats.
     */
    fun resetStats() {
        totalThrows = 0
        totalHits = 0
        consecutiveHits = 0
    }

    /**
     * Shows debug visualization of the target bounding box.
     */
    var showDebugOverlay = true

    /**
     * Shows the trajectory preview while aiming.
     */
    var showTrajectoryPreview = true

    // ========================================================================
    // SURFACE HOLDER CALLBACKS
    // ========================================================================

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "Surface created")
        startGameLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i(TAG, "Surface changed: ${width}x${height}")
        // Reset projectile to bottom center of screen
        resetProjectile()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "Surface destroyed")
        stopGameLoop()
    }

    // ========================================================================
    // GAME LOOP CONTROL
    // ========================================================================

    /**
     * Starts the game loop thread.
     */
    private fun startGameLoop() {
        if (isRunning) return

        isRunning = true
        gameThread = GameThread().also { it.start() }
        Log.i(TAG, "Game loop started")
    }

    /**
     * Stops the game loop thread.
     */
    private fun stopGameLoop() {
        isRunning = false
        gameThread?.let { thread ->
            try {
                thread.join(1000)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Game thread join interrupted", e)
            }
        }
        gameThread = null
        Log.i(TAG, "Game loop stopped")
    }

    /**
     * Resets the projectile to its starting position.
     */
    private fun resetProjectile() {
        projectile.reset(
            startX = width / 2f,
            startY = height - 150f  // Near bottom of screen
        )
    }

    // ========================================================================
    // TOUCH INPUT HANDLING
    // ========================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(event)
            MotionEvent.ACTION_MOVE -> handleTouchMove(event)
            MotionEvent.ACTION_UP -> handleTouchUp(event)
            MotionEvent.ACTION_CANCEL -> handleTouchCancel()
        }
        return true
    }

    /**
     * Handles touch down event - start tracking the swipe.
     */
    private fun handleTouchDown(event: MotionEvent) {
        // Only allow new throws when projectile is idle
        if (projectile.state != ProjectileState.IDLE) {
            // If projectile is done, reset it
            if (projectile.isExpired) {
                resetProjectile()
            }
            return
        }

        isTouching = true
        touchStartX = event.x
        touchStartY = event.y
        touchStartTimeMs = System.currentTimeMillis()
        touchCurrentX = event.x
        touchCurrentY = event.y

        // Mark projectile as being held
        projectile.state = ProjectileState.HELD
        projectile.x = event.x
        projectile.y = event.y

        eventListener?.onTouchStart(event.x, event.y)

        Log.d(TAG, "Touch down at (${event.x}, ${event.y})")
    }

    /**
     * Handles touch move event - update current position for preview.
     */
    private fun handleTouchMove(event: MotionEvent) {
        if (!isTouching) return

        touchCurrentX = event.x
        touchCurrentY = event.y

        // Optionally move the projectile with the finger (uncomment if desired)
        // projectile.x = event.x
        // projectile.y = event.y
    }

    /**
     * Handles touch up event - calculate velocity and launch.
     */
    private fun handleTouchUp(event: MotionEvent) {
        if (!isTouching) return

        isTouching = false
        val touchEndTimeMs = System.currentTimeMillis()

        // Calculate throw velocity
        val swipeResult = physics.calculateThrowVelocity(
            startX = touchStartX,
            startY = touchStartY,
            endX = event.x,
            endY = event.y,
            startTimeMs = touchStartTimeMs,
            endTimeMs = touchEndTimeMs
        )

        if (swipeResult.isValid) {
            // Valid swipe - launch the projectile!
            projectile.x = touchStartX
            projectile.y = touchStartY
            projectile.velocityX = swipeResult.velocityX
            projectile.velocityY = swipeResult.velocityY
            projectile.velocityZ = swipeResult.velocityZ
            projectile.state = ProjectileState.FLYING

            totalThrows++

            eventListener?.onThrow(
                velocityX = swipeResult.velocityX,
                velocityY = swipeResult.velocityY,
                velocityZ = swipeResult.velocityZ,
                angle = physics.radiansToDegrees(swipeResult.angleRadians)
            )

            Log.d(TAG, "Throw! velocity=(${swipeResult.velocityX}, ${swipeResult.velocityY}, ${swipeResult.velocityZ})")
        } else {
            // Invalid swipe - reset projectile
            projectile.state = ProjectileState.IDLE
            showFeedback("Swipe harder!", Color.YELLOW)

            Log.d(TAG, "Invalid swipe: ${swipeResult.reason}")
        }
    }

    /**
     * Handles touch cancel event.
     */
    private fun handleTouchCancel() {
        isTouching = false
        if (projectile.state == ProjectileState.HELD) {
            projectile.state = ProjectileState.IDLE
        }
    }

    // ========================================================================
    // GAME LOGIC UPDATE
    // ========================================================================

    /**
     * Updates game logic for one frame.
     * Called by the game loop at ~60fps.
     */
    private fun updateGame(deltaTimeMs: Float) {
        if (projectile.state != ProjectileState.FLYING) return

        // Update physics
        physics.updateProjectile(projectile, deltaTimeMs)

        // Check for collision at target depth
        currentTarget?.let { target ->
            if (physics.hasReachedTargetDepth(projectile)) {
                val collision = physics.checkCollision(projectile, target)

                if (collision.hit) {
                    // Hit detected! Roll for success
                    val result = probability.calculateAndRoll(
                        baseRate = target.baseSuccessRate,
                        accuracyMultiplier = collision.accuracyMultiplier,
                        consecutiveHits = consecutiveHits
                    )

                    if (result.success) {
                        handleSuccess(collision, result)
                    } else {
                        handleBounceOut(collision, result)
                    }
                } else {
                    // Passed target depth but missed the bounding box
                    handleMiss()
                }
            }
        }

        // Check if projectile went off-screen
        if (currentTarget == null || physics.hasMissed(projectile, width.toFloat(), height.toFloat())) {
            if (projectile.state == ProjectileState.FLYING) {
                handleMiss()
            }
        }
    }

    /**
     * Handles a successful throw (paper stayed in the bin).
     */
    private fun handleSuccess(collision: ProjectilePhysics.CollisionResult, result: ProbabilityCalculator.SuccessResult) {
        projectile.state = ProjectileState.SUCCESS
        consecutiveHits++
        totalHits++

        val zoneDesc = probability.getHitZoneDescription(collision.hitZone)
        showFeedback("$zoneDesc\n${result.description}", Color.GREEN)

        eventListener?.onSuccess(
            hitZone = collision.hitZone,
            probability = result.probability,
            consecutiveHits = consecutiveHits
        )

        Log.i(TAG, "SUCCESS! Zone=${collision.hitZone}, P=${result.probability}, Streak=$consecutiveHits")
    }

    /**
     * Handles a bounce-out (hit target but RNG failed).
     */
    private fun handleBounceOut(collision: ProjectilePhysics.CollisionResult, result: ProbabilityCalculator.SuccessResult) {
        projectile.state = ProjectileState.BOUNCED_OUT
        consecutiveHits = 0

        showFeedback("Bounced Out!\n${result.description}", Color.rgb(255, 165, 0))  // Orange

        eventListener?.onBounceOut(
            hitZone = collision.hitZone,
            probability = result.probability
        )

        Log.i(TAG, "BOUNCED OUT! Zone=${collision.hitZone}, P=${result.probability}, Roll=${result.roll}")
    }

    /**
     * Handles a complete miss.
     */
    private fun handleMiss() {
        projectile.state = ProjectileState.MISSED
        consecutiveHits = 0

        showFeedback("Miss!", Color.RED)

        eventListener?.onMiss()

        Log.i(TAG, "MISS!")
    }

    /**
     * Shows a feedback message on screen.
     */
    private fun showFeedback(message: String, color: Int) {
        feedbackMessage = message
        feedbackPaint.color = color
        feedbackExpireTime = System.currentTimeMillis() + feedbackDurationMs
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    /**
     * Renders the game to the canvas.
     * Override individual draw methods to customize graphics.
     */
    private fun renderGame(canvas: Canvas) {
        // Clear the canvas (transparent)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        // Draw target bounding box (debug)
        if (showDebugOverlay) {
            currentTarget?.let { drawTarget(canvas, it) }
        }

        // Draw trajectory preview while aiming
        if (showTrajectoryPreview && isTouching && projectile.state == ProjectileState.HELD) {
            drawTrajectoryPreview(canvas)
        }

        // Draw the projectile
        if (projectile.state != ProjectileState.IDLE || projectile.state == ProjectileState.HELD) {
            drawProjectile(canvas, projectile)
        }

        // Draw feedback message
        if (feedbackMessage.isNotEmpty() && System.currentTimeMillis() < feedbackExpireTime) {
            drawFeedback(canvas)
        }

        // Draw stats (optional)
        if (showDebugOverlay) {
            drawStats(canvas)
        }
    }

    /**
     * Draws the target bounding box and zones.
     * Replace with your own target visualization.
     */
    private fun drawTarget(canvas: Canvas, target: CVTarget) {
        // Draw outer bounding box
        canvas.drawRect(target.boundingBox, targetPaint)

        // Draw bullseye zone
        canvas.drawCircle(
            target.centerX,
            target.centerY,
            target.bullseyeRadius,
            bullseyePaint
        )

        // Draw center crosshair
        val crosshairSize = 20f
        val cx = target.centerX
        val cy = target.centerY

        canvas.drawLine(cx - crosshairSize, cy, cx + crosshairSize, cy, targetPaint)
        canvas.drawLine(cx, cy - crosshairSize, cx, cy + crosshairSize, targetPaint)
    }

    /**
     * Draws the projectile (paper ball).
     * Replace with your own sprite rendering.
     */
    private fun drawProjectile(canvas: Canvas, proj: Projectile) {
        val radius = proj.currentRadius

        // Calculate alpha based on state (fade out on success/miss)
        val alpha = when (proj.state) {
            ProjectileState.SUCCESS -> 150
            ProjectileState.BOUNCED_OUT -> 100
            ProjectileState.MISSED -> 50
            else -> 255
        }
        projectilePaint.alpha = alpha
        projectileOutlinePaint.alpha = alpha

        // Draw crumpled paper effect (simple version: multiple overlapping circles)
        // Replace this with a bitmap sprite for better visuals
        val wrinkleOffset = radius * 0.15f
        canvas.drawCircle(proj.x - wrinkleOffset, proj.y - wrinkleOffset, radius * 0.9f, projectilePaint)
        canvas.drawCircle(proj.x + wrinkleOffset, proj.y + wrinkleOffset, radius * 0.85f, projectilePaint)
        canvas.drawCircle(proj.x, proj.y, radius, projectilePaint)
        canvas.drawCircle(proj.x, proj.y, radius, projectileOutlinePaint)

        // Draw shadow (gets smaller and lighter as projectile goes deeper)
        val shadowAlpha = ((1f - proj.z) * 80).toInt()
        val shadowOffset = (1f - proj.z) * 20f
        projectilePaint.alpha = shadowAlpha
        canvas.drawCircle(proj.x + shadowOffset, proj.y + shadowOffset + radius, radius * 0.8f, projectilePaint)
    }

    /**
     * Draws the trajectory preview while the user is aiming.
     */
    private fun drawTrajectoryPreview(canvas: Canvas) {
        // Calculate predicted velocity based on current swipe
        val deltaX = touchCurrentX - touchStartX
        val deltaY = touchCurrentY - touchStartY
        val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)

        if (distance < physics.minSwipeDistance * 0.5f) return  // Too short to preview

        // Estimate velocity (simplified)
        val speed = distance / 100f  // Rough estimate
        val vx = (deltaX / distance) * speed * physics.throwPower * 10f
        val vy = (deltaY / distance) * speed * physics.throwPower * 10f
        val vz = if (deltaY < 0) (-deltaY * physics.depthFromUpwardVelocity * speed * 5f) else 0.01f

        // Get predicted trajectory points
        val points = physics.predictTrajectory(
            startX = touchStartX,
            startY = touchStartY,
            velocityX = vx,
            velocityY = vy,
            velocityZ = vz,
            numPoints = 15
        )

        if (points.size < 2) return

        // Draw trajectory path
        val path = Path()
        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }

        canvas.drawPath(path, trajectoryPaint)

        // Draw dots at trajectory points with decreasing size (depth effect)
        for (point in points) {
            val dotRadius = 5f * (1f - point.z * 0.7f)
            canvas.drawCircle(point.x, point.y, dotRadius, projectilePaint.apply { alpha = 150 })
        }
    }

    /**
     * Draws the feedback message (SUCCESS, MISS, etc.)
     */
    private fun drawFeedback(canvas: Canvas) {
        // Calculate fade out
        val timeRemaining = feedbackExpireTime - System.currentTimeMillis()
        val alpha = ((timeRemaining.toFloat() / feedbackDurationMs) * 255).toInt().coerceIn(0, 255)
        feedbackPaint.alpha = alpha

        // Draw each line of the message
        val lines = feedbackMessage.split("\n")
        val lineHeight = feedbackPaint.textSize * 1.2f
        val startY = height / 3f

        for ((index, line) in lines.withIndex()) {
            canvas.drawText(
                line,
                width / 2f,
                startY + index * lineHeight,
                feedbackPaint
            )
        }
    }

    /**
     * Draws game statistics (debug overlay).
     */
    private fun drawStats(canvas: Canvas) {
        val stats = listOf(
            "Throws: $totalThrows",
            "Hits: $totalHits",
            "Streak: $consecutiveHits",
            "Accuracy: ${if (totalThrows > 0) "%.1f%%".format(totalHits.toFloat() / totalThrows * 100) else "N/A"}"
        )

        val padding = 20f
        var y = padding + textPaint.textSize

        textPaint.textAlign = Paint.Align.LEFT
        for (stat in stats) {
            canvas.drawText(stat, padding, y, textPaint)
            y += textPaint.textSize + 10f
        }
        textPaint.textAlign = Paint.Align.CENTER
    }

    // ========================================================================
    // GAME LOOP THREAD
    // ========================================================================

    /**
     * Dedicated thread for the game loop.
     * Runs at ~60fps with fixed timestep.
     */
    private inner class GameThread : Thread() {
        override fun run() {
            var lastFrameTime = System.currentTimeMillis()

            while (isRunning) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastFrameTime).toFloat()
                lastFrameTime = currentTime

                // Update game logic
                updateGame(deltaTime)

                // Render
                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        synchronized(holder) {
                            renderGame(canvas)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Render error", e)
                } finally {
                    canvas?.let {
                        try {
                            holder.unlockCanvasAndPost(it)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error posting canvas", e)
                        }
                    }
                }

                // Frame rate limiting
                val frameTime = System.currentTimeMillis() - currentTime
                val sleepTime = FRAME_TIME_MS - frameTime
                if (sleepTime > 0) {
                    try {
                        sleep(sleepTime)
                    } catch (e: InterruptedException) {
                        // Ignore
                    }
                }
            }
        }
    }

    // ========================================================================
    // DATA CLASSES & INTERFACES
    // ========================================================================

    /**
     * Game statistics.
     */
    data class GameStats(
        val totalThrows: Int,
        val totalHits: Int,
        val consecutiveHits: Int,
        val accuracy: Float
    )

    /**
     * Listener interface for game events.
     * Implement this to respond to game events in your Activity.
     */
    interface GameEventListener {
        /** Called when the user starts touching the screen */
        fun onTouchStart(x: Float, y: Float) {}

        /** Called when the projectile is thrown */
        fun onThrow(velocityX: Float, velocityY: Float, velocityZ: Float, angle: Float) {}

        /** Called when the throw is successful */
        fun onSuccess(hitZone: CVTarget.HitZone, probability: Float, consecutiveHits: Int) {}

        /** Called when the throw hit but bounced out */
        fun onBounceOut(hitZone: CVTarget.HitZone, probability: Float) {}

        /** Called when the throw missed entirely */
        fun onMiss() {}
    }
}
