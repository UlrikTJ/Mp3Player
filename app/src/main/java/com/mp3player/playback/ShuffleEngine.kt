package com.mp3player.playback

import com.mp3player.data.dao.SongStats
import com.mp3player.data.entity.SongEntity
import kotlin.random.Random

object ShuffleEngine {

    /**
     * Evaluates a math-based cooldown formula where 'n' is the number of candidates.
     * Rounds up using Ceiling.
     */
    fun evaluateCooldownFormula(formula: String, n: Int): Int {
        if (n <= 1) return 0
        val cleaned = formula.replace(" ", "").lowercase()
        return try {
            val result: Double = when {
                cleaned.contains("n/2") -> n.toDouble() / 2.0
                cleaned.contains("n/3") -> n.toDouble() / 3.0
                cleaned.contains("n/4") -> n.toDouble() / 4.0
                cleaned.contains("n/5") -> n.toDouble() / 5.0
                cleaned.contains("n-1") -> (n - 1).toDouble()
                cleaned.contains("log") -> {
                    val logVal = Math.log(n.toDouble())
                    if (cleaned.startsWith("3*")) 3.0 * logVal else logVal
                }
                cleaned.contains("n^2") -> (n * n).toDouble()
                else -> {
                    cleaned.toDoubleOrNull() ?: (n.toDouble() / 3.0)
                }
            }
            Math.ceil(result).toInt()
        } catch (e: Exception) {
            Math.ceil(n.toDouble() / 3.0).toInt()
        }
    }

    /**
     * Selects the next song from a list of candidates based on weighted probabilities.
     * Includes dynamic cooldown buffer (excluding recently played songs) and automatic modifiers.
     */
    fun selectNextSong(
        songs: List<SongEntity>,
        statsMap: Map<Int, SongStats>,
        history: List<Int>, // list of song IDs representing recently played history
        cooldownFormula: String = "n/3",
        useSkipPenalty: Boolean = true,
        useKeeperBonus: Boolean = true
    ): SongEntity? {
        if (songs.isEmpty()) return null
        if (songs.size == 1) return songs[0]

        // Calculate dynamic cooldown size using ceiling
        val cooldownSize = evaluateCooldownFormula(cooldownFormula, songs.size)

        // 1. Filter out recently played songs based on cooldown buffer
        val effectiveCooldown = minOf(cooldownSize, songs.size - 1)
        val recentlyPlayedIds = history.takeLast(effectiveCooldown).toSet()
        val candidates = songs.filter { it.id !in recentlyPlayedIds }

        if (candidates.isEmpty()) {
            return songs.random() // Fallback
        }

        // 2. Compute effective weights for each candidate
        val weights = candidates.map { song ->
            val stats = statsMap[song.id]
            val baseWeight = song.baseWeight

            // Auto-modifiers:
            var modifier = 1.0f

            if (stats != null) {
                // A. Skip Penalty: reduce weight based on skip rate
                if (useSkipPenalty && stats.totalPlays > 2) {
                    val skipRate = stats.skipRate
                    if (skipRate > 0.5f) {
                        modifier *= maxOf(0.1f, 1.0f - (skipRate * 0.8f))
                    }
                }

                // B. Keeper Bonus: boost weight of songs that are frequent landing points
                if (useKeeperBonus && stats.keeperCount > 0) {
                    modifier *= (1.0f + (stats.keeperCount * 0.15f))
                }
            }

            val effectiveWeight = baseWeight * modifier
            maxOf(0.05f, minOf(15.0f, effectiveWeight))
        }

        // 3. Perform Weighted Random Selection
        val sumOfWeights = weights.sum()
        if (sumOfWeights <= 0) {
            return candidates.random()
        }

        val target = Random.nextFloat() * sumOfWeights
        var cumulative = 0.0f
        
        for (i in candidates.indices) {
            cumulative += weights[i]
            if (target <= cumulative) {
                return candidates[i]
            }
        }

        return candidates.last()
    }
}
