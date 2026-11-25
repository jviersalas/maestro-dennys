package apk.mavier.dguitar.domain.model

import kotlin.math.abs

object SequencePositions {

    data class Position(val stringIndex: Int, val fret: Int) {
        fun midi(tuning: Tuning): Int = tuning.strings[stringIndex].openMidi + fret
    }

    fun exactPosition(midi: Int, startFret: Int, endFret: Int, tuning: Tuning): Position? {
        var best: Position? = null
        var bestFret = Int.MAX_VALUE
        for (index in tuning.strings.indices) {
            val fret = midi - tuning.strings[index].openMidi
            if (fret in startFret..endFret && fret < bestFret) {
                bestFret = fret
                best = Position(index, fret)
            }
        }
        return best
    }

    fun closestPitchClassPosition(midi: Int, startFret: Int, endFret: Int, tuning: Tuning): Position? {
        val pitchClass = Pitch.pitchClass(midi)
        var best: Position? = null
        var bestDistance = Int.MAX_VALUE

        for (index in tuning.strings.indices) {
            val open = tuning.strings[index].openMidi
            for (fret in startFret..endFret) {
                val candidate = open + fret
                if (Pitch.pitchClass(candidate) == pitchClass) {
                    val distance = abs(candidate - midi)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = Position(index, fret)
                    }
                }
            }
        }
        return best
    }

    fun bestOccurrence(
        notes: List<Int>,
        occurrences: List<CagedPatternOccurrence>,
        tuning: Tuning
    ): CagedPatternOccurrence? {
        var best: CagedPatternOccurrence? = null
        var bestCount = -1

        for (occurrence in occurrences) {
            var count = 0
            for (midi in notes) {
                if (exactPosition(midi, occurrence.startFret, occurrence.endFret, tuning) != null) {
                    count++
                }
            }
            val bestStart = best?.startFret ?: Int.MAX_VALUE
            if (count > bestCount || (count == bestCount && occurrence.startFret < bestStart)) {
                bestCount = count
                best = occurrence
            }
        }
        return best
    }

    fun positionInOccurrence(
        midi: Int,
        occurrence: CagedPatternOccurrence,
        tuning: Tuning
    ): Position? =
        exactPosition(midi, occurrence.startFret, occurrence.endFret, tuning)
            ?: closestPitchClassPosition(midi, occurrence.startFret, occurrence.endFret, tuning)

    fun freePosition(midi: Int, tuning: Tuning, maxFret: Int): Position? {
        var best: Position? = null
        var bestFret = Int.MAX_VALUE
        for (index in tuning.strings.indices) {
            val fret = midi - tuning.strings[index].openMidi
            if (fret in 0..maxFret && fret < bestFret) {
                bestFret = fret
                best = Position(index, fret)
            }
        }
        return best
    }
}
