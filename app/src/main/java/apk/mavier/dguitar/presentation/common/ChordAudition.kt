package apk.mavier.dguitar.presentation.common

import apk.mavier.dguitar.domain.model.ChordSpec
import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.Tuning

/**
 * Converts a chord or a voicing into the note steps a [NoteSequencePlayer]
 * needs. This is the pure part of chord auditioning and is easy to test.
 */
object ChordNotes {

    fun steps(spec: ChordSpec, tuning: Tuning): List<NoteStep> =
        steps(ChordTheory.positions(spec.rootPc, spec.quality, tuning), tuning)

    fun steps(positions: List<Pair<Int, Int>>, tuning: Tuning): List<NoteStep> =
        positions.mapNotNull { (stringIndex, fret) ->
            val string = tuning.strings.getOrNull(stringIndex) ?: return@mapNotNull null
            NoteStep(
                stringIndex = stringIndex,
                fret = fret,
                midi = string.openMidi + fret
            )
        }
}

/**
 * Plays a chord as a short strum and silences the previous one. Callers no
 * longer need to know about voicings, MIDI conversion or the sequence player.
 */
class ChordAudition(
    private val silenceNotes: () -> Unit,
    private val player: NoteSequencePlayer,
    private val tuning: () -> Tuning,
    private val stepMs: Long = DEFAULT_STEP_MS
) {

    fun play(spec: ChordSpec) {
        play(ChordTheory.positions(spec.rootPc, spec.quality, tuning()), stepMs)
    }

    fun play(positions: List<Pair<Int, Int>>, stepMs: Long = this.stepMs) {
        val steps = ChordNotes.steps(positions, tuning())
        if (steps.isEmpty()) return
        stop()
        silenceNotes()
        player.play(steps, stepMs)
    }

    fun stop() {
        player.stop()
    }

    private companion object {
        const val DEFAULT_STEP_MS = 70L
    }
}
