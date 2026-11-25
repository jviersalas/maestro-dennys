package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.model.Tunings
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicTheoryTest {

    @Test
    fun a4Is440Hz() {
        assertEquals(440.0, Pitch.frequency(69), 0.001)
    }

    @Test
    fun lowEIsAbout82Hz() {
        assertEquals(82.41, Pitch.frequency(40), 0.1)
    }

    @Test
    fun locrianNatural6InC() {
        val pitchClasses = Scales.LOCRIAN_N6.pitchClasses(0)
        assertEquals(setOf(0, 1, 3, 5, 6, 9, 10), pitchClasses)
    }

    @Test
    fun locrianNatural6DegreeMapping() {
        assertEquals(1, Scales.LOCRIAN_N6.degreeOf(0, 0))
        assertEquals(2, Scales.LOCRIAN_N6.degreeOf(0, 1))
        assertEquals(6, Scales.LOCRIAN_N6.degreeOf(0, 9))
        assertEquals(-1, Scales.LOCRIAN_N6.degreeOf(0, 2))
    }

    @Test
    fun standardTuningUsesSixStrings() {
        assertEquals(
            listOf(64, 59, 55, 50, 45, 40),
            Tunings.STANDARD.strings.map { it.openMidi }
        )
    }

    @Test
    fun fretMapsToMidi() {
        assertEquals(69, Tunings.STANDARD.strings[0].noteAt(5))
    }

    @Test
    fun noteNamesRespectAccidentals() {
        assertEquals("Db", Pitch.name(1, true))
        assertEquals("C#", Pitch.name(1, false))
        assertEquals("Bb", Pitch.name(10, true))
    }

    @Test
    fun fourStringBassTuning() {
        assertEquals(
            listOf(43, 38, 33, 28),
            Tunings.BASS_4.strings.map { it.openMidi }
        )
    }

    @Test
    fun fiveStringBassAddsLowB() {
        assertEquals(
            listOf(43, 38, 33, 28, 23),
            Tunings.BASS_5.strings.map { it.openMidi }
        )
    }
}
