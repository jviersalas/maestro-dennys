package apk.mavier.dguitar.domain.model

/**
 * Standard guitar chord shapes (standard tuning), written from the low E
 * string to the high E string. null = muted string, 0 = open string.
 */
data class StandardChordShape(
    val fretsLowToHigh: List<Int?>,
    val barreFret: Int? = null
)

object StandardChordShapes {

    private const val MAJOR = ""
    private const val MINOR = "m"
    private const val DOMINANT_7 = "7"
    private const val SIXTH = "6"
    private const val NINTH = "9"
    private const val MINOR_6 = "m6"
    private const val MINOR_7 = "m7"
    private const val MAJOR_7 = "maj7"
    private const val DIMINISHED = "dim"
    private const val AUGMENTED = "aug"
    private const val SUS_4 = "sus4"

    private val shapes: Map<Pair<Int, String>, StandardChordShape> = mapOf(
        (0 to MAJOR) to shape(null, 3, 2, 0, 1, 0),
        (1 to MAJOR) to shape(null, 4, 6, 6, 6, 4, barreFret = 4),
        (2 to MAJOR) to shape(null, null, 0, 2, 3, 2),
        (3 to MAJOR) to shape(null, 6, 8, 8, 8, 6, barreFret = 6),
        (4 to MAJOR) to shape(0, 2, 2, 1, 0, 0),
        (5 to MAJOR) to shape(1, 3, 3, 2, 1, 1, barreFret = 1),
        (6 to MAJOR) to shape(2, 4, 4, 3, 2, 2, barreFret = 2),
        (7 to MAJOR) to shape(3, 2, 0, 0, 0, 3),
        (8 to MAJOR) to shape(4, 6, 6, 5, 4, 4, barreFret = 4),
        (9 to MAJOR) to shape(null, 0, 2, 2, 2, 0),
        (10 to MAJOR) to shape(null, 1, 3, 3, 3, 1, barreFret = 1),
        (11 to MAJOR) to shape(null, 2, 4, 4, 4, 2, barreFret = 2),

        (0 to MINOR) to shape(null, 3, 5, 5, 4, 3, barreFret = 3),
        (1 to MINOR) to shape(null, 4, 6, 6, 5, 4, barreFret = 4),
        (2 to MINOR) to shape(null, null, 0, 2, 3, 1),
        (3 to MINOR) to shape(null, 6, 8, 8, 7, 6, barreFret = 6),
        (4 to MINOR) to shape(0, 2, 2, 0, 0, 0),
        (5 to MINOR) to shape(1, 3, 3, 1, 1, 1, barreFret = 1),
        (6 to MINOR) to shape(2, 4, 4, 2, 2, 2, barreFret = 2),
        (7 to MINOR) to shape(3, 5, 5, 3, 3, 3, barreFret = 3),
        (8 to MINOR) to shape(4, 6, 6, 4, 4, 4, barreFret = 4),
        (9 to MINOR) to shape(null, 0, 2, 2, 1, 0),
        (10 to MINOR) to shape(null, 1, 3, 3, 2, 1, barreFret = 1),
        (11 to MINOR) to shape(null, 2, 4, 4, 3, 2, barreFret = 2),

        (0 to DOMINANT_7) to shape(null, 3, 2, 3, 1, 0),
        (1 to DOMINANT_7) to shape(null, 4, 6, 4, 6, 4),
        (2 to DOMINANT_7) to shape(null, null, 0, 2, 1, 2),
        (3 to DOMINANT_7) to shape(null, 6, 8, 6, 8, 6),
        (4 to DOMINANT_7) to shape(0, 2, 0, 1, 0, 0),
        (5 to DOMINANT_7) to shape(1, 3, 1, 2, 1, 1, barreFret = 1),
        (6 to DOMINANT_7) to shape(2, 4, 2, 3, 2, 2, barreFret = 2),
        (7 to DOMINANT_7) to shape(3, 2, 0, 0, 0, 1),
        (8 to DOMINANT_7) to shape(4, 6, 4, 5, 4, 4, barreFret = 4),
        (9 to DOMINANT_7) to shape(null, 0, 2, 0, 2, 0),
        (10 to DOMINANT_7) to shape(null, 1, 3, 1, 3, 1),
        (11 to DOMINANT_7) to shape(null, 2, 1, 2, 0, 2),

        (2 to SIXTH) to shape(null, null, 0, 2, 0, 2),
        (2 to NINTH) to shape(null, 5, 4, 5, 5, null),
        (2 to MINOR_6) to shape(null, null, 0, 2, 0, 1),
        (2 to MINOR_7) to shape(null, null, 0, 2, 1, 1),
        (2 to MAJOR_7) to shape(null, null, 0, 2, 2, 2),
        (2 to DIMINISHED) to shape(null, null, 0, 1, 3, 1),
        (2 to AUGMENTED) to shape(null, null, 0, 3, 3, 2),
        (2 to SUS_4) to shape(null, null, 0, 2, 3, 3)
    )

    private fun shape(vararg frets: Int?, barreFret: Int? = null): StandardChordShape =
        StandardChordShape(frets.toList(), barreFret)

    fun shapeFor(rootPc: Int, quality: ChordQuality): StandardChordShape? {
        val pc = ((rootPc % 12) + 12) % 12
        return shapes[pc to quality.symbol]
    }
}
