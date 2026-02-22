package com.example.dimohamster.papertoss

import android.graphics.RectF

/**
 * ============================================================================
 * CVTarget - Computer Vision Target Representation
 * ============================================================================
 *
 * Represents a real-world object detected by the computer vision system.
 * This could be a trash can, cup, basket, or any other target for the paper toss.
 *
 * The CV system provides:
 * - A 2D bounding box (RectF) in screen coordinates
 * - An estimated depth/scale metric indicating how far the target is
 * - A base success rate based on target size and distance
 *
 * @param boundingBox The 2D bounding box in screen coordinates (pixels)
 * @param estimatedDepth Normalized depth value (0.0 = closest, 1.0 = farthest)
 *                       This represents how "far" the target appears in the AR scene
 * @param baseSuccessRate The base probability (0.0 - 1.0) of a successful catch
 *                        based on the target's size and distance
 * @param label Optional label for the detected object type (e.g., "trash_can", "cup")
 */
data class CVTarget(
    val boundingBox: RectF,
    val estimatedDepth: Float,
    val baseSuccessRate: Float,
    val label: String = "target"
) {
    // ========================================================================
    // COMPUTED PROPERTIES
    // ========================================================================

    /**
     * The center X coordinate of the bounding box.
     * Used for bullseye calculations.
     */
    val centerX: Float
        get() = boundingBox.centerX()

    /**
     * The center Y coordinate of the bounding box.
     * Used for bullseye calculations.
     */
    val centerY: Float
        get() = boundingBox.centerY()

    /**
     * The width of the bounding box in pixels.
     */
    val width: Float
        get() = boundingBox.width()

    /**
     * The height of the bounding box in pixels.
     */
    val height: Float
        get() = boundingBox.height()

    /**
     * The "radius" of the bullseye zone (inner 30% of the target).
     * Hitting within this zone grants the maximum accuracy multiplier.
     */
    val bullseyeRadius: Float
        get() = minOf(width, height) * 0.15f  // 15% of smallest dimension

    /**
     * The "radius" of the inner zone (30-60% from center).
     * Hitting within this zone grants a moderate accuracy multiplier.
     */
    val innerZoneRadius: Float
        get() = minOf(width, height) * 0.30f  // 30% of smallest dimension

    // ========================================================================
    // COLLISION & ACCURACY METHODS
    // ========================================================================

    /**
     * Determines if a point (projectile position) is within the bounding box.
     *
     * @param x The X coordinate to test
     * @param y The Y coordinate to test
     * @return True if the point is inside the bounding box
     */
    fun containsPoint(x: Float, y: Float): Boolean {
        return boundingBox.contains(x, y)
    }

    /**
     * Calculates the distance from a point to the center of the target.
     * Used for determining hit accuracy.
     *
     * @param x The X coordinate
     * @param y The Y coordinate
     * @return The Euclidean distance to the target's center
     */
    fun distanceToCenter(x: Float, y: Float): Float {
        val dx = x - centerX
        val dy = y - centerY
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculates the accuracy multiplier based on how close the hit is to center.
     *
     * Zones:
     * - Bullseye (innermost 15%): 1.5x multiplier
     * - Inner zone (15-30%):      1.25x multiplier
     * - Outer zone (30%+):        1.0x multiplier
     *
     * @param x The X coordinate of the hit
     * @param y The Y coordinate of the hit
     * @return The accuracy multiplier (1.0 - 1.5)
     */
    fun calculateAccuracyMultiplier(x: Float, y: Float): Float {
        val distance = distanceToCenter(x, y)

        return when {
            // Dead center - bullseye!
            distance <= bullseyeRadius -> MULTIPLIER_BULLSEYE
            // Inner zone - good shot
            distance <= innerZoneRadius -> MULTIPLIER_INNER
            // Outer zone / edge hit
            else -> MULTIPLIER_OUTER
        }
    }

    /**
     * Determines the hit zone for feedback/scoring purposes.
     *
     * @param x The X coordinate of the hit
     * @param y The Y coordinate of the hit
     * @return The HitZone enum value
     */
    fun getHitZone(x: Float, y: Float): HitZone {
        val distance = distanceToCenter(x, y)

        return when {
            distance <= bullseyeRadius -> HitZone.BULLSEYE
            distance <= innerZoneRadius -> HitZone.INNER
            else -> HitZone.OUTER
        }
    }

    companion object {
        // Accuracy multipliers for different hit zones
        const val MULTIPLIER_BULLSEYE = 1.5f
        const val MULTIPLIER_INNER = 1.25f
        const val MULTIPLIER_OUTER = 1.0f
    }

    /**
     * Enum representing the different hit zones within a target.
     */
    enum class HitZone {
        BULLSEYE,  // Dead center - highest multiplier
        INNER,     // Good shot - moderate multiplier
        OUTER      // Edge hit - base multiplier
    }
}
