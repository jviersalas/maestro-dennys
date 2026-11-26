package apk.mavier.dguitar.core.audio

import kotlin.math.min
import kotlin.math.pow

class Sf2Voice(
    private val zone: Sf2Zone,
    midi: Int,
    private val outputRate: Int = 44100,
    velocity: Int = 100
) : PlayableVoice {

    private val ratio = zone.playbackRatio(midi, outputRate)
    private val attackSamples = samplesFor(zone.attackTc)
    private val holdSamples = samplesFor(zone.holdTc)
    private val decaySamples = samplesFor(zone.decayTc)
    private val releaseSamples = samplesFor(zone.releaseTc).coerceAtLeast(1)
    private val sustainLevel = centibelsToGain(zone.sustainCb)
    private val gain = centibelsToGain(zone.attenuationCb) *
        (velocity.coerceIn(0, 127) / 127f)
    private val autoReleaseSamples = (outputRate * AUTO_RELEASE_SECONDS).toLong()
    private val maxAgeSamples = (outputRate * MAX_AGE_SECONDS).toLong()

    private var cursor = zone.start.toDouble()
    private var stage = Stage.ATTACK
    private var stageSamples = 0
    private var envelope = 0f
    private var releaseStartEnvelope = 0f
    private var releaseAge = 0
    private var age = 0L
    private var done = false

    override val ageSamples: Long
        get() = age

    override val finished: Boolean
        get() = done

    override fun next(): Float {
        if (done) return 0f
        age++
        if (stage != Stage.RELEASE && age >= autoReleaseSamples) startRelease()
        updateEnvelope()
        if (done) return 0f
        val sample = readSample()
        advanceCursor()
        if (age > maxAgeSamples) done = true
        return sample * envelope * gain
    }

    private fun readSample(): Float {
        if (cursor < zone.start || cursor >= zone.end) return 0f
        val current = sampleAt(cursor)
        if (!zone.loops) {
            val remaining = zone.end - cursor
            val fade = if (remaining < TAIL_FADE_FRAMES) {
                (remaining / TAIL_FADE_FRAMES).toFloat().coerceIn(0f, 1f)
            } else {
                1f
            }
            return current * fade
        }
        val loopLength = zone.loopEnd - zone.loopStart
        val crossfade = min(LOOP_CROSSFADE_FRAMES.toInt(), loopLength / 4).toDouble()
        if (crossfade > 0 && cursor >= zone.loopEnd - crossfade) {
            val t = ((cursor - (zone.loopEnd - crossfade)) / crossfade)
                .toFloat()
                .coerceIn(0f, 1f)
            val loopPosition = zone.loopStart + (cursor - (zone.loopEnd - crossfade))
            return current * (1f - t) + sampleAt(loopPosition) * t
        }
        return current
    }

    private fun sampleAt(position: Double): Float {
        val index = position.toInt()
        val fraction = (position - index).toFloat()
        val first = zone.sample.amplitudeAt(index)
        val second = zone.sample.amplitudeAt(index + 1)
        return first + (second - first) * fraction
    }

    private fun advanceCursor() {
        cursor += ratio
        if (zone.loops) {
            val loopLength = (zone.loopEnd - zone.loopStart).toDouble()
            if (loopLength > 0.0) {
                while (cursor >= zone.loopEnd) {
                    cursor -= loopLength
                }
                if (cursor < zone.loopStart) cursor = zone.loopStart.toDouble()
            }
        } else if (cursor >= zone.end) {
            done = true
        }
    }

    private fun updateEnvelope() {
        when (stage) {
            Stage.ATTACK -> {
                stageSamples++
                envelope = (stageSamples.toFloat() / attackSamples).coerceIn(0f, 1f)
                if (stageSamples >= attackSamples) {
                    stage = Stage.HOLD
                    stageSamples = 0
                    envelope = 1f
                }
            }

            Stage.HOLD -> {
                stageSamples++
                envelope = 1f
                if (stageSamples >= holdSamples) {
                    stage = Stage.DECAY
                    stageSamples = 0
                }
            }

            Stage.DECAY -> {
                stageSamples++
                val progress = (stageSamples.toFloat() / decaySamples).coerceIn(0f, 1f)
                envelope = 1f - (1f - sustainLevel) * progress
                if (progress >= 1f) {
                    stage = Stage.SUSTAIN
                    stageSamples = 0
                    envelope = sustainLevel
                }
            }

            Stage.SUSTAIN -> {
                envelope = sustainLevel
            }

            Stage.RELEASE -> {
                releaseAge++
                val progress = (releaseAge.toFloat() / releaseSamples).coerceIn(0f, 1f)
                envelope = releaseStartEnvelope * (1f - progress)
                if (progress >= 1f) {
                    done = true
                    envelope = 0f
                }
            }
        }
    }

    private fun startRelease() {
        if (stage == Stage.RELEASE) return
        stage = Stage.RELEASE
        stageSamples = 0
        releaseAge = 0
        releaseStartEnvelope = envelope
    }

    private fun samplesFor(timecents: Int): Int {
        if (timecents <= -12000) return 1
        val seconds = 2.0.pow(timecents / 1200.0).coerceAtMost(60.0)
        return (seconds * outputRate).toInt().coerceAtLeast(1)
    }

    private fun centibelsToGain(centibels: Int): Float =
        10.0.pow(-centibels / 200.0).toFloat()

    private enum class Stage { ATTACK, HOLD, DECAY, SUSTAIN, RELEASE }

    private companion object {
        const val AUTO_RELEASE_SECONDS = 4.0
        const val MAX_AGE_SECONDS = 6.0
        const val TAIL_FADE_FRAMES = 64.0
        const val LOOP_CROSSFADE_FRAMES = 96.0
    }
}
