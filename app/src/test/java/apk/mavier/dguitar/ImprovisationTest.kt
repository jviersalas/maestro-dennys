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

    @Test
    fun barsRespectBarsPerChord() {
        val sections = listOf(
            ImprovSection(
                "A",
                listOf(
                    ImprovChord(0, Chords.MAJOR, barsPerChord = 2),
                    ImprovChord(7, Chords.MAJOR, barsPerChord = 1),
                    ImprovChord(9, Chords.MINOR, barsPerChord = 4)
                )
            )
        )

        val bars = Improvisation.bars(sections)
        assertEquals(7, bars.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), bars.map { it.globalIndex })
        assertEquals(listOf(0, 1, 0, 0, 1, 2, 3), bars.map { it.barInChord })
        assertEquals(listOf(0, 0, 1, 2, 2, 2, 2), bars.map { it.stepIndex })
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), bars.map { it.globalBar })
        assertEquals(7, Improvisation.totalBars(sections))
        assertEquals(9, Improvisation.barAt(sections, 3)?.step?.chord?.rootPc)
    }

    @Test
    fun barsPerChordNormalizesInvalidValues() {
        assertEquals(1, ImprovChord(0, Chords.MAJOR, barsPerChord = 0).normalizedBarsPerChord)
        assertEquals(1, ImprovChord(0, Chords.MAJOR, barsPerChord = 3).normalizedBarsPerChord)
        assertEquals(4, ImprovChord(0, Chords.MAJOR, barsPerChord = 4).normalizedBarsPerChord)
    }

    @Test
    fun emptySectionsHaveNoBars() {
        assertTrue(Improvisation.bars(emptyList()).isEmpty())
        assertEquals(0, Improvisation.totalBars(emptyList()))
        assertEquals(null, Improvisation.barAt(emptyList(), 0))
    }

    @Test
    fun bassArpeggioAscendsFromLowestRoot() {
        assertEquals(
            listOf(36, 40, 43, 48),
            Improvisation.ascendingChordMidi(36, listOf(0, 4, 7), 4)
        )
        assertEquals(
            listOf(36, 40, 43, 48, 52, 55, 60, 64),
            Improvisation.ascendingChordMidi(36, listOf(0, 4, 7), 8)
        )
        assertEquals(
            listOf(36),
            Improvisation.ascendingChordMidi(36, listOf(0, 4, 7), 1)
        )
        assertEquals(
            listOf(35, 38, 42, 47),
            Improvisation.ascendingChordMidi(35, listOf(0, 3, 7), 4)
        )
    }
}
