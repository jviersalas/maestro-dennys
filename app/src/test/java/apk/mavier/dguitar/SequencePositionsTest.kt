package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.CagedPatterns
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.Scales
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
    fun patternOneUsesTheOccurrenceThatFitsTheOctave() {
        val occurrence = SequencePositions.bestOccurrence(
            pentatonicSequence,
            CagedPatterns.occurrencesFor(1),
            tuning
        )
        assertNotNull(occurrence)
        assertEquals(12, occurrence!!.startFret)
        assertEquals(16, occurrence.endFret)
    }

    @Test
    fun patternThreeUsesTheLowerPosition() {
        val occurrence = SequencePositions.bestOccurrence(
            pentatonicSequence,
            CagedPatterns.occurrencesFor(3),
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
        val occurrence = CagedPatterns.occurrencesFor(1).first { it.startFret == 12 }
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

    @Test
    fun lowestRootStartsOnTheThickestStringForG() {
        val position = SequencePositions.lowestRootPosition(7, tuning, 22)
        assertNotNull(position)
        assertEquals(5, position!!.stringIndex)
        assertEquals(3, position.fret)
    }

    @Test
    fun lowestRootUsesTheClosestPatternForBm() {
        val position = SequencePositions.lowestRootPosition(11, tuning, 22)
        assertNotNull(position)
        assertEquals(4, position!!.stringIndex)
        assertEquals(2, position.fret)
    }

    @Test
    fun lowestRootMovesDToTheAStringFifthFret() {
        val position = SequencePositions.lowestRootPosition(2, tuning, 22)
        assertNotNull(position)
        assertEquals(4, position!!.stringIndex)
        assertEquals(5, position.fret)
    }

    @Test
    fun bassLowestRootMovesDToTheAStringFifthFret() {
        val position = SequencePositions.lowestRootPosition(2, Tunings.BASS_4, 22)
        assertNotNull(position)
        assertEquals(2, position!!.stringIndex)
        assertEquals(5, position.fret)
    }

    @Test
    fun gMajorNotesUseTheClosestThickStrings() {
        val root = SequencePositions.bestPositionForMidi(43, tuning, 22)!!
        assertEquals(5, root.stringIndex)
        assertEquals(3, root.fret)

        val third = SequencePositions.bestPositionForMidi(47, tuning, 22)!!
        assertEquals(4, third.stringIndex)
        assertEquals(2, third.fret)

        val fifth = SequencePositions.bestPositionForMidi(50, tuning, 22)!!
        assertEquals(4, fifth.stringIndex)
        assertEquals(5, fifth.fret)

        val octave = SequencePositions.bestPositionForMidi(55, tuning, 22)!!
        assertEquals(3, octave.stringIndex)
        assertEquals(5, octave.fret)
    }

    @Test
    fun bassLowestRootsFollowTheSamePattern() {
        val g = SequencePositions.lowestRootPosition(7, Tunings.BASS_4, 22)
        assertNotNull(g)
        assertEquals(3, g!!.stringIndex)
        assertEquals(3, g.fret)

        val bm = SequencePositions.lowestRootPosition(11, Tunings.BASS_4, 22)
        assertNotNull(bm)
        assertEquals(2, bm!!.stringIndex)
        assertEquals(2, bm.fret)
    }

    @Test
    fun octaveSequenceGoesUpAndDownExactlyOneOctave() {
        val notes = Scales.MAJOR.octaveSequence(0, tuning, 22)
        assertEquals(
            listOf(48, 50, 52, 53, 55, 57, 59, 60, 59, 57, 55, 53, 52, 50, 48),
            notes
        )
    }

    @Test
    fun bassOctaveSequenceGoesUpAndDownOneOctave() {
        val notes = Scales.MAJOR.octaveSequence(0, Tunings.BASS_4, 22)
        assertEquals(15, notes.size)
        assertEquals(36, notes.first())
        assertEquals(36, notes.last())
    }

    @Test
    fun segmentSequenceStaysInsideTheSelectedSegment() {
        val occurrence = CagedPatterns.occurrencesFor(3).first { it.startFret == 5 }
        val positions = Scales.MAJOR.segmentSequence(0, tuning, occurrence, 22)
        assertEquals(15, positions.size)
        assertTrue(positions.all { it.fret in occurrence.startFret..occurrence.endFret })
        assertEquals(5, positions.first().stringIndex)
        assertEquals(8, positions.first().fret)
        assertEquals(positions.first(), positions.last())
    }
}
