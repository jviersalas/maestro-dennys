package apk.mavier.dguitar.domain.model

import kotlin.math.pow

object Pitch {

    private val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_NAMES = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val SOLFEGE_SHARP_NAMES = arrayOf(
        "Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si"
    )
    private val SOLFEGE_FLAT_NAMES = arrayOf(
        "Do", "Reb", "Re", "Mib", "Mi", "Fa", "Solb", "Sol", "Lab", "La", "Sib", "Si"
    )

    fun pitchClass(midi: Int): Int = ((midi % 12) + 12) % 12

    fun name(pitchClass: Int, useFlats: Boolean): String {
        val pc = ((pitchClass % 12) + 12) % 12
        return if (useFlats) FLAT_NAMES[pc] else SHARP_NAMES[pc]
    }

    fun solfegeName(pitchClass: Int, useFlats: Boolean): String {
        val pc = ((pitchClass % 12) + 12) % 12
        return if (useFlats) SOLFEGE_FLAT_NAMES[pc] else SOLFEGE_SHARP_NAMES[pc]
    }

    fun frequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)
}

data class GuitarString(val index: Int, val openMidi: Int) {
    fun noteAt(fret: Int): Int = openMidi + fret
    fun name(useFlats: Boolean): String = Pitch.name(Pitch.pitchClass(openMidi), useFlats)
}

data class Tuning(val name: String, val strings: List<GuitarString>)

object Tunings {

    private fun tuning(name: String, vararg midi: Int): Tuning =
        Tuning(name, midi.mapIndexed { index, value -> GuitarString(index, value) })

    val STANDARD = tuning("Estándar", 64, 59, 55, 50, 45, 40)
    val DROP_D = tuning("Drop D", 64, 59, 55, 50, 45, 38)
    val HALF_STEP_DOWN = tuning("Medio tono abajo", 63, 58, 54, 49, 44, 39)
    val DADGAD = tuning("DADGAD", 62, 57, 55, 50, 45, 38)
    val OPEN_G = tuning("Open G", 62, 59, 55, 50, 43, 38)
    val OPEN_E = tuning("Open E", 64, 59, 56, 52, 47, 40)

    val BASS_4 = tuning("Bajo 4 cuerdas", 43, 38, 33, 28)
    val BASS_5 = tuning("Bajo 5 cuerdas", 43, 38, 33, 28, 23)

    val guitarAll = listOf(STANDARD, DROP_D, HALF_STEP_DOWN, DADGAD, OPEN_G, OPEN_E)
    val bassAll = listOf(BASS_4, BASS_5)
    val all = guitarAll + bassAll
}

data class ScaleMode(
    val name: String,
    val intervals: List<Int>,
    val formula: String = ""
) {

    fun pitchClasses(rootPc: Int): Set<Int> =
        intervals.mapTo(HashSet()) { semitone -> ((rootPc + semitone) % 12 + 12) % 12 }

    fun degreeOf(rootPc: Int, pitchClass: Int): Int {
        val relative = ((pitchClass - rootPc) % 12 + 12) % 12
        val index = intervals.indexOf(relative)
        return if (index >= 0) index + 1 else -1
    }

    fun degreeName(degree: Int): String {
        val parts = formula.split('–', '-', '—')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.size == intervals.size && degree in 1..parts.size) {
            return parts[degree - 1]
        }
        return intervalDegreeName(intervals.getOrElse(degree - 1) { 0 })
    }

    private fun intervalDegreeName(semitone: Int): String =
        when (((semitone % 12) + 12) % 12) {
            0 -> "1"
            1 -> "b2"
            2 -> "2"
            3 -> "b3"
            4 -> "3"
            5 -> "4"
            6 -> "b5"
            7 -> "5"
            8 -> "b6"
            9 -> "6"
            10 -> "b7"
            11 -> "7"
            else -> "1"
        }

    /**
     * Example exercise used by the app: play the scale up, then back down
     * without repeating the highest note (e.g. C-D-E-F-G-A-B-A-G-F-E-D-C).
     * Values are semitone offsets from the root.
     */
    val exampleSequence: List<Int>
        get() = intervals + intervals.asReversed().drop(1)

    fun octaveSequence(rootPc: Int, tuning: Tuning, maxFret: Int): List<Int> {
        val lowest = SequencePositions.lowestRootPosition(rootPc, tuning, maxFret)
            ?: return emptyList()
        val lowMidi = lowest.midi(tuning)
        val highMidi = lowMidi + 12
        val scalePcs = pitchClasses(rootPc)
        val ascending = ArrayList<Int>(13)
        for (midi in lowMidi..highMidi) {
            if (Pitch.pitchClass(midi) in scalePcs) ascending.add(midi)
        }
        return ascending + ascending.asReversed().drop(1)
    }

    fun segmentSequence(
        rootPc: Int,
        tuning: Tuning,
        occurrence: CagedPatternOccurrence,
        maxFret: Int
    ): List<SequencePositions.Position> {
        val lowestRoot = SequencePositions.lowestRootPositionInOccurrence(
            rootPc,
            tuning,
            occurrence,
            maxFret
        ) ?: return emptyList()
        val lowMidi = lowestRoot.midi(tuning)
        val highMidi = lowMidi + 12
        val scalePcs = pitchClasses(rootPc)
        val endFret = minOf(occurrence.endFret, maxFret)
        val byMidi = sortedMapOf<Int, SequencePositions.Position>()

        for (stringIndex in tuning.strings.indices) {
            val openMidi = tuning.strings[stringIndex].openMidi
            for (fret in occurrence.startFret..endFret) {
                val midi = openMidi + fret
                if (midi !in lowMidi..highMidi) continue
                if (Pitch.pitchClass(midi) !in scalePcs) continue
                val candidate = SequencePositions.Position(stringIndex, fret)
                val current = byMidi[midi]
                if (current == null ||
                    stringIndex > current.stringIndex ||
                    (stringIndex == current.stringIndex && fret < current.fret)
                ) {
                    byMidi[midi] = candidate
                }
            }
        }

        val ascending = byMidi.values.toList()
        if (ascending.isEmpty()) return emptyList()
        return ascending + ascending.asReversed().drop(1)
    }
}

object Scales {

    val PENTATONIC_MINOR = ScaleMode("Pentatónica menor", listOf(0, 3, 5, 7, 10), "1–b3–4–5–b7")
    val PENTATONIC_MAJOR = ScaleMode("Pentatónica mayor", listOf(0, 2, 4, 7, 9), "1–2–3–5–6")
    val BLUES_MINOR = ScaleMode("Blues menor", listOf(0, 3, 5, 6, 7, 10), "1–b3–4–b5–5–b7")
    val BLUES_MAJOR = ScaleMode("Blues mayor", listOf(0, 2, 3, 4, 7, 9), "1–2–b3–3–5–6")
    val PENTATONIC_MINOR_6 = ScaleMode("Pentatónica menor 6", listOf(0, 3, 5, 7, 9), "1–b3–4–5–6")
    val PENTATONIC_MINOR_9 = ScaleMode("Pentatónica menor 9", listOf(0, 2, 3, 7, 10), "1–2–b3–5–b7")
    val PENTATONIC_DOMINANT = ScaleMode("Pentatónica dominante", listOf(0, 2, 4, 7, 10), "1–2–3–5–b7")
    val PENTATONIC_PHRYGIAN = ScaleMode("Pentatónica frigia", listOf(0, 1, 5, 7, 10), "1–b2–4–5–b7")

    val MAJOR = ScaleMode("Mayor (Jónica)", listOf(0, 2, 4, 5, 7, 9, 11), "1–2–3–4–5–6–7")
    val NATURAL_MINOR = ScaleMode("Menor natural (Eólica)", listOf(0, 2, 3, 5, 7, 8, 10), "1–2–b3–4–5–b6–b7")
    val HARMONIC_MINOR = ScaleMode("Menor armónica", listOf(0, 2, 3, 5, 7, 8, 11), "1–2–b3–4–5–b6–7")
    val MELODIC_MINOR = ScaleMode("Menor melódica", listOf(0, 2, 3, 5, 7, 9, 11), "1–2–b3–4–5–6–7")
    val HARMONIC_MAJOR = ScaleMode("Mayor armónica", listOf(0, 2, 4, 5, 7, 8, 11), "1–2–3–4–5–b6–7")
    val MELODIC_MINOR_B6 = ScaleMode("Menor melódica b6", listOf(0, 2, 3, 5, 7, 8, 11), "1–2–b3–4–5–b6–7")

    val IONIAN = ScaleMode("Jónico", listOf(0, 2, 4, 5, 7, 9, 11), "1–2–3–4–5–6–7")
    val DORIAN = ScaleMode("Dórico", listOf(0, 2, 3, 5, 7, 9, 10), "1–2–b3–4–5–6–b7")
    val PHRYGIAN = ScaleMode("Frigio", listOf(0, 1, 3, 5, 7, 8, 10), "1–b2–b3–4–5–b6–b7")
    val LYDIAN = ScaleMode("Lidio", listOf(0, 2, 4, 6, 7, 9, 11), "1–2–3–#4–5–6–7")
    val MIXOLYDIAN = ScaleMode("Mixolidio", listOf(0, 2, 4, 5, 7, 9, 10), "1–2–3–4–5–6–b7")
    val AEOLIAN = ScaleMode("Eólico", listOf(0, 2, 3, 5, 7, 8, 10), "1–2–b3–4–5–b6–b7")
    val LOCRIAN = ScaleMode("Locrio", listOf(0, 1, 3, 5, 6, 8, 10), "1–b2–b3–4–b5–b6–b7")

    val BEBOP_DOMINANT = ScaleMode("Bebop dominante", listOf(0, 2, 4, 5, 7, 9, 10, 11), "1–2–3–4–5–6–b7–7")
    val BEBOP_MAJOR = ScaleMode("Bebop mayor", listOf(0, 2, 4, 5, 7, 8, 9, 11), "1–2–3–4–5–#5–6–7")
    val BEBOP_MINOR = ScaleMode("Bebop menor", listOf(0, 2, 3, 5, 7, 9, 10, 11), "1–2–b3–4–5–6–b7–7")
    val CHROMATIC = ScaleMode(
        "Cromática",
        listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
        "1–b2–2–b3–3–4–b5–5–b6–6–b7–7"
    )
    val WHOLE_TONE = ScaleMode("Tonos enteros", listOf(0, 2, 4, 6, 8, 10), "1–2–3–#4–#5–b7")
    val DIMINISHED_HALF_WHOLE = ScaleMode(
        "Disminuida (semitono-tono)",
        listOf(0, 1, 3, 5, 6, 8, 9, 11),
        "1–b2–b3–4–b5–b6–6–7"
    )
    val DIMINISHED_WHOLE_HALF = ScaleMode(
        "Disminuida (tono-semitono)",
        listOf(0, 2, 3, 5, 6, 8, 9, 11),
        "1–2–b3–4–b5–b6–6–7"
    )
    val ALTERED = ScaleMode("Alterada (Superlocria)", listOf(0, 1, 3, 4, 6, 8, 10), "1–b2–b3–b4–b5–b6–b7")
    val LYDIAN_DOMINANT = ScaleMode("Lidio dominante", listOf(0, 2, 4, 6, 7, 9, 10), "1–2–3–#4–5–6–b7")

    val PHRYGIAN_DOMINANT = ScaleMode("Frigia dominante (Española)", listOf(0, 1, 4, 5, 7, 8, 10), "1–b2–3–4–5–b6–b7")
    val DOUBLE_HARMONIC = ScaleMode("Doble armónica (Bizantina)", listOf(0, 1, 4, 5, 7, 8, 11), "1–b2–3–4–5–b6–7")
    val HUNGARIAN_MINOR = ScaleMode("Húngara (Gitana)", listOf(0, 2, 3, 6, 7, 8, 11), "1–2–b3–#4–5–b6–7")
    val NEAPOLITAN_MINOR = ScaleMode("Napolitana menor", listOf(0, 1, 3, 5, 7, 8, 11), "1–b2–b3–4–5–b6–7")
    val ORIENTAL = ScaleMode("Oriental", listOf(0, 1, 4, 5, 6, 8, 10), "1–b2–3–4–b5–b6–b7")
    val PERSIAN = ScaleMode("Persa", listOf(0, 1, 4, 5, 6, 8, 11), "1–b2–3–4–b5–b6–7")
    val ROMANIAN_MINOR = ScaleMode("Rumana (Menor húngara)", listOf(0, 2, 3, 6, 7, 9, 10), "1–2–b3–#4–5–6–b7")
    val PELOG = ScaleMode("Pelog", listOf(0, 1, 3, 7, 10), "1–b2–b3–5–b7")
    val HINDU = ScaleMode("Hindú", listOf(0, 2, 4, 5, 7, 8, 10), "1–2–3–4–5–b6–b7")
    val VOODOO = ScaleMode("Voodoo", listOf(0, 3, 5, 7, 9), "1–b3–4–5–6")
    val ENIGMATIC = ScaleMode("Enigmática", listOf(0, 1, 4, 6, 8, 10, 11), "1–b2–3–#4–#5–#6–7")
    val GYPSY_MINOR = ScaleMode("Gitana menor", listOf(0, 2, 3, 6, 7, 8, 11), "1–2–b3–#4–5–b6–7")
    val IONIAN_SHARP_5 = ScaleMode("Jónica #5", listOf(0, 2, 4, 5, 8, 9, 11), "1–2–3–4–#5–6–7")
    val DORIAN_FLAT_2 = ScaleMode("Dórica b2", listOf(0, 1, 3, 5, 7, 9, 10), "1–b2–b3–4–5–6–b7")
    val MIXOLYDIAN_FLAT_6 = ScaleMode("Mixolidia b6", listOf(0, 2, 4, 5, 7, 8, 10), "1–2–3–4–5–b6–b7")
    val LYDIAN_SHARP_2 = ScaleMode("Lidio #2", listOf(0, 3, 4, 6, 7, 9, 11), "1–#2–3–#4–5–6–7")
    val LOCRIAN_SHARP_2 = ScaleMode("Locria #2", listOf(0, 2, 3, 5, 6, 8, 10), "1–2–b3–4–b5–b6–b7")
    val AUGMENTED_MAJOR = ScaleMode("Mayor aumentada", listOf(0, 2, 4, 5, 8, 9, 11), "1–2–3–4–#5–6–7")
    val MELODIC_MINOR_FLAT_5 = ScaleMode("Menor melódica b5", listOf(0, 2, 3, 5, 6, 9, 11), "1–2–b3–4–b5–6–7")
    val BLUES_MAJOR_FLAT_3 = ScaleMode("Blues mayor b3", listOf(0, 2, 3, 4, 7, 9, 10), "1–2–b3–3–5–6–b7")

    val LOCRIAN_N6 = ScaleMode("Locrio ♮6", listOf(0, 1, 3, 5, 6, 9, 10), "1–b2–b3–4–b5–6–b7")

    val all = listOf(
        PENTATONIC_MINOR,
        PENTATONIC_MAJOR,
        BLUES_MINOR,
        BLUES_MAJOR,
        PENTATONIC_MINOR_6,
        PENTATONIC_MINOR_9,
        PENTATONIC_DOMINANT,
        PENTATONIC_PHRYGIAN,
        MAJOR,
        NATURAL_MINOR,
        HARMONIC_MINOR,
        MELODIC_MINOR,
        HARMONIC_MAJOR,
        MELODIC_MINOR_B6,
        IONIAN,
        DORIAN,
        PHRYGIAN,
        LYDIAN,
        MIXOLYDIAN,
        AEOLIAN,
        LOCRIAN,
        BEBOP_DOMINANT,
        BEBOP_MAJOR,
        BEBOP_MINOR,
        CHROMATIC,
        WHOLE_TONE,
        DIMINISHED_HALF_WHOLE,
        DIMINISHED_WHOLE_HALF,
        ALTERED,
        LYDIAN_DOMINANT,
        PHRYGIAN_DOMINANT,
        DOUBLE_HARMONIC,
        HUNGARIAN_MINOR,
        NEAPOLITAN_MINOR,
        ORIENTAL,
        PERSIAN,
        ROMANIAN_MINOR,
        PELOG,
        HINDU,
        VOODOO,
        ENIGMATIC,
        GYPSY_MINOR,
        IONIAN_SHARP_5,
        DORIAN_FLAT_2,
        MIXOLYDIAN_FLAT_6,
        LYDIAN_SHARP_2,
        LOCRIAN_SHARP_2,
        AUGMENTED_MAJOR,
        MELODIC_MINOR_FLAT_5,
        BLUES_MAJOR_FLAT_3,
        LOCRIAN_N6
    )

    val default = PENTATONIC_MINOR
}

data class ChordQuality(val name: String, val symbol: String, val intervals: List<Int>) {

    fun pitchClasses(rootPc: Int): Set<Int> =
        intervals.mapTo(HashSet()) { semitone -> ((rootPc + semitone) % 12 + 12) % 12 }

    fun roleOf(rootPc: Int, pitchClass: Int): Int? {
        val relative = ((pitchClass - rootPc) % 12 + 12) % 12
        val found = intervals.any { ((it % 12) + 12) % 12 == relative }
        return if (found) relative else null
    }
}

object Chords {

    val MAJOR = ChordQuality("Mayor", "", listOf(0, 4, 7))
    val MINOR = ChordQuality("Menor", "m", listOf(0, 3, 7))
    val POWER = ChordQuality("5 (power)", "5", listOf(0, 7))
    val DOMINANT_7 = ChordQuality("Séptima", "7", listOf(0, 4, 7, 10))
    val MAJOR_7 = ChordQuality("Maj7", "maj7", listOf(0, 4, 7, 11))
    val MINOR_7 = ChordQuality("m7", "m7", listOf(0, 3, 7, 10))
    val DIMINISHED = ChordQuality("Disminuido", "dim", listOf(0, 3, 6))
    val DIMINISHED_7 = ChordQuality("Disminuido 7", "dim7", listOf(0, 3, 6, 9))
    val HALF_DIMINISHED = ChordQuality("Semidisminuido", "m7b5", listOf(0, 3, 6, 10))
    val AUGMENTED = ChordQuality("Aumentado", "aug", listOf(0, 4, 8))
    val SUS_2 = ChordQuality("Sus2", "sus2", listOf(0, 2, 7))
    val SUS_4 = ChordQuality("Sus4", "sus4", listOf(0, 5, 7))
    val SIXTH = ChordQuality("Sexta", "6", listOf(0, 4, 7, 9))
    val MINOR_6 = ChordQuality("m6", "m6", listOf(0, 3, 7, 9))
    val ADD_9 = ChordQuality("Add9", "add9", listOf(0, 4, 7, 14))
    val NINTH = ChordQuality("Novena", "9", listOf(0, 4, 7, 10, 14))
    val MINOR_9 = ChordQuality("m9", "m9", listOf(0, 3, 7, 10, 14))
    val MAJOR_9 = ChordQuality("Maj9", "maj9", listOf(0, 4, 7, 11, 14))
    val MINOR_MAJOR_7 = ChordQuality("Menor con maj7", "mMaj7", listOf(0, 3, 7, 11))
    val AUGMENTED_MAJOR_7 = ChordQuality("Aumentado con maj7", "maj7#5", listOf(0, 4, 8, 11))
    val AUGMENTED_7 = ChordQuality("Aumentado 7", "7#5", listOf(0, 4, 8, 10))
    val DIMINISHED_MAJOR_7 = ChordQuality("Disminuido con maj7", "dimMaj7", listOf(0, 3, 6, 11))
    val SUS_2_7 = ChordQuality("Sus2 7", "7sus2", listOf(0, 2, 7, 10))
    val SUS_4_7 = ChordQuality("Sus4 7", "7sus4", listOf(0, 5, 7, 10))

    val all = listOf(
        MAJOR, MINOR, POWER, DOMINANT_7, MAJOR_7, MINOR_7, DIMINISHED,
        DIMINISHED_7, HALF_DIMINISHED, AUGMENTED, SUS_2, SUS_4, SIXTH,
        MINOR_6, ADD_9, NINTH, MINOR_9, MAJOR_9, MINOR_MAJOR_7,
        AUGMENTED_MAJOR_7, AUGMENTED_7, DIMINISHED_MAJOR_7, SUS_2_7, SUS_4_7
    )
}

enum class ViewMode { SCALE, CHORD, FREE, IMPROV }

data class FretboardConfig(
    val tuning: Tuning = Tunings.STANDARD,
    val mode: ViewMode = ViewMode.SCALE,
    val rootPc: Int = 0,
    val scale: ScaleMode = Scales.default,
    val chord: ChordQuality = Chords.MAJOR,
    val useFlats: Boolean = true,
    val colorsEnabled: Boolean = true,
    val showDegrees: Boolean = true,
    val circleDegrees: Set<Int> = emptySet(),
    val circleSevenths: Boolean = false,
    val circleOverrides: Map<Int, ChordQuality> = emptyMap(),
    val improvActive: ImprovChord? = null,
    val improvNext: ImprovChord? = null,
    val improvOverlayScale: Boolean = false,
    val improvShowScale: Boolean = true,
    val improvShowChord: Boolean = true,
    val selectedPattern: Int? = null,
    val selectedPatternStartFret: Int? = null,
    val chordPattern: Int? = null,
    val chordPatternStartFret: Int? = null
)
