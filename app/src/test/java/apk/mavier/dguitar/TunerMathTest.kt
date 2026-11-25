package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.TunerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerMathTest {

    @Test
    fun a4IsMidi69() {
        assertEquals(69, TunerMath.nearestMidi(440.0))
    }

    @Test
    fun dStringShowsMinusFiveCents() {
        val cents = TunerMath.centsBetween(146.4, 146.83)
        assertEquals(-5.07, cents, 0.2)
        assertEquals("-5 cents", TunerMath.formatCents(cents))
    }

    @Test
    fun octaveIsWrappedToZero() {
        assertEquals(0.0, TunerMath.wrappedCents(293.66, 146.83), 0.5)
    }

    @Test
    fun positiveCentsAreSigned() {
        assertEquals("+12 cents", TunerMath.formatCents(12.2))
    }

    @Test
    fun inTuneTolerance() {
        assertTrue(TunerMath.isInTune(4.9))
        assertFalse(TunerMath.isInTune(6.0))
    }
}
