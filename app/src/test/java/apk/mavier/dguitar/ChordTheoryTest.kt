package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTheoryTest {

    @Test
    fun dMinorNamesRootSolfège() {
        val data = ChordTheory.diagram(2, Chords.MINOR, Tunings.STANDARD, useFlats = true)
        assertEquals("Dm", data.title)
        assertEquals("Re menor", data.subtitle)
        assertEquals(listOf("Re", "Fa", "La"), data.notesSolfege)
        assertEquals(listOf("D", "F", "A"), data.notesAnglo)
    }

    @Test
    fun cMajorUsesOpenShape() {
        val data = ChordTheory.diagram(0, Chords.MAJOR, Tunings.STANDARD, useFlats = true)
        assertEquals("C", data.title)
        assertEquals("Do mayor", data.subtitle)
        assertEquals(listOf<Int?>(null, 3, 2, 0, 1, 0), data.strings.map { it.fret })
        assertEquals(1, data.baseFret)
        assertEquals(4, data.fretSpan)
    }

    @Test
    fun bMinorStartsAtSecondFret() {
        val data = ChordTheory.diagram(11, Chords.MINOR, Tunings.STANDARD, useFlats = true)
        assertEquals(2, data.baseFret)
        assertEquals(2, data.barreFret)
    }

    @Test
    fun formationTextMentionsFormulaAndNotes() {
        val text = ChordTheory.formationText(2, Chords.MINOR, useFlats = true)
        assertTrue(text.contains("Re (D)"))
        assertTrue(text.contains("1 · b3 · 5"))
        assertTrue(text.contains("Re · Fa · La"))
    }

    @Test
    fun namingTextExplainsMinorSuffix() {
        val text = ChordTheory.namingText(2, Chords.MINOR, useFlats = true)
        assertTrue(text.contains("Dm"))
        assertTrue(text.contains("Re menor"))
    }

    @Test
    fun solfegeUsesAccidentalsFromPreference() {
        assertEquals("Sib", Pitch.solfegeName(10, useFlats = true))
        assertEquals("La#", Pitch.solfegeName(10, useFlats = false))
    }
}
