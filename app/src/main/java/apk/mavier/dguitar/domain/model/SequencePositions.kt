package apk.mavier.dguitar.domain.model

import kotlin.math.abs

object SequencePositions {

    private const val FIRST_POSITION_FRETS = 6

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

    fun lowestRootPosition(rootPc: Int, tuning: Tuning, maxFret: Int): Position? {
        val windowEnd = minOf(FIRST_POSITION_FRETS, maxFret)
        var best: Position? = null
        var bestMidi = Int.MAX_VALUE
        var bestString = -1
        var bestFret = Int.MAX_VALUE

        for (stringIndex in tuning.strings.indices) {
            val openMidi = tuning.strings[stringIndex].openMidi
            val fret = ((rootPc - openMidi) % 12 + 12) % 12
            if (fret > windowEnd) continue
            val midi = openMidi + fret
            val better = when {
                midi < bestMidi -> true
                midi > bestMidi -> false
                stringIndex > bestString -> true
                stringIndex < bestString -> false
                fret < bestFret -> true
                else -> false
            }
            if (better) {
                best = Position(stringIndex, fret)
                bestMidi = midi
                bestString = stringIndex
                bestFret = fret
            }
        }
        if (best != null) return best

        val maxStringIndex = tuning.strings.size - 1
        var fallback: Position? = null
        var fallbackMidi = Int.MAX_VALUE
        var fallbackScore = Int.MAX_VALUE
        for (stringIndex in tuning.strings.indices) {
            val openMidi = tuning.strings[stringIndex].openMidi
            val fret = ((rootPc - openMidi) % 12 + 12) % 12
            if (fret > maxFret) continue
            val midi = openMidi + fret
            val score = fret + (maxStringIndex - stringIndex)
            val better = when {
                midi < fallbackMidi -> true
                midi > fallbackMidi -> false
                score < fallbackScore -> true
                score > fallbackScore -> false
                else -> stringIndex > (fallback?.stringIndex ?: -1)
            }
            if (better) {
                fallback = Position(stringIndex, fret)
                fallbackMidi = midi
                fallbackScore = score
            }
        }
        return fallback
    }

    fun lowestRootPositionInOccurrence(
        rootPc: Int,
        tuning: Tuning,
        occurrence: CagedPatternOccurrence,
        maxFret: Int
    ): Position? {
        val endFret = minOf(occurrence.endFret, maxFret)
        var best: Position? = null
        var bestMidi = Int.MAX_VALUE
        var bestString = -1
        var bestFret = Int.MAX_VALUE

        for (stringIndex in tuning.strings.indices) {
            val openMidi = tuning.strings[stringIndex].openMidi
            for (fret in occurrence.startFret..endFret) {
                val midi = openMidi + fret
                if (Pitch.pitchClass(midi) != rootPc) continue
                val better = when {
                    midi < bestMidi -> true
                    midi > bestMidi -> false
                    stringIndex > bestString -> true
                    stringIndex < bestString -> false
                    fret < bestFret -> true
                    else -> false
                }
                if (better) {
                    best = Position(stringIndex, fret)
                    bestMidi = midi
                    bestString = stringIndex
                    bestFret = fret
                }
            }
        }
        return best
    }

    fun bestPositionForMidi(midi: Int, tuning: Tuning, maxFret: Int): Position? {
        val windowEnd = minOf(FIRST_POSITION_FRETS, maxFret)
        var best: Position? = null
        var bestString = -1
        var bestFret = Int.MAX_VALUE

        for (stringIndex in tuning.strings.indices) {
            val fret = midi - tuning.strings[stringIndex].openMidi
            if (fret !in 0..windowEnd) continue
            if (stringIndex > bestString || (stringIndex == bestString && fret < bestFret)) {
                bestString = stringIndex
                bestFret = fret
                best = Position(stringIndex, fret)
            }
        }
        return best ?: freePosition(midi, tuning, maxFret)
    }
}
