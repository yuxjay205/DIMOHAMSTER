package com.example.dimohamster.papertoss

import kotlin.random.Random

/**
 * ============================================================================
 * ProbabilityCalculator - Success/Failure RNG System
 * ============================================================================
 *
 * Handles the probability calculations for determining whether a paper ball
 * successfully stays in the target or bounces out.
 *
 * Success Probability Formula:
 *   P = BaseRate * AccuracyMultiplier * ItemWeight
 *
 * Where:
 * - BaseRate: Provided by the CV system based on target size/distance (0.0 - 1.0)
 *   - Large close bin: ~0.8
 *   - Medium distance: ~0.5
 *   - Small far cup: ~0.3
 *
 * - AccuracyMultiplier: Based on hit zone (1.0 - 1.5)
 *   - Bullseye (center): 1.5x
 *   - Inner zone: 1.25x
 *   - Outer zone (edge): 1.0x
 *
 * - ItemWeight: Optional modifier for different projectile types (default 1.0)
 *   - Light paper: 1.0
 *   - Heavy object: 0.8 (harder to stay in)
 *
 * The final probability is clamped to the range [MIN_PROBABILITY, MAX_PROBABILITY]
 * to ensure the game is never trivially easy or impossibly hard.
 */
class ProbabilityCalculator(
    private val random: Random = Random.Default
) {
    // ========================================================================
    // TWEAKABLE PROBABILITY CONSTANTS
    // ========================================================================

    /**
     * Minimum probability floor.
     * Even the worst throw has some chance of success.
     */
    var minProbability: Float = 0.05f

    /**
     * Maximum probability ceiling.
     * Even the best throw has some chance of failure for tension.
     */
    var maxProbability: Float = 0.95f

    /**
     * Default item weight for standard paper balls.
     */
    var defaultItemWeight: Float = 1.0f

    /**
     * Bonus probability for consecutive successful shots (streak bonus).
     * Applied as: P += streakBonus * consecutiveHits
     */
    var streakBonus: Float = 0.02f

    /**
     * Maximum streak bonus that can be applied.
     */
    var maxStreakBonus: Float = 0.10f

    // ========================================================================
    // MAIN PROBABILITY CALCULATION
    // ========================================================================

    /**
     * Calculates the final success probability for a throw.
     *
     * @param baseRate The base success rate from CV (0.0 - 1.0)
     * @param accuracyMultiplier The multiplier based on hit accuracy (1.0 - 1.5)
     * @param itemWeight Optional weight modifier for different projectiles (default 1.0)
     * @param consecutiveHits Number of consecutive successful shots (for streak bonus)
     * @return The final clamped probability (0.05 - 0.95)
     */
    fun calculateSuccessProbability(
        baseRate: Float,
        accuracyMultiplier: Float,
        itemWeight: Float = defaultItemWeight,
        consecutiveHits: Int = 0
    ): Float {
        // Core formula: P = BaseRate * AccuracyMultiplier * ItemWeight
        var probability = baseRate * accuracyMultiplier * itemWeight

        // Apply streak bonus (capped)
        val streakBonusAmount = (streakBonus * consecutiveHits).coerceAtMost(maxStreakBonus)
        probability += streakBonusAmount

        // Clamp to valid range
        return probability.coerceIn(minProbability, maxProbability)
    }

    /**
     * Determines if the throw is successful based on the calculated probability.
     * Generates a random number and compares against the probability.
     *
     * @param probability The success probability (0.0 - 1.0)
     * @return SuccessResult containing the outcome and details
     */
    fun rollForSuccess(probability: Float): SuccessResult {
        // Generate random float between 0.0 and 1.0
        val roll = random.nextFloat()

        // Success if roll is less than or equal to probability
        val success = roll <= probability

        return SuccessResult(
            success = success,
            probability = probability,
            roll = roll,
            margin = if (success) probability - roll else roll - probability
        )
    }

    /**
     * Convenience method that calculates probability and rolls in one call.
     *
     * @param baseRate The base success rate from CV
     * @param accuracyMultiplier The accuracy multiplier
     * @param itemWeight Optional item weight modifier
     * @param consecutiveHits Optional streak count
     * @return SuccessResult with full outcome details
     */
    fun calculateAndRoll(
        baseRate: Float,
        accuracyMultiplier: Float,
        itemWeight: Float = defaultItemWeight,
        consecutiveHits: Int = 0
    ): SuccessResult {
        val probability = calculateSuccessProbability(
            baseRate = baseRate,
            accuracyMultiplier = accuracyMultiplier,
            itemWeight = itemWeight,
            consecutiveHits = consecutiveHits
        )
        return rollForSuccess(probability)
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Estimates the base rate for a target based on its bounding box size
     * relative to the screen. Useful if CV doesn't provide a base rate.
     *
     * @param targetWidth Width of target bounding box
     * @param targetHeight Height of target bounding box
     * @param screenWidth Width of the screen
     * @param screenHeight Height of the screen
     * @param targetDepth Normalized depth of the target (0.0 - 1.0)
     * @return Estimated base success rate (0.1 - 0.9)
     */
    fun estimateBaseRate(
        targetWidth: Float,
        targetHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        targetDepth: Float
    ): Float {
        // Calculate target area relative to screen area
        val targetArea = targetWidth * targetHeight
        val screenArea = screenWidth * screenHeight
        val relativeSize = targetArea / screenArea

        // Size factor: larger targets = easier
        // Typical range: 0.01 (tiny) to 0.25 (huge)
        val sizeFactor = (relativeSize * 4).coerceIn(0.1f, 1.0f)

        // Depth factor: closer targets = easier
        // depth 0.0 = 1.0 factor, depth 1.0 = 0.5 factor
        val depthFactor = 1.0f - (targetDepth * 0.5f)

        // Combine factors
        val baseRate = sizeFactor * depthFactor

        // Clamp to reasonable range
        return baseRate.coerceIn(0.1f, 0.9f)
    }

    /**
     * Gets a descriptive string for the probability level.
     * Useful for UI feedback.
     */
    fun getProbabilityDescription(probability: Float): String {
        return when {
            probability >= 0.80f -> "Easy Shot"
            probability >= 0.60f -> "Good Chance"
            probability >= 0.40f -> "Fair Shot"
            probability >= 0.25f -> "Risky"
            else -> "Long Shot"
        }
    }

    /**
     * Gets a descriptive string for the hit zone.
     */
    fun getHitZoneDescription(hitZone: CVTarget.HitZone): String {
        return when (hitZone) {
            CVTarget.HitZone.BULLSEYE -> "Perfect! Dead Center!"
            CVTarget.HitZone.INNER -> "Nice! Inner Ring!"
            CVTarget.HitZone.OUTER -> "Edge Hit!"
        }
    }

    // ========================================================================
    // DATA CLASSES
    // ========================================================================

    /**
     * Result of a success/failure roll.
     */
    data class SuccessResult(
        /** Whether the throw was successful */
        val success: Boolean,
        /** The calculated probability that was used */
        val probability: Float,
        /** The random value that was rolled (0.0 - 1.0) */
        val roll: Float,
        /** How close the roll was to the threshold */
        val margin: Float
    ) {
        /**
         * Returns true if this was a "lucky" success (roll in bottom 10% of success range)
         */
        val wasLucky: Boolean
            get() = success && roll > probability * 0.9f

        /**
         * Returns true if this was an "unlucky" failure (roll in bottom 10% of fail range)
         */
        val wasUnlucky: Boolean
            get() = !success && roll < probability * 1.1f

        /**
         * Returns a description of how the roll went.
         */
        val description: String
            get() = when {
                success && margin > 0.3f -> "Solid success!"
                success && wasLucky -> "Just barely made it!"
                success -> "Success!"
                !success && wasUnlucky -> "So close! Bad luck!"
                !success && margin > 0.3f -> "Not even close..."
                else -> "Bounced out!"
            }
    }

    // ========================================================================
    // PRESET CONFIGURATIONS
    // ========================================================================

    companion object {
        /**
         * Creates a calculator configured for easy mode (more forgiving).
         */
        fun easyMode(): ProbabilityCalculator {
            return ProbabilityCalculator().apply {
                minProbability = 0.15f
                maxProbability = 0.98f
                streakBonus = 0.03f
                maxStreakBonus = 0.15f
            }
        }

        /**
         * Creates a calculator configured for hard mode (less forgiving).
         */
        fun hardMode(): ProbabilityCalculator {
            return ProbabilityCalculator().apply {
                minProbability = 0.02f
                maxProbability = 0.85f
                streakBonus = 0.01f
                maxStreakBonus = 0.05f
            }
        }

        /**
         * Creates a calculator configured for arcade mode (balanced with high tension).
         */
        fun arcadeMode(): ProbabilityCalculator {
            return ProbabilityCalculator().apply {
                minProbability = 0.05f
                maxProbability = 0.90f
                streakBonus = 0.025f
                maxStreakBonus = 0.12f
            }
        }
    }
}
