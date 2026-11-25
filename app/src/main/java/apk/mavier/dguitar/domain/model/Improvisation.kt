package apk.mavier.dguitar.domain.model

enum class ImprovInstrument { GUITAR, BASS }

data class ImprovChord(
    val rootPc: Int,
    val quality: ChordQuality,
    val principalOnly: Boolean = false,
    val hitsPerBar: Int = DEFAULT_HITS_PER_BAR,
    val barsPerChord: Int = DEFAULT_BARS_PER_CHORD
) {
    fun label(useFlats: Boolean): String =
        Pitch.name(rootPc, useFlats) + quality.symbol

    val normalizedHitsPerBar: Int
        get() = if (hitsPerBar in HITS_OPTIONS) hitsPerBar else DEFAULT_HITS_PER_BAR

    val normalizedBarsPerChord: Int
        get() = if (barsPerChord in BARS_OPTIONS) barsPerChord else DEFAULT_BARS_PER_CHORD

    fun notes(useFlats: Boolean): List<String> =
        quality.pitchClasses(rootPc)
            .sortedBy { ((it - rootPc) % 12 + 12) % 12 }
            .map { Pitch.name(it, useFlats) }

    fun contains(pitchClass: Int): Boolean =
        pitchClass in quality.pitchClasses(rootPc)

    fun roleLabel(pitchClass: Int): String? {
        val role = quality.roleOf(rootPc, pitchClass) ?: return null
        return Improvisation.roleName(role)
    }

    companion object {
        const val DEFAULT_HITS_PER_BAR = 4
        val HITS_OPTIONS = listOf(8, 4, 2, 1)
        const val DEFAULT_BARS_PER_CHORD = 1
        val BARS_OPTIONS = listOf(1, 2, 4, 8)
    }
}

data class ImprovSection(
    val name: String,
    val chords: List<ImprovChord> = emptyList()
)

data class ImprovProgression(
    val id: String,
    val name: String,
    val sections: List<ImprovSection> = emptyList()
)

data class ImprovStep(
    val sectionIndex: Int,
    val chordIndex: Int,
    val sectionName: String,
    val chord: ImprovChord
)

data class ImprovBar(
    val globalIndex: Int,
    val barInChord: Int,
    val barsInChord: Int,
    val stepIndex: Int,
    val step: ImprovStep
) {
    val globalBar: Int get() = globalIndex + 1
}

object Improvisation {

    fun steps(sections: List<ImprovSection>): List<ImprovStep> =
        sections.flatMapIndexed { sectionIndex, section ->
            section.chords.mapIndexed { chordIndex, chord ->
                ImprovStep(sectionIndex, chordIndex, section.name, chord)
            }
        }

    fun bars(sections: List<ImprovSection>): List<ImprovBar> {
        val result = ArrayList<ImprovBar>()
        steps(sections).forEachIndexed { stepIndex, step ->
            val barsInChord = step.chord.normalizedBarsPerChord
            for (barInChord in 0 until barsInChord) {
                result.add(
                    ImprovBar(
                        globalIndex = result.size,
                        barInChord = barInChord,
                        barsInChord = barsInChord,
                        stepIndex = stepIndex,
                        step = step
                    )
                )
            }
        }
        return result
    }

    fun totalBars(sections: List<ImprovSection>): Int =
        steps(sections).sumOf { it.chord.normalizedBarsPerChord }

    fun barAt(sections: List<ImprovSection>, index: Int): ImprovBar? =
        bars(sections).getOrNull(index)

    fun ascendingChordPositions(
        rootMidi: Int,
        intervals: List<Int>,
        limit: Int,
        tuning: Tuning,
        maxFret: Int
    ): List<SequencePositions.Position> =
        ascendingChordMidi(rootMidi, intervals, limit).mapNotNull { midi ->
            SequencePositions.bestPositionForMidi(midi, tuning, maxFret)
        }

    fun ascendingChordMidi(
        rootMidi: Int,
        intervals: List<Int>,
        limit: Int
    ): List<Int> {
        if (limit <= 0) return emptyList()
        val steps = intervals
            .map { ((it % 12) + 12) % 12 }
            .distinct()
            .sorted()
        val result = ArrayList<Int>(limit)
        result.add(rootMidi)
        var octave = 0
        while (result.size < limit && octave <= 4) {
            for (step in steps) {
                if (result.size >= limit) break
                if (octave == 0 && step == 0) continue
                result.add(rootMidi + octave * 12 + step)
            }
            octave++
        }
        return result
    }

    fun roleName(relativeSemitones: Int): String {
        val value = ((relativeSemitones % 12) + 12) % 12
        return when (value) {
            0 -> "R"
            3, 4 -> "3"
            6, 7, 8 -> "5"
            9 -> "6"
            10, 11 -> "7"
            2 -> "9"
            5 -> "4"
            1 -> "b9"
            else -> "?"
        }
    }
}
