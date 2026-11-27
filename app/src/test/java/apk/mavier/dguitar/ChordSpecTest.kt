package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.ChordSpec
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.HarmonicChord
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.ModulationChord
import apk.mavier.dguitar.domain.model.SongChord
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordSpecTest {

    @Test
    fun everyChordTypeSharesSymbolAndReading() {
        val chords: List<ChordSpec> = listOf(
            SongChord(7, Chords.MAJOR),
            ImprovChord(7, Chords.MAJOR),
            HarmonicChord(degree = 1, rootPc = 7, quality = Chords.MAJOR),
            ModulationChord(7, Chords.MAJOR)
        )

        chords.forEach { chord ->
            assertEquals("G", chord.symbol(useFlats = false))
            assertEquals("Sol mayor", chord.reading(useFlats = false))
            assertEquals("G", chord.label(useFlats = false))
        }
    }

    @Test
    fun songChordKeepsItsSlashBassInTheLabel() {
        val chord = SongChord(7, Chords.MAJOR, bassPc = 11)

        assertEquals("G", chord.symbol(useFlats = false))
        assertEquals("G/B", chord.label(useFlats = false))
    }
}
