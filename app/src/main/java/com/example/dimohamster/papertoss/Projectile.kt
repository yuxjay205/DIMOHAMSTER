package com.example.dimohamster.papertoss

/**
 * ============================================================================
 * Projectile - Paper Ball State Representation
 * ============================================================================
 *
 * Represents the current state of a paper ball projectile in the pseudo-3D
 * AR environment. The projectile has position, velocity, and a simulated
 * depth (Z-axis) that affects its visual scale.
 *
 * Coordinate System:
 * - X: Horizontal position (0 = left edge of screen)
 * - Y: Vertical position (0 = top of screen, increases downward)
 * - Z: Simulated depth (0.0 = closest to camera, 1.0 = farthest away)
 *
 * The Z-axis is simulated by:
 * 1. Shrinking the projectile's visual radius as Z increases
 * 2. Moving the projectile "upward" on screen as it goes deeper
 *
 * @param x Current X position in screen pixels
 * @param y Current Y position in screen pixels
 * @param z Simulated depth (0.0 = near camera, 1.0 = max depth)
 * @param velocityX Horizontal velocity in pixels per frame
 * @param velocityY Vertical velocity in pixels per frame (positive = downward)
 * @param velocityZ Depth velocity per frame (positive = moving away from camera)
 * @param baseRadius The original radius of the projectile at Z = 0
 * @param state The current state of the projectile
 */
data class Projectile(
    var x: Float,
    var y: Float,
    var z: Float = 0f,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
    var velocityZ: Float = 0f,
    val baseRadius: Float = DEFAULT_RADIUS,
    var state: ProjectileState = ProjectileState.IDLE
) {
    // ========================================================================
    // COMPUTED PROPERTIES
    // ========================================================================

    /**
     * The visual radius of the projectile, scaled based on depth.
     * As the projectile moves farther away (higher Z), it appears smaller.
     *
     * Scale formula: radius = baseRadius * (1.0 - z * DEPTH_SCALE_FACTOR)
     * At Z = 0.0: radius = baseRadius (100%)
     * At Z = 1.0: radius = baseRadius * 0.3 (30%)
     */
    val currentRadius: Float
        get() = baseRadius * (1f - z * DEPTH_SCALE_FACTOR)

    /**
     * Returns true if the projectile is currently in flight.
     */
    val isInFlight: Boolean
        get() = state == ProjectileState.FLYING

    /**
     * Returns true if the projectile has reached its apex (maximum depth).
     * The apex is when Z velocity becomes zero or negative.
     */
    val hasReachedApex: Boolean
        get() = velocityZ <= 0f && z > 0f

    /**
     * Returns true if the projectile has gone off-screen or completed its arc.
     */
    val isExpired: Boolean
        get() = state == ProjectileState.SUCCESS ||
                state == ProjectileState.MISSED ||
                state == ProjectileState.BOUNCED_OUT

    // ========================================================================
    // METHODS
    // ========================================================================

    /**
     * Creates a copy of this projectile with updated position.
     * Useful for immutable state updates if needed.
     */
    fun withPosition(newX: Float, newY: Float, newZ: Float): Projectile {
        return copy(x = newX, y = newY, z = newZ)
    }

    /**
     * Creates a copy of this projectile with updated velocity.
     * Useful for immutable state updates if needed.
     */
    fun withVelocity(newVX: Float, newVY: Float, newVZ: Float): Projectile {
        return copy(velocityX = newVX, velocityY = newVY, velocityZ = newVZ)
    }

    /**
     * Resets the projectile to its initial state at the given position.
     * Call this when preparing for a new throw.
     */
    fun reset(startX: Float, startY: Float) {
        x = startX
        y = startY
        z = 0f
        velocityX = 0f
        velocityY = 0f
        velocityZ = 0f
        state = ProjectileState.IDLE
    }

    companion object {
        // Default projectile radius in pixels (at Z = 0)
        const val DEFAULT_RADIUS = 40f

        // How much the projectile shrinks as it moves away
        // 0.7 means at max depth (Z=1), the projectile is 30% of original size
        const val DEPTH_SCALE_FACTOR = 0.7f
    }
}

/**
 * Enum representing the possible states of a projectile.
 */
enum class ProjectileState {
    /**
     * Projectile is ready to be thrown.
     * Waiting for user input.
     */
    IDLE,

    /**
     * Projectile is being held/dragged by the user.
     * Touch is active, awaiting release.
     */
    HELD,

    /**
     * Projectile is in flight after being thrown.
     * Physics simulation is active.
     */
    FLYING,

    /**
     * Projectile has successfully landed in the target.
     * Trigger success animation/sound.
     */
    SUCCESS,

    /**
     * Projectile missed the target completely.
     * Went off-screen or past the target depth.
     */
    MISSED,

    /**
     * Projectile hit the target but bounced out (RNG failure).
     * Close, but no points!
     */
    BOUNCED_OUT
}
