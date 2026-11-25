package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.TunerTargetResolver
import apk.mavier.dguitar.domain.model.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TunerTargetResolverTest {

    private val standard = Tunings.STANDARD

    @Test
    fun manualStringAlwaysWins() {
        assertEquals(2, TunerTargetResolver.resolve(82.41, standard, selectedStringIndex = 2, null))
    }

    @Test
    fun autoModePicksTheClosestString() {
        assertEquals(5, TunerTargetResolver.resolve(82.41, standard, null, null))
        assertEquals(4, TunerTargetResolver.resolve(110.0, standard, null, null))
    }

    @Test
    fun previousStringIsKeptInsideTheHysteresisBand() {
        val betweenLowEAndA = 95.5
        assertEquals(4, TunerTargetResolver.resolve(betweenLowEAndA, standard, null, null))
        assertEquals(5, TunerTargetResolver.resolve(betweenLowEAndA, standard, null, previousAutoIndex = 5))
    }

    @Test
    fun emptyTuningHasNoTarget() {
        assertNull(TunerTargetResolver.resolve(100.0, Tunings.BASS_4.copy(strings = emptyList()), null, null))
    }
}
