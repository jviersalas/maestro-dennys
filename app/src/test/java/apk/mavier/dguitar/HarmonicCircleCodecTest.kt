package apk.mavier.dguitar

import apk.mavier.dguitar.data.preferences.HarmonicCircleCodec
import apk.mavier.dguitar.domain.model.Chords
import org.junit.Assert.assertEquals
import org.junit.Test

class HarmonicCircleCodecTest {

    @Test
    fun degreesIgnoreInvalidEntries() {
        assertEquals(setOf(1, 2, 7), HarmonicCircleCodec.parseDegrees("1,2,7,9,abc,-1"))
        assertEquals(emptySet<Int>(), HarmonicCircleCodec.parseDegrees(null))
    }

    @Test
    fun degreesEncodeSorted() {
        assertEquals("1,3,5", HarmonicCircleCodec.encodeDegrees(setOf(5, 1, 3)))
    }

    @Test
    fun overridesRoundTrip() {
        val overrides = mapOf(1 to Chords.MINOR, 4 to Chords.MAJOR_7)
        val encoded = HarmonicCircleCodec.encodeOverrides(overrides)
        assertEquals(overrides, HarmonicCircleCodec.parseOverrides(encoded))
    }

    @Test
    fun overridesIgnoreUnknownQualityOrDegree() {
        assertEquals(
            mapOf(2 to Chords.MINOR),
            HarmonicCircleCodec.parseOverrides("2:1;8:0;3:999;broken")
        )
    }
}
