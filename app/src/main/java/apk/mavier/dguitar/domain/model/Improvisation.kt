package apk.mavier.dguitar.domain.model

data class ImprovChord(
    val rootPc: Int,
    val quality: ChordQuality,
    val principalOnly: Boolean = false,
    val hitsPerBar: Int = DEFAULT_HITS_PER_BAR
) {
    fun label(useFlats: Boolean): String =
        Pitch.name(rootPc, useFlats) + quality.symbol

    val normalizedHitsPerBar: Int
        get() = if (hitsPerBar in HITS_OPTIONS) hitsPerBar else DEFAULT_HITS_PER_BAR

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
    }
}

data class ImprovSection(
    val name: String,
    val chords: List<ImprovChord> = emptyList()
)

data class ImprovStep(
    val sectionIndex: Int,
    val chordIndex: Int,
    val sectionName: String,
    val chord: ImprovChord
)

object Improvisation {

    fun steps(sections: List<ImprovSection>): List<ImprovStep> =
        sections.flatMapIndexed { sectionIndex, section ->
            section.chords.mapIndexed { chordIndex, chord ->
                ImprovStep(sectionIndex, chordIndex, section.name, chord)
            }
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
