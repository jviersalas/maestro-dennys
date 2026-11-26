package apk.mavier.dguitar.domain.model

/**
 * Number and names of the accidentals that define a key signature.
 * [sharps] is true for sharp signatures and false for flat signatures.
 */
data class KeySignature(
    val accidentals: List<String>,
    val sharps: Boolean
) {
    val count: Int
        get() = accidentals.size

    val isNatural: Boolean
        get() = accidentals.isEmpty()
}

/**
 * One position of the circle of fifths, clockwise from C.
 *
 * Each position holds a major key and its relative minor. Enharmonic positions
 * (F#/Gb and C#/Db) keep both spellings so the user can toggle between sharps
 * and flats without changing the musical logic.
 */
data class CircleKey(
    val index: Int,
    val majorPc: Int,
    val minorPc: Int,
    val majorSharpName: String,
    val majorFlatName: String,
    val minorSharpName: String,
    val minorFlatName: String,
    val sharpSignature: KeySignature,
    val flatSignature: KeySignature
) {
    fun majorName(useFlats: Boolean): String =
        if (useFlats) majorFlatName else majorSharpName

    fun minorName(useFlats: Boolean): String =
        if (useFlats) minorFlatName else minorSharpName

    fun signature(useFlats: Boolean): KeySignature =
        if (useFlats) flatSignature else sharpSignature

    fun rootPc(minor: Boolean): Int = if (minor) minorPc else majorPc

    fun modeName(useFlats: Boolean, minor: Boolean): String =
        if (minor) minorName(useFlats) else majorName(useFlats)

    fun modeScale(minor: Boolean): ScaleMode =
        if (minor) Scales.NATURAL_MINOR else Scales.MAJOR

    fun spelledScale(useFlats: Boolean, minor: Boolean): List<String> =
        KeySpelling.spell(modeName(useFlats, minor), rootPc(minor), modeScale(minor).intervals)
}

/**
 * Spells scale degrees following the key letter, so F# major produces E# and
 * Gb major produces Cb instead of the generic enharmonic names.
 */
object KeySpelling {

    private val LETTERS = arrayOf("C", "D", "E", "F", "G", "A", "B")
    private val LETTER_PCS = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    fun spell(rootName: String, rootPc: Int, intervals: List<Int>): List<String> {
        val rootLetter = LETTERS.indexOfFirst { rootName.startsWith(it) }.coerceAtLeast(0)
        return intervals.mapIndexed { degree, interval ->
            val targetPc = normalizePitchClass(rootPc + interval)
            val letterIndex = (rootLetter + degree) % 7
            var diff = targetPc - LETTER_PCS[letterIndex]
            while (diff > 6) diff -= 12
            while (diff < -6) diff += 12
            val accidental = when {
                diff > 0 -> "#".repeat(diff)
                diff < 0 -> "b".repeat(-diff)
                else -> ""
            }
            LETTERS[letterIndex] + accidental
        }
    }
}

/**
 * Positions of the circle that belong to the harmonic field of a key.
 * Major indices highlight the major ring; minor indices highlight the
 * relative-minor ring.
 */
data class CircleHighlight(
    val majorIndices: Set<Int>,
    val minorIndices: Set<Int>
)

object CircleOfFifths {

    private val SHARP_ORDER = listOf("F#", "C#", "G#", "D#", "A#", "E#", "B#")
    private val FLAT_ORDER = listOf("Bb", "Eb", "Ab", "Db", "Gb", "Cb", "Fb")
    private val NATURAL = KeySignature(emptyList(), sharps = true)
    private val ROMANS = arrayOf("I", "II", "III", "IV", "V", "VI", "VII")

    private val MAJOR_PROGRESSIONS = listOf(
        "major-145",
        "pop-1645",
        "pop-6415",
        "jazz-251"
    )
    private val MINOR_PROGRESSIONS = listOf(
        "minor-145",
        "minor-1637",
        "jazz-minor-251"
    )

    private fun sharpSignature(count: Int) =
        KeySignature(SHARP_ORDER.take(count), sharps = true)

    private fun flatSignature(count: Int) =
        KeySignature(FLAT_ORDER.take(count), sharps = false)

    /** Twelve positions, clockwise from C. */
    val keys: List<CircleKey> = listOf(
        CircleKey(0, 0, 9, "C", "C", "Am", "Am", NATURAL, NATURAL),
        CircleKey(1, 7, 4, "G", "G", "Em", "Em", sharpSignature(1), sharpSignature(1)),
        CircleKey(2, 2, 11, "D", "D", "Bm", "Bm", sharpSignature(2), sharpSignature(2)),
        CircleKey(3, 9, 6, "A", "A", "F#m", "F#m", sharpSignature(3), sharpSignature(3)),
        CircleKey(4, 4, 1, "E", "E", "C#m", "C#m", sharpSignature(4), sharpSignature(4)),
        CircleKey(5, 11, 8, "B", "B", "G#m", "G#m", sharpSignature(5), sharpSignature(5)),
        CircleKey(
            6, 6, 3, "F#", "Gb", "D#m", "Ebm",
            sharpSignature(6), flatSignature(6)
        ),
        CircleKey(
            7, 1, 10, "C#", "Db", "A#m", "Bbm",
            sharpSignature(7), flatSignature(5)
        ),
        CircleKey(8, 8, 5, "Ab", "Ab", "Fm", "Fm", flatSignature(4), flatSignature(4)),
        CircleKey(9, 3, 0, "Eb", "Eb", "Cm", "Cm", flatSignature(3), flatSignature(3)),
        CircleKey(10, 10, 7, "Bb", "Bb", "Gm", "Gm", flatSignature(2), flatSignature(2)),
        CircleKey(11, 5, 2, "F", "F", "Dm", "Dm", flatSignature(1), flatSignature(1))
    )

    fun byIndex(index: Int): CircleKey = keys[normalizePitchClass(index)]

    fun byMajorPc(pitchClass: Int): CircleKey? {
        val normalized = normalizePitchClass(pitchClass)
        return keys.firstOrNull { it.majorPc == normalized }
    }

    fun indexForMajorPc(pitchClass: Int): Int {
        val normalized = normalizePitchClass(pitchClass)
        return keys.indexOfFirst { it.majorPc == normalized }
    }

    fun diatonicChords(key: CircleKey, minor: Boolean): List<HarmonicChord> =
        if (minor) {
            HarmonicCircle.chords(Scales.NATURAL_MINOR, key.minorPc)
        } else {
            HarmonicCircle.chords(Scales.MAJOR, key.majorPc)
        }

    fun romanFor(degreeIndex: Int, quality: ChordQuality): String {
        val base = ROMANS[degreeIndex.coerceIn(0, ROMANS.size - 1)]
        val diminished = quality.symbol.contains("dim")
        val lowered = diminished || quality.symbol == Chords.MINOR.symbol
        val text = if (lowered) base.lowercase() else base
        return if (diminished) "$text°" else text
    }

    fun progressions(minor: Boolean): List<GuidedProgression> {
        val ids = if (minor) MINOR_PROGRESSIONS else MAJOR_PROGRESSIONS
        return ids.mapNotNull { GuidedProgressions.byId(it) }
    }

    /**
     * Lights up the diatonic chords on the wheel. Major chords (I, IV, V) map
     * to the major ring and minor chords (ii, iii, vi) to the relative-minor
     * ring. The diminished degree is not represented on the wheel.
     */
    fun highlight(key: CircleKey, minor: Boolean): CircleHighlight {
        val majorIndices = LinkedHashSet<Int>()
        val minorIndices = LinkedHashSet<Int>()
        for (chord in diatonicChords(key, minor)) {
            when {
                chord.quality == Chords.MAJOR -> {
                    val index = indexForMajorPc(chord.rootPc)
                    if (index >= 0) majorIndices.add(index)
                }

                chord.quality == Chords.MINOR -> {
                    val index = keys.indexOfFirst { it.minorPc == chord.rootPc }
                    if (index >= 0) minorIndices.add(index)
                }
            }
        }
        return CircleHighlight(majorIndices, minorIndices)
    }
}

private fun normalizePitchClass(value: Int): Int = ((value % 12) + 12) % 12
