package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.CagedPatterns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CagedPatternsTest {

    @Test
    fun leftmostOccurrenceIsPatternOne() {
        val first = CagedPatterns.occurrences.first()
        assertEquals(1, first.number)
        assertEquals(0, first.startFret)
        assertEquals(4, first.endFret)
    }

    @Test
    fun secondOccurrenceIsPatternTwo() {
        val second = CagedPatterns.occurrences[1]
        assertEquals(2, second.number)
        assertEquals(3, second.startFret)
        assertEquals(7, second.endFret)
    }

    @Test
    fun patternOneIncludesOpenString() {
        assertTrue(CagedPatterns.includesOpenString(1))
        assertTrue(CagedPatterns.contains(1, 0))
    }

    @Test
    fun patternOneRepeatsOnNextOctave() {
        val ranges = CagedPatterns.occurrencesFor(1)
        assertEquals(2, ranges.size)
        assertEquals(0, ranges[0].startFret)
        assertEquals(12, ranges[1].startFret)
        assertEquals(16, ranges[1].endFret)
    }

    @Test
    fun patternThreeOccurrences() {
        val ranges = CagedPatterns.occurrencesFor(3).map { it.startFret..it.endFret }
        assertEquals(listOf(5..9, 17..21), ranges)
    }

    @Test
    fun unknownPatternHasNoOccurrences() {
        assertTrue(CagedPatterns.occurrencesFor(9).isEmpty())
    }

    @Test
    fun allWindowsStayWithinTheNeck() {
        assertTrue(
            CagedPatterns.occurrences.all {
                it.startFret >= 0 &&
                    it.endFret <= CagedPatterns.FRET_COUNT &&
                    it.endFret - it.startFret >= 2
            }
        )
    }
}
