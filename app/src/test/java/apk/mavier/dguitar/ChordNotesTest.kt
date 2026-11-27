package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.SongChord
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.presentation.common.ChordNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordNotesTest {

    @Test
    fun gMajorOpenShapeBecomesMidiSteps() {
        val steps = ChordNotes.steps(
            SongChord(7, Chords.MAJOR),
            Tunings.STANDARD
        )

        assertEquals(listOf(5, 4, 3, 2, 1, 0), steps.map { it.stringIndex })
        assertEquals(listOf(3, 2, 0, 0, 0, 3), steps.map { it.fret })
        assertEquals(listOf(43, 47, 50, 55, 59, 67), steps.map { it.midi })
    }

    @Test
    fun unknownStringPositionsAreSkipped() {
        val steps = ChordNotes.steps(
            listOf(0 to 3, 99 to 5),
            Tunings.STANDARD
        )

        assertEquals(1, steps.size)
    }
}
