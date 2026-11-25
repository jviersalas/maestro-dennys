package apk.mavier.dguitar.domain.model

import kotlin.math.abs

object TunerTargetResolver {

    const val DEFAULT_HYSTERESIS_CENTS = 20.0

    fun resolve(
        frequency: Double,
        tuning: Tuning,
        selectedStringIndex: Int?,
        previousAutoIndex: Int?,
        hysteresisCents: Double = DEFAULT_HYSTERESIS_CENTS
    ): Int? {
        selectedStringIndex?.let { return it }
        if (tuning.strings.isEmpty()) return null

        var bestIndex = -1
        var bestDistance = Double.MAX_VALUE
        for (index in tuning.strings.indices) {
            val target = Pitch.frequency(tuning.strings[index].openMidi)
            val distance = abs(TunerMath.centsBetween(frequency, target))
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        if (bestIndex < 0) return null

        val previous = previousAutoIndex
        if (previous != null && previous != bestIndex && previous in tuning.strings.indices) {
            val previousTarget = Pitch.frequency(tuning.strings[previous].openMidi)
            val previousDistance = abs(TunerMath.centsBetween(frequency, previousTarget))
            if (previousDistance < bestDistance + hysteresisCents) {
                return previous
            }
        }
        return bestIndex
    }
}
