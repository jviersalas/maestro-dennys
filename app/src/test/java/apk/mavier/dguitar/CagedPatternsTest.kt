package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.CagedPatterns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CagedPatternsTest {

    @Test
    fun leftmostOccurrenceIsPatternFour() {
        val first = CagedPatterns.occurrences.first()
        assertEquals(4, first.number)
        assertEquals(0, first.startFret)
        assertEquals(4, first.endFret)
    }

    @Test
    fun secondOccurrenceIsPatternFive() {
        val second = CagedPatterns.occurrences[1]
        assertEquals(5, second.number)
        assertEquals(3, second.startFret)
        assertEquals(7, second.endFret)
    }

    @Test
    fun patternFourIncludesOpenString() {
        assertTrue(CagedPatterns.includesOpenString(4))
        assertTrue(CagedPatterns.contains(4, 0))
    }

    @Test
    fun patternFourRepeatsOnNextOctave() {
        val ranges = CagedPatterns.occurrencesFor(4)
        assertEquals(2, ranges.size)
        assertEquals(0, ranges[0].startFret)
        assertEquals(16, ranges[1].endFret)
        assertEquals(12, ranges[1].startFret)
    }

    @Test
    fun patternOneOccurrences() {
        val ranges = CagedPatterns.occurrencesFor(1).map { it.startFret..it.endFret }
        assertEquals(listOf(5..9, 17..21), ranges)
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
