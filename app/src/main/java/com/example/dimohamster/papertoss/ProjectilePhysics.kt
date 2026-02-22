package com.example.dimohamster.papertoss

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ============================================================================
 * ProjectilePhysics - Isolated Physics Engine for Paper Toss
 * ============================================================================
 *
 * This class contains all the physics and math logic for the paper toss game.
 * All constants are exposed as public properties for easy tweaking.
 *
 * Physics Model:
 * - 2D screen coordinates (X, Y) with simulated depth (Z)
 * - Gravity pulls projectile downward (increases Y velocity)
 * - Air resistance/drag slows the projectile over time
 * - Z-axis simulates depth by affecting visual scale
 *
 * Usage:
 * 1. Call calculateThrowVelocity() when the user releases the throw
 * 2. Call updateProjectile() each frame to advance the physics
 * 3. Call checkCollision() when projectile reaches target depth
 */
class ProjectilePhysics {

    // ========================================================================
    // TWEAKABLE PHYSICS CONSTANTS
    // ========================================================================
    // Modify these values to adjust the "feel" of the game

    /**
     * Gravity acceleration applied per frame (pixels per frame²).
     * Higher values = projectile falls faster.
     * Typical range: 0.3 - 1.0
     */
    var gravity: Float = 0.5f

    /**
     * Air resistance / drag coefficient.
     * Applied as a multiplier to velocity each frame.
     * Value of 0.99 means 1% velocity loss per frame.
     * Higher values = less drag, farther throws.
     * Typical range: 0.95 - 0.995
     */
    var airResistance: Float = 0.985f

    /**
     * Drag coefficient specifically for the Z-axis (depth).
     * Controls how quickly the "depth momentum" decays.
     * Typical range: 0.95 - 0.99
     */
    var depthDrag: Float = 0.98f

    /**
     * Velocity multiplier applied to the initial swipe.
     * Higher values = more responsive, faster throws.
     * Typical range: 0.5 - 2.0
     */
    var throwPower: Float = 1.2f

    /**
     * How much the Y velocity affects Z velocity.
     * Higher values = upward swipes push projectile deeper faster.
     * Typical range: 0.01 - 0.05
     */
    var depthFromUpwardVelocity: Float = 0.025f

    /**
     * Maximum allowed velocity in any direction.
     * Prevents projectiles from going too fast.
     */
    var maxVelocity: Float = 50f

    /**
     * Minimum velocity threshold. Velocities below this are zeroed.
     * Prevents endless micro-movements.
     */
    var minVelocityThreshold: Float = 0.1f

    /**
     * The target depth at which collision detection occurs.
     * Projectiles reaching this depth are checked against CV targets.
     * 0.0 = near camera, 1.0 = maximum depth
     */
    var targetDepth: Float = 0.85f

    /**
     * Depth tolerance for collision detection.
     * Projectile must be within targetDepth ± tolerance to trigger collision.
     */
    var depthTolerance: Float = 0.1f

    // ========================================================================
    // SWIPE DETECTION CONSTANTS
    // ========================================================================

    /**
     * Minimum swipe distance in pixels to register a throw.
     * Prevents accidental tiny flicks.
     */
    var minSwipeDistance: Float = 50f

    /**
     * Maximum time (in milliseconds) for a valid swipe.
     * Swipes longer than this are ignored.
     */
    var maxSwipeDurationMs: Long = 500L

    // ========================================================================
    // PHYSICS CALCULATION METHODS
    // ========================================================================

    /**
     * Calculates the initial throw velocity based on a swipe gesture.
     *
     * @param startX Starting X coordinate of the swipe
     * @param startY Starting Y coordinate of the swipe
     * @param endX Ending X coordinate of the swipe
     * @param endY Ending Y coordinate of the swipe
     * @param startTimeMs Timestamp when touch began (milliseconds)
     * @param endTimeMs Timestamp when touch ended (milliseconds)
     * @return SwipeResult containing velocity components and validity
     */
    fun calculateThrowVelocity(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        startTimeMs: Long,
        endTimeMs: Long
    ): SwipeResult {
        // Calculate swipe delta
        val deltaX = endX - startX
        val deltaY = endY - startY

        // Calculate swipe distance (magnitude)
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)

        // Calculate duration
        val durationMs = endTimeMs - startTimeMs

        // Validate the swipe
        if (distance < minSwipeDistance) {
            return SwipeResult(
                isValid = false,
                reason = "Swipe too short (${distance.toInt()}px < ${minSwipeDistance.toInt()}px)"
            )
        }

        if (durationMs > maxSwipeDurationMs || durationMs <= 0) {
            return SwipeResult(
                isValid = false,
                reason = "Swipe too slow (${durationMs}ms > ${maxSwipeDurationMs}ms)"
            )
        }

        // Calculate velocity (pixels per millisecond, then scaled)
        val speed = distance / durationMs.toFloat()

        // Normalize direction and apply speed + power multiplier
        val normalizedVX = (deltaX / distance) * speed * throwPower
        val normalizedVY = (deltaY / distance) * speed * throwPower

        // Clamp to max velocity
        val velocityX = normalizedVX.coerceIn(-maxVelocity, maxVelocity)
        val velocityY = normalizedVY.coerceIn(-maxVelocity, maxVelocity)

        // Calculate Z velocity based on upward movement
        // Negative deltaY = upward swipe = positive depth velocity
        val velocityZ = if (deltaY < 0) {
            (-deltaY * depthFromUpwardVelocity * speed).coerceIn(0f, maxVelocity * 0.5f)
        } else {
            // Downward swipes don't increase depth much
            0.01f
        }

        // Calculate the angle of the throw (for UI feedback)
        val angleRadians = atan2(-deltaY, deltaX)  // Negative Y because screen Y is inverted

        return SwipeResult(
            isValid = true,
            velocityX = velocityX,
            velocityY = velocityY,
            velocityZ = velocityZ,
            speed = speed,
            distance = distance,
            angleRadians = angleRadians,
            durationMs = durationMs
        )
    }

    /**
     * Updates the projectile's position and velocity for one frame.
     * Call this 60 times per second for smooth 60fps animation.
     *
     * @param projectile The projectile to update (mutated in place)
     * @param deltaTime Time since last frame in milliseconds (typically 16.67ms for 60fps)
     */
    fun updateProjectile(projectile: Projectile, deltaTime: Float = 16.67f) {
        if (!projectile.isInFlight) return

        // Time scaling factor (normalize to 60fps baseline)
        val timeScale = deltaTime / 16.67f

        // ====================================================================
        // APPLY GRAVITY (Y-axis, downward)
        // ====================================================================
        // Gravity accelerates the projectile downward
        projectile.velocityY += gravity * timeScale

        // ====================================================================
        // APPLY AIR RESISTANCE / DRAG
        // ====================================================================
        // Drag slows down all velocity components over time
        val dragFactor = airResistance.toDouble().pow(timeScale.toDouble()).toFloat()
        projectile.velocityX *= dragFactor
        projectile.velocityY *= dragFactor

        // Depth has its own drag coefficient
        val zDragFactor = depthDrag.toDouble().pow(timeScale.toDouble()).toFloat()
        projectile.velocityZ *= zDragFactor

        // ====================================================================
        // UPDATE POSITION
        // ====================================================================
        projectile.x += projectile.velocityX * timeScale
        projectile.y += projectile.velocityY * timeScale
        projectile.z += projectile.velocityZ * timeScale

        // Clamp Z to valid range
        projectile.z = projectile.z.coerceIn(0f, 1f)

        // ====================================================================
        // ZERO OUT MICRO-VELOCITIES
        // ====================================================================
        if (kotlin.math.abs(projectile.velocityX) < minVelocityThreshold) {
            projectile.velocityX = 0f
        }
        if (kotlin.math.abs(projectile.velocityY) < minVelocityThreshold) {
            projectile.velocityY = 0f
        }
        if (kotlin.math.abs(projectile.velocityZ) < minVelocityThreshold) {
            projectile.velocityZ = 0f
        }
    }

    /**
     * Checks if the projectile is at the correct depth for collision detection.
     *
     * @param projectile The projectile to check
     * @return True if the projectile is within the collision depth range
     */
    fun isAtCollisionDepth(projectile: Projectile): Boolean {
        return projectile.z >= (targetDepth - depthTolerance) &&
               projectile.z <= (targetDepth + depthTolerance)
    }

    /**
     * Checks if the projectile has passed the target depth (missed or need to check collision).
     *
     * @param projectile The projectile to check
     * @return True if projectile has reached or passed target depth
     */
    fun hasReachedTargetDepth(projectile: Projectile): Boolean {
        return projectile.z >= targetDepth
    }

    /**
     * Checks if the projectile should be considered as having missed.
     * This happens when the projectile goes off-screen or loses all momentum.
     *
     * @param projectile The projectile to check
     * @param screenWidth Width of the screen in pixels
     * @param screenHeight Height of the screen in pixels
     * @return True if the projectile has missed (off-screen or stopped)
     */
    fun hasMissed(projectile: Projectile, screenWidth: Float, screenHeight: Float): Boolean {
        // Check if off-screen
        val offScreen = projectile.x < -projectile.currentRadius * 2 ||
                       projectile.x > screenWidth + projectile.currentRadius * 2 ||
                       projectile.y > screenHeight + projectile.currentRadius * 2

        // Check if projectile has stopped moving (lost all momentum before reaching target)
        val stoppedMoving = projectile.velocityX == 0f &&
                           projectile.velocityY == 0f &&
                           projectile.velocityZ == 0f &&
                           projectile.z < targetDepth - depthTolerance

        return offScreen || stoppedMoving
    }

    /**
     * Checks for collision between a projectile and a CV target.
     *
     * @param projectile The projectile to check
     * @param target The CV target bounding box
     * @return CollisionResult with hit status and accuracy info
     */
    fun checkCollision(projectile: Projectile, target: CVTarget): CollisionResult {
        // First, check if projectile is at the correct depth
        if (!isAtCollisionDepth(projectile)) {
            return CollisionResult(
                hit = false,
                reason = "Not at target depth (Z=${projectile.z}, target=${targetDepth})"
            )
        }

        // Check if the projectile's position intersects the target bounding box
        if (!target.containsPoint(projectile.x, projectile.y)) {
            return CollisionResult(
                hit = false,
                reason = "Outside bounding box"
            )
        }

        // We have a hit! Calculate accuracy
        val hitZone = target.getHitZone(projectile.x, projectile.y)
        val accuracyMultiplier = target.calculateAccuracyMultiplier(projectile.x, projectile.y)
        val distanceToCenter = target.distanceToCenter(projectile.x, projectile.y)

        return CollisionResult(
            hit = true,
            hitZone = hitZone,
            accuracyMultiplier = accuracyMultiplier,
            distanceToCenter = distanceToCenter
        )
    }

    /**
     * Predicts the trajectory of a projectile for preview/aiming visualization.
     * Returns a list of points along the predicted path.
     *
     * @param startX Starting X position
     * @param startY Starting Y position
     * @param velocityX Initial X velocity
     * @param velocityY Initial Y velocity
     * @param velocityZ Initial Z velocity
     * @param numPoints Number of prediction points to generate
     * @return List of TrajectoryPoint objects representing the path
     */
    fun predictTrajectory(
        startX: Float,
        startY: Float,
        velocityX: Float,
        velocityY: Float,
        velocityZ: Float,
        numPoints: Int = 20
    ): List<TrajectoryPoint> {
        val points = mutableListOf<TrajectoryPoint>()

        var x = startX
        var y = startY
        var z = 0f
        var vx = velocityX
        var vy = velocityY
        var vz = velocityZ

        for (i in 0 until numPoints) {
            // Add current point
            points.add(TrajectoryPoint(x, y, z))

            // Simulate physics for one frame (at 60fps)
            vy += gravity
            vx *= airResistance
            vy *= airResistance
            vz *= depthDrag

            x += vx
            y += vy
            z = (z + vz).coerceIn(0f, 1f)

            // Stop if we've reached target depth
            if (z >= targetDepth) break
        }

        return points
    }

    // ========================================================================
    // DATA CLASSES FOR RESULTS
    // ========================================================================

    /**
     * Result of swipe velocity calculation.
     */
    data class SwipeResult(
        val isValid: Boolean,
        val velocityX: Float = 0f,
        val velocityY: Float = 0f,
        val velocityZ: Float = 0f,
        val speed: Float = 0f,
        val distance: Float = 0f,
        val angleRadians: Float = 0f,
        val durationMs: Long = 0L,
        val reason: String = ""
    )

    /**
     * Result of collision detection.
     */
    data class CollisionResult(
        val hit: Boolean,
        val hitZone: CVTarget.HitZone = CVTarget.HitZone.OUTER,
        val accuracyMultiplier: Float = 1f,
        val distanceToCenter: Float = 0f,
        val reason: String = ""
    )

    /**
     * A point along a predicted trajectory.
     */
    data class TrajectoryPoint(
        val x: Float,
        val y: Float,
        val z: Float
    )

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Converts radians to degrees.
     */
    fun radiansToDegrees(radians: Float): Float {
        return Math.toDegrees(radians.toDouble()).toFloat()
    }

    /**
     * Calculates a unit vector in the direction of the given angle.
     */
    fun angleToVector(angleRadians: Float): Pair<Float, Float> {
        return Pair(cos(angleRadians), sin(angleRadians))
    }
}
