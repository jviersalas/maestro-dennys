package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Scales
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleDegreeTest {

    @Test
    fun majorDegrees() {
        assertEquals("1", Scales.MAJOR.degreeName(1))
        assertEquals("4", Scales.MAJOR.degreeName(4))
        assertEquals("7", Scales.MAJOR.degreeName(7))
    }

    @Test
    fun naturalMinorKeepsFlatDegreeNames() {
        assertEquals("1", Scales.NATURAL_MINOR.degreeName(1))
        assertEquals("b3", Scales.NATURAL_MINOR.degreeName(3))
        assertEquals("b6", Scales.NATURAL_MINOR.degreeName(6))
        assertEquals("b7", Scales.NATURAL_MINOR.degreeName(7))
    }

    @Test
    fun pentatonicUsesMusicalDegreeNumbers() {
        assertEquals("1", Scales.PENTATONIC_MAJOR.degreeName(1))
        assertEquals("2", Scales.PENTATONIC_MAJOR.degreeName(2))
        assertEquals("3", Scales.PENTATONIC_MAJOR.degreeName(3))
        assertEquals("5", Scales.PENTATONIC_MAJOR.degreeName(4))
        assertEquals("6", Scales.PENTATONIC_MAJOR.degreeName(5))
    }

    @Test
    fun bluesKeepsFlatFive() {
        assertEquals("b5", Scales.BLUES_MINOR.degreeName(4))
    }
}
