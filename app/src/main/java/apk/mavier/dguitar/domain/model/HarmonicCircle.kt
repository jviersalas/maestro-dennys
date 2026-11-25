package apk.mavier.dguitar.domain.model

data class HarmonicChord(
    val degree: Int,
    val rootPc: Int,
    val quality: ChordQuality
)

object HarmonicCircle {

    fun supports(scale: ScaleMode): Boolean = scale.intervals.size == 7

    fun chords(
        scale: ScaleMode,
        rootPc: Int,
        sevenths: Boolean = false
    ): List<HarmonicChord> {
        if (!supports(scale)) return emptyList()

        val intervals = scale.intervals
        val steps = if (sevenths) listOf(0, 2, 4, 6) else listOf(0, 2, 4)

        return (0 until 7).map { degreeIndex ->
            val rootInterval = intervals[degreeIndex]
            val chordIntervals = steps.map { step ->
                val index = degreeIndex + step
                val octave = index / 7
                val absolute = intervals[index % 7] + octave * 12
                ((absolute - rootInterval) % 12 + 12) % 12
            }.distinct().sorted()

            val root = ((rootPc + rootInterval) % 12 + 12) % 12
            HarmonicChord(
                degree = degreeIndex + 1,
                rootPc = root,
                quality = qualityFor(chordIntervals)
            )
        }
    }

    fun effectiveChords(config: FretboardConfig): List<HarmonicChord> {
        if (config.mode != ViewMode.SCALE) return emptyList()
        return chords(config.scale, config.rootPc, config.circleSevenths)
            .filter { it.degree in config.circleDegrees }
            .map { chord ->
                chord.copy(quality = config.circleOverrides[chord.degree] ?: chord.quality)
            }
    }

    fun qualityFor(intervals: List<Int>): ChordQuality {
        val normalized = intervals.map { ((it % 12) + 12) % 12 }.distinct().sorted()
        Chords.all.firstOrNull { quality ->
            quality.intervals.map { ((it % 12) + 12) % 12 }.distinct().sorted() == normalized
        }?.let { return it }
        return fallbackQuality(normalized)
    }

    private fun fallbackQuality(intervals: List<Int>): ChordQuality {
        val has = intervals.toSet()
        val third = when {
            has.contains(4) -> 4
            has.contains(3) -> 3
            has.contains(2) -> 2
            has.contains(5) -> 5
            else -> null
        }
        val fifth = when {
            has.contains(7) -> 7
            has.contains(6) -> 6
            has.contains(8) -> 8
            else -> null
        }
        val seventh = when {
            has.contains(11) -> 11
            has.contains(10) -> 10
            has.contains(9) -> 9
            else -> null
        }

        val thirdSymbol = when (third) {
            3 -> "m"
            2 -> "sus2"
            5 -> "sus4"
            else -> ""
        }
        val fifthSymbol = when (fifth) {
            6 -> "dim"
            8 -> "aug"
            else -> ""
        }
        val seventhSymbol = when (seventh) {
            11 -> "maj7"
            10 -> "7"
            9 -> "6"
            else -> ""
        }
        val symbol = (thirdSymbol + fifthSymbol + seventhSymbol).ifEmpty { "?" }
        val name = when {
            seventh != null -> "Séptima $symbol"
            fifth == 6 -> "Disminuido"
            fifth == 8 -> "Aumentado"
            third == 3 -> "Menor"
            third == 4 -> "Mayor"
            else -> "Acorde $symbol"
        }
        return ChordQuality(name, symbol, intervals)
    }
}
