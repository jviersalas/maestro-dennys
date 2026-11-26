package apk.mavier.dguitar

import apk.mavier.dguitar.core.audio.Sf2Sample
import apk.mavier.dguitar.core.audio.Sf2SoundFont
import apk.mavier.dguitar.core.audio.Sf2Voice
import apk.mavier.dguitar.core.audio.Sf2Zone
import apk.mavier.dguitar.data.preferences.SoundProfileCodec
import apk.mavier.dguitar.domain.model.SoundProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ShortBuffer
import kotlin.math.abs

class Sf2Test {

    @Test
    fun parsesBundledGuitarSoundFont() {
        val file = requireFile("guitar_acoustic.sf2")
        val font = Sf2SoundFont.parse(file.readBytes())

        assertEquals(9, font.presets.size)
        assertEquals("Acoustic Guitar", font.presets.first().name)
        assertEquals("Public Domain", font.metadata["ICOP"])

        val zones = font.zonesFor(64, 100)
        assertTrue(zones.isNotEmpty())
        assertEquals("Acstc Guitar D3", zones.first().sample.name)
        assertEquals(67, zones.first().rootKey)
        assertFalse(zones.first().loops)
        assertTrue(zones.first().playbackRatio(64, 44100) in 0.83..0.86)
    }

    @Test
    fun parsesAllBundledSoundFonts() {
        val assets = listOf(
            "guitar_acoustic.sf2" to 64,
            "bass_yr.sf2" to 40
        )
        for ((name, midi) in assets) {
            val font = Sf2SoundFont.parse(requireFile(name).readBytes())
            assertTrue("$name sin presets", font.presets.isNotEmpty())
            assertTrue("$name sin zonas", font.zonesFor(midi, 100).isNotEmpty())
        }
    }

    @Test
    fun bundledVoiceProducesAudibleSignal() {
        val file = requireFile("guitar_acoustic.sf2")
        val font = Sf2SoundFont.parse(file.readBytes())
        val zone = font.zonesFor(64, 100).first()
        val voice = Sf2Voice(zone, 64)
        var energy = 0.0
        var peak = 0f
        var samples = 0
        while (!voice.finished && samples < 44_100) {
            val value = voice.next()
            energy += value * value
            peak = maxOf(peak, abs(value))
            samples++
        }
        assertTrue(samples > 1_000)
        assertTrue(peak > 0.001f)
        assertTrue(energy / samples > 0.0)
    }

    @Test
    fun bundledGuitarCoversTheWholeFretboardRange() {
        val font = Sf2SoundFont.parse(requireFile("guitar_acoustic.sf2").readBytes())
        for (midi in 40..88) {
            assertTrue("Sin zona para MIDI $midi", font.zonesFor(midi, 100).isNotEmpty())
        }
    }

    @Test
    fun voicePlaysAndFinishes() {
        val voice = Sf2Voice(sampleZone(), 60)
        assertTrue(abs(voice.next()) > 0f)
        var samples = 0
        while (!voice.finished && samples < 100_000) {
            voice.next()
            samples++
        }
        assertTrue(voice.finished)
        assertEquals(1.0, sampleZone().playbackRatio(60, 44100), 0.0001)
        assertEquals(2.0, sampleZone().playbackRatio(72, 44100), 0.0001)
    }

    @Test
    fun loopVoiceSurvivesLongerThanSample() {
        val zone = sampleZone(loop = true)
        val voice = Sf2Voice(zone, 60)
        var samples = 0
        while (!voice.finished && samples < 250_000) {
            voice.next()
            samples++
        }
        assertTrue(voice.finished)
        assertTrue(samples > zone.end)
    }

    @Test
    fun soundProfileCodecRoundTrips() {
        assertEquals(SoundProfile.synthetic, SoundProfileCodec.parse("synthetic"))
        assertEquals(
            SoundProfile.bundledGuitar,
            SoundProfileCodec.parse("bundled:sf2/guitar_acoustic.sf2")
        )
        assertEquals(
            SoundProfile.imported("clasica.sf2"),
            SoundProfileCodec.parse("imported:clasica.sf2")
        )
        assertEquals(
            "imported:clasica.sf2",
            SoundProfileCodec.encode(SoundProfile.imported("clasica.sf2"))
        )
        assertEquals(SoundProfile.bundledGuitar, SoundProfileCodec.parse(null))
        assertEquals("synthetic", SoundProfileCodec.encode(SoundProfile.synthetic))
    }

    private fun sampleZone(loop: Boolean = false): Sf2Zone {
        val frames = 4096
        val data = ShortArray(frames) { index ->
            if (index % 100 < 50) 12000 else -12000
        }
        val sample = Sf2Sample(
            name = "test",
            sampleRate = 44100,
            originalKey = 60,
            pitchCorrection = 0,
            start = 0,
            end = frames,
            loopStart = 2048,
            loopEnd = frames,
            pcm = ShortBuffer.wrap(data)
        )
        return Sf2Zone(
            keyLow = 0,
            keyHigh = 127,
            velocityLow = 0,
            velocityHigh = 127,
            sample = sample,
            rootKey = 60,
            coarseTune = 0,
            fineTune = 0,
            attenuationCb = 0,
            pan = 0,
            sampleModes = if (loop) 1 else 0,
            start = 0,
            end = frames,
            loopStart = 2048,
            loopEnd = frames,
            attackTc = -12000,
            holdTc = -12000,
            decayTc = -12000,
            sustainCb = 0,
            releaseTc = -12000
        )
    }

    private fun requireFile(name: String): File {
        val file = findFile(
            "src/main/assets/sf2/$name",
            "app/src/main/assets/sf2/$name"
        )
        assertTrue("Falta el asset $name", file != null)
        return file!!
    }

    private fun findFile(vararg paths: String): File? =
        paths.map(::File).firstOrNull { it.isFile }
}
