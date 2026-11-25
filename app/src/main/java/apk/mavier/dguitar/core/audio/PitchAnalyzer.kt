package apk.mavier.dguitar.core.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Monophonic fundamental-frequency detector based on the YIN algorithm.
 * Pure Kotlin (no Android dependencies) so it can be unit tested.
 */
object PitchAnalyzer {

    data class Result(val frequency: Double, val confidence: Float)

    private const val MIN_FREQUENCY = 28.0
    private const val MAX_FREQUENCY = 1300.0
    private const val SILENCE_RMS = 30.0
    private const val YIN_THRESHOLD = 0.15

    fun detect(samples: ShortArray, sampleRate: Int): Result? {
        val size = samples.size
        if (size < 256 || sampleRate <= 0) return null

        var mean = 0.0
        for (sample in samples) mean += sample
        mean /= size

        val centered = DoubleArray(size) { samples[it] - mean }
        var energy = 0.0
        for (value in centered) energy += value * value
        val rms = sqrt(energy / size)
        if (rms < SILENCE_RMS) return null

        val minLag = (sampleRate / MAX_FREQUENCY).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / MIN_FREQUENCY).toInt().coerceAtMost(size / 2)
        if (maxLag <= minLag) return null

        val difference = DoubleArray(maxLag + 2)
        for (lag in minLag..maxLag) {
            var sum = 0.0
            val limit = size - lag
            var index = 0
            while (index < limit) {
                val delta = centered[index] - centered[index + lag]
                sum += delta * delta
                index++
            }
            difference[lag] = sum
        }

        val normalized = DoubleArray(maxLag + 2)
        normalized[0] = 1.0
        var runningSum = 0.0
        for (lag in 1..maxLag) {
            runningSum += difference[lag]
            normalized[lag] = if (runningSum <= 0.0) 1.0 else difference[lag] * lag / runningSum
        }

        var tau = minLag
        while (tau <= maxLag && normalized[tau] >= YIN_THRESHOLD) tau++

        if (tau > maxLag) {
            var best = minLag
            for (lag in minLag..maxLag) {
                if (normalized[lag] < normalized[best]) best = lag
            }
            tau = best
        } else {
            while (tau + 1 <= maxLag && normalized[tau + 1] < normalized[tau]) tau++
        }

        val previous = normalized[(tau - 1).coerceAtLeast(minLag)]
        val current = normalized[tau]
        val next = normalized[(tau + 1).coerceAtMost(maxLag)]
        val denominator = previous - 2.0 * current + next
        val offset = if (abs(denominator) > 1e-9) 0.5 * (previous - next) / denominator else 0.0
        val refinedLag = tau + offset
        if (refinedLag <= 0.0) return null

        val frequency = sampleRate / refinedLag
        if (frequency < MIN_FREQUENCY || frequency > MAX_FREQUENCY) return null

        val confidence = (1.0 - current).coerceIn(0.0, 1.0)
        return Result(frequency, confidence.toFloat())
    }
}
