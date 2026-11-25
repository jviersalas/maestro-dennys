package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.CagedPatterns
import apk.mavier.dguitar.presentation.ui.PatternSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternSegmentsTest {

    private val delta = 0.001f

    private fun layout() = PatternSegments.layout(
        occurrences = CagedPatterns.occurrences,
        boardStartX = 54f,
        fretWidth = 64f,
        scrollX = 0f,
        width = 1000,
        height = 600,
        density = 1f
    )

    @Test
    fun lanesStayWithinTheTwoAvailableLanes() {
        val lanes = PatternSegments.assignLanes(CagedPatterns.occurrences)
        assertEquals(CagedPatterns.occurrences.size, lanes.size)
        assertTrue(lanes.all { it in 0..1 })
    }

    @Test
    fun openPatternStartsAtTheBoardEdge() {
        val segment = layout().first { it.number == 4 }
        assertEquals(0, segment.lane)
        assertEquals(54f, segment.left, delta)
        assertEquals(307f, segment.right, delta)
        assertEquals(588f, segment.barY, delta)
    }

    @Test
    fun overlappingOccurrencesUseDifferentLanes() {
        val segments = layout()
        val four = segments.first { it.number == 4 }
        val five = segments.first { it.number == 5 }
        assertEquals(1, five.lane)
        assertTrue(four.lane != five.lane)
    }

    @Test
    fun tapOnASegmentSelectsItsPattern() {
        val segments = layout()
        assertEquals(4, PatternSegments.hit(segments, 100f, 588f, 1f))
    }

    @Test
    fun tapAwayFromSegmentsSelectsNothing() {
        val segments = layout()
        assertNull(PatternSegments.hit(segments, 100f, 100f, 1f))
    }
}
