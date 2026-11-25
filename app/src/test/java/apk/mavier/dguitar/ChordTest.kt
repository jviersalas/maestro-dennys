package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChordTest {

    @Test
    fun cMajorIsCEG() {
        assertEquals(setOf(0, 4, 7), Chords.MAJOR.pitchClasses(0))
    }

    @Test
    fun cMinor7IsCEbGBb() {
        assertEquals(setOf(0, 3, 7, 10), Chords.MINOR_7.pitchClasses(0))
    }

    @Test
    fun dMajorIsDFSharpA() {
        assertEquals(setOf(2, 6, 9), Chords.MAJOR.pitchClasses(2))
    }

    @Test
    fun nonChordToneHasNoRole() {
        assertNull(Chords.MAJOR.roleOf(0, 2))
    }

    @Test
    fun majorThirdHasRoleFour() {
        assertEquals(4, Chords.MAJOR.roleOf(0, 4))
    }

    @Test
    fun add9IncludesSecondOctave() {
        assertEquals(setOf(0, 4, 7, 2), Chords.ADD_9.pitchClasses(0))
    }
}
