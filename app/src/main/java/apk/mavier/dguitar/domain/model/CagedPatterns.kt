package apk.mavier.dguitar.domain.model

data class CagedPatternOccurrence(
    val number: Int,
    val startFret: Int,
    val endFret: Int
) {
    operator fun contains(fret: Int): Boolean = fret in startFret..endFret
}

object CagedPatterns {

    const val FRET_COUNT = 22
    private const val CYCLE = 12

    private val BASE_WINDOWS = listOf(
        0..4,
        3..7,
        5..9,
        8..12,
        10..14
    )

    private val NUMBERS = intArrayOf(1, 2, 3, 4, 5)

    val occurrences: List<CagedPatternOccurrence> by lazy { buildOccurrences() }

    private val occurrencesByNumber: Map<Int, List<CagedPatternOccurrence>> by lazy {
        occurrences.groupBy { it.number }
    }

    private fun buildOccurrences(): List<CagedPatternOccurrence> {
        val result = ArrayList<CagedPatternOccurrence>()
        var shift = 0
        while (shift <= FRET_COUNT) {
            for (index in BASE_WINDOWS.indices) {
                val start = BASE_WINDOWS[index].first + shift
                if (start > FRET_COUNT) continue

                val end = (BASE_WINDOWS[index].last + shift).coerceAtMost(FRET_COUNT)
                val clampedStart = start.coerceAtLeast(0)
                if (end - clampedStart < 2) continue

                val occurrence = CagedPatternOccurrence(NUMBERS[index], clampedStart, end)
                val duplicate = result.any {
                    it.number == occurrence.number &&
                        it.startFret == occurrence.startFret &&
                        it.endFret == occurrence.endFret
                }
                if (!duplicate) result.add(occurrence)
            }
            shift += CYCLE
        }
        return result.sortedWith(compareBy({ it.startFret }, { it.endFret }))
    }

    fun occurrencesFor(number: Int): List<CagedPatternOccurrence> =
        occurrencesByNumber[number].orEmpty()

    fun occurrence(number: Int, startFret: Int? = null): CagedPatternOccurrence? {
        val ranges = occurrencesFor(number)
        if (ranges.isEmpty()) return null
        if (startFret != null) {
            ranges.firstOrNull { it.startFret == startFret }?.let { return it }
        }
        return ranges.minByOrNull { it.startFret }
    }

    fun contains(number: Int, fret: Int): Boolean =
        occurrencesFor(number).any { it.contains(fret) }

    fun includesOpenString(number: Int): Boolean =
        occurrencesFor(number).any { it.startFret == 0 }
}
