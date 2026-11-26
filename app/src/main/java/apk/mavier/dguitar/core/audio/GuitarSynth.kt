package apk.mavier.dguitar.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.pow
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class GuitarSynth {

    @Volatile
    var bassMode: Boolean = false

    @Volatile
    private var soundFont: Sf2SoundFont? = null

    @Volatile
    private var soundFontBank: Int = 0

    @Volatile
    private var soundFontProgram: Int = 0

    private companion object {
        const val SAMPLE_RATE = 44100
        const val MAX_VOICES = 8
        const val BASS_MAX_VOICES = 4
        const val CHUNK = 512
        const val MASTER_GAIN = 0.22f
        const val BASS_MASTER_GAIN = 0.15f
        const val BASS_LOWPASS_ALPHA = 0.10f
        const val SF2_VELOCITY = 100
    }

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    private val voices = CopyOnWriteArrayList<PlayableVoice>()

    @Synchronized
    fun start() {
        if (running) return

        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return

        val bufferSize = max(minBuffer * 2, CHUNK * 2 * 4)
        val audioTrack = createTrack(bufferSize) ?: return

        track = audioTrack
        audioTrack.play()
        running = true
        thread = Thread({ renderLoop() }, "DGuitarSynth").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun createTrack(bufferSize: Int): AudioTrack? {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        return try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (_: Exception) {
            try {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Exception) {
                null
            }
        }
    }

    @Synchronized
    fun stop() {
        running = false
        val worker = thread
        thread = null
        try {
            worker?.join(500)
        } catch (_: InterruptedException) {
        }
        voices.clear()
        try {
            track?.pause()
            track?.flush()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }

    fun pluck(stringIndex: Int, midi: Int) {
        if (!running) return
        val isBass = bassMode
        val font = soundFont
        if (font != null) {
            val zones = font.zonesFor(
                midi,
                SF2_VELOCITY,
                soundFontBank,
                soundFontProgram
            )
            if (zones.isNotEmpty()) {
                zones.forEach { zone ->
                    trimVoices(MAX_VOICES - 1)
                    voices.add(Sf2Voice(zone, midi, SAMPLE_RATE, SF2_VELOCITY))
                }
                return
            }
        }
        val maxVoices = if (isBass) BASS_MAX_VOICES else MAX_VOICES
        trimVoices(maxVoices - 1)
        voices.add(Voice(frequency(midi), stringIndex, isBass))
    }

    fun setSoundFont(font: Sf2SoundFont?, bank: Int = 0, program: Int = 0) {
        soundFont = font
        soundFontBank = bank
        soundFontProgram = program
        voices.clear()
    }

    private fun trimVoices(maxSize: Int) {
        while (voices.size > maxSize) {
            val oldest = voices.minByOrNull { it.ageSamples } ?: break
            voices.remove(oldest)
        }
    }

    private fun frequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun renderLoop() {
        val audioTrack = track ?: return
        val buffer = ShortArray(CHUNK)
        var bassFilter = 0f

        while (running) {
            val isBass = bassMode
            val hasSoundFont = soundFont != null
            for (i in 0 until CHUNK) {
                var mix = 0f
                var index = 0
                while (index < voices.size) {
                    val voice = voices[index]
                    mix += voice.next()
                    if (voice.finished) {
                        voices.removeAt(index)
                    } else {
                        index++
                    }
                }
                if (isBass && !hasSoundFont) {
                    bassFilter += (mix - bassFilter) * BASS_LOWPASS_ALPHA
                    mix = bassFilter
                }
                mix *= if (isBass) BASS_MASTER_GAIN else MASTER_GAIN
                mix /= (1f + abs(mix))
                buffer[i] = (mix.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            try {
                audioTrack.write(buffer, 0, CHUNK)
            } catch (_: Exception) {
                break
            }
        }
    }

    private class Voice(
        frequency: Double,
        stringIndex: Int,
        bass: Boolean
    ) : PlayableVoice {

        private val size: Int = max(2, (SAMPLE_RATE / frequency).toInt())
        private val delayLine = FloatArray(size)
        private var cursor = 0
        private var envelope = 1f
        private val envelopeDecay: Float = if (bass) 0.99996f else 0.99997f
        private val damping: Float = if (bass) {
            when (stringIndex) {
                0 -> 0.9982f
                1 -> 0.9978f
                2 -> 0.9974f
                else -> 0.9970f
            }
        } else {
            when (stringIndex) {
                0 -> 0.9986f
                1 -> 0.9982f
                2 -> 0.9978f
                3 -> 0.9974f
                4 -> 0.9970f
                else -> 0.9966f
            }
        }

        override var ageSamples: Long = 0L
            private set

        override val finished: Boolean
            get() = envelope < 0.0006f || ageSamples > SAMPLE_RATE.toLong() * 6L

        init {
            val brightness = if (bass) {
                (0.18f - stringIndex.coerceIn(0, 5) * 0.025f).coerceAtLeast(0.08f)
            } else {
                (0.34f - stringIndex.coerceIn(0, 5) * 0.03f).coerceAtLeast(0.12f)
            }
            var previous = 0f
            for (i in delayLine.indices) {
                val noise = Random.nextFloat() * 2f - 1f
                previous = previous * (1f - brightness) + noise * brightness
                delayLine[i] = previous
            }
        }

        override fun next(): Float {
            ageSamples++
            val current = delayLine[cursor]
            val following = delayLine[(cursor + 1) % size]
            delayLine[cursor] = (current + following) * 0.5f * damping
            cursor = (cursor + 1) % size
            envelope *= envelopeDecay
            return current * envelope
        }
    }
}
