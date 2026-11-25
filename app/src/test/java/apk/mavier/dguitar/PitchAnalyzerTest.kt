package apk.mavier.dguitar

import apk.mavier.dguitar.core.audio.PitchAnalyzer
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchAnalyzerTest {

    private val sampleRate = 22050
    private val size = 2048

    private fun sine(frequency: Double, amplitude: Double = 12000.0): ShortArray =
        ShortArray(size) { index ->
            (sin(2.0 * PI * frequency * index / sampleRate) * amplitude).roundToInt().toShort()
        }

    @Test
    fun detectsLowE() {
        val result = PitchAnalyzer.detect(sine(82.41), sampleRate)
        assertNotNull(result)
        assertEquals(82.41, result!!.frequency, 1.0)
    }

    @Test
    fun detectsA2() {
        val result = PitchAnalyzer.detect(sine(110.0), sampleRate)
        assertNotNull(result)
        assertEquals(110.0, result!!.frequency, 1.0)
    }

    @Test
    fun detectsHighE() {
        val result = PitchAnalyzer.detect(sine(329.63), sampleRate)
        assertNotNull(result)
        assertEquals(329.63, result!!.frequency, 2.0)
    }

    @Test
    fun reportsHighConfidenceForCleanTone() {
        val result = PitchAnalyzer.detect(sine(196.0), sampleRate)
        assertNotNull(result)
        assertTrue(result!!.confidence > 0.5f)
    }

    @Test
    fun silenceReturnsNull() {
        assertNull(PitchAnalyzer.detect(ShortArray(size), sampleRate))
    }

    @Test
    fun detectsLowBassB0() {
        val bassSize = 4096
        val samples = ShortArray(bassSize) { index ->
            (sin(2.0 * PI * 30.87 * index / sampleRate) * 12000.0).roundToInt().toShort()
        }
        val result = PitchAnalyzer.detect(samples, sampleRate)
        assertNotNull(result)
        assertEquals(30.87, result!!.frequency, 1.5)
    }
}
