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
        val segment = layout().first { it.number == 1 }
        assertEquals(0, segment.lane)
        assertEquals(54f, segment.left, delta)
        assertEquals(307f, segment.right, delta)
        assertEquals(588f, segment.barY, delta)
    }

    @Test
    fun overlappingOccurrencesUseDifferentLanes() {
        val segments = layout()
        val one = segments.first { it.number == 1 }
        val two = segments.first { it.number == 2 }
        assertEquals(1, two.lane)
        assertTrue(one.lane != two.lane)
    }

    @Test
    fun tapOnASegmentSelectsItsPattern() {
        val segments = layout()
        val hit = PatternSegments.hit(segments, 100f, 588f, 1f)
        assertEquals(1, hit?.number)
        assertEquals(0, hit?.startFret)
    }

    @Test
    fun tapAwayFromSegmentsSelectsNothing() {
        val segments = layout()
        assertNull(PatternSegments.hit(segments, 100f, 100f, 1f))
    }
}
