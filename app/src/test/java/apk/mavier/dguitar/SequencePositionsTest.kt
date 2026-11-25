package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.CagedPatterns
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.SequencePositions
import apk.mavier.dguitar.domain.model.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SequencePositionsTest {

    private val tuning = Tunings.STANDARD
    private val pentatonicSequence = listOf(60, 62, 64, 67, 69, 67, 64, 62, 60)

    @Test
    fun patternFourUsesTheOccurrenceThatFitsTheOctave() {
        val occurrence = SequencePositions.bestOccurrence(
            pentatonicSequence,
            CagedPatterns.occurrencesFor(4),
            tuning
        )
        assertNotNull(occurrence)
        assertEquals(12, occurrence!!.startFret)
        assertEquals(16, occurrence.endFret)
    }

    @Test
    fun patternOneUsesTheLowerPosition() {
        val occurrence = SequencePositions.bestOccurrence(
            pentatonicSequence,
            CagedPatterns.occurrencesFor(1),
            tuning
        )
        assertNotNull(occurrence)
        assertEquals(5, occurrence!!.startFret)
        assertEquals(9, occurrence.endFret)
    }

    @Test
    fun exactPositionFindsC4InPatternOne() {
        val position = SequencePositions.exactPosition(60, 5, 9, tuning)
        assertNotNull(position)
        assertEquals(60, position!!.midi(tuning))
        assertTrue(position.fret in 5..9)
    }

    @Test
    fun missingNoteFallsBackToNearestPitchClassInsideThePattern() {
        val position = SequencePositions.closestPitchClassPosition(69, 0, 4, tuning)
        assertNotNull(position)
        val midi = position!!.midi(tuning)
        assertEquals(9, Pitch.pitchClass(midi))
        assertTrue(position.fret in 0..4)
    }

    @Test
    fun positionInOccurrenceKeepsEveryNoteInsideTheWindow() {
        val occurrence = CagedPatterns.occurrencesFor(4).first { it.startFret == 12 }
        for (midi in pentatonicSequence) {
            val position = SequencePositions.positionInOccurrence(midi, occurrence, tuning)
            assertNotNull(position)
            assertTrue(position!!.fret in occurrence.startFret..occurrence.endFret)
        }
    }

    @Test
    fun freePositionPicksTheLowestPlayableFret() {
        val position = SequencePositions.freePosition(60, tuning, 22)
        assertNotNull(position)
        assertEquals(1, position!!.fret)
        assertEquals(60, position.midi(tuning))
    }
}
