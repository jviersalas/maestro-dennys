package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.Improvisation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImprovisationTest {

    @Test
    fun sectionsFlattenInOrder() {
        val sections = listOf(
            ImprovSection(
                "Intro",
                listOf(
                    ImprovChord(7, Chords.MAJOR),
                    ImprovChord(9, Chords.MINOR),
                    ImprovChord(11, Chords.MINOR)
                )
            ),
            ImprovSection(
                "Coro",
                listOf(
                    ImprovChord(7, Chords.MAJOR),
                    ImprovChord(11, Chords.MINOR),
                    ImprovChord(0, Chords.MAJOR)
                )
            )
        )
        val steps = Improvisation.steps(sections)
        assertEquals(6, steps.size)
        assertEquals(listOf(0, 0, 0, 1, 1, 1), steps.map { it.sectionIndex })
        assertEquals(listOf(0, 1, 2, 0, 1, 2), steps.map { it.chordIndex })
        assertEquals("Coro", steps[4].sectionName)
        assertEquals(11, steps[4].chord.rootPc)
    }

    @Test
    fun chordShowsItsNotesAndRoleLabels() {
        val chord = ImprovChord(7, Chords.MAJOR)
        assertEquals("G", chord.label(true))
        assertEquals(listOf("G", "B", "D"), chord.notes(true))
        assertTrue(chord.contains(11))
        assertEquals("R", chord.roleLabel(7))
        assertEquals("3", chord.roleLabel(11))
        assertEquals("5", chord.roleLabel(2))
    }

    @Test
    fun minorAndSeventhRoleLabels() {
        val chord = ImprovChord(0, Chords.MINOR_7)
        assertEquals("R", chord.roleLabel(0))
        assertEquals("3", chord.roleLabel(3))
        assertEquals("5", chord.roleLabel(7))
        assertEquals("7", chord.roleLabel(10))
    }
}
