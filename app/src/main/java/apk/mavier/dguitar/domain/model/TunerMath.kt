package apk.mavier.dguitar.domain.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.round
import kotlin.math.roundToInt

object TunerMath {

    fun midiFromFrequency(frequency: Double): Double =
        if (frequency <= 0.0) 0.0 else 69.0 + 12.0 * log2(frequency / 440.0)

    fun frequencyForMidi(midi: Int): Double = Pitch.frequency(midi)

    fun centsBetween(frequency: Double, target: Double): Double =
        if (frequency <= 0.0 || target <= 0.0) 0.0 else 1200.0 * log2(frequency / target)

    fun wrappedCents(frequency: Double, target: Double): Double {
        val cents = centsBetween(frequency, target)
        return cents - 1200.0 * round(cents / 1200.0)
    }

    fun nearestMidi(frequency: Double): Int = round(midiFromFrequency(frequency)).toInt()

    fun noteName(midi: Int, useFlats: Boolean): String =
        Pitch.name(Pitch.pitchClass(midi), useFlats)

    fun formatFrequency(frequency: Double): String =
        String.format(Locale.US, "Hz %.1f", frequency)

    fun formatCents(cents: Double): String {
        val value = cents.roundToInt()
        return if (value > 0) "+$value cents" else "$value cents"
    }

    fun isInTune(cents: Double, tolerance: Double = 5.0): Boolean =
        abs(cents) <= tolerance
}
