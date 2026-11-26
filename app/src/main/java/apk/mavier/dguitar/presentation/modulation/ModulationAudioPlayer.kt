package apk.mavier.dguitar.presentation.modulation

import apk.mavier.dguitar.core.audio.GuitarSynth
import apk.mavier.dguitar.domain.model.ChordQuality
import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.ModulationChord
import apk.mavier.dguitar.domain.model.SequencePositions
import apk.mavier.dguitar.domain.model.Tuning
import apk.mavier.dguitar.presentation.common.Scheduler

class ModulationAudioPlayer(
    private val synth: GuitarSynth,
    private val scheduler: Scheduler,
    var tuning: Tuning,
    var useFlats: Boolean
) {

    enum class PlayMode { STRUM, ARPEGGIO }

    private data class Note(val stringIndex: Int, val midi: Int)

    private val scheduled = mutableListOf<Scheduler.Cancellable>()

    fun playChord(
        chord: ModulationChord,
        mode: PlayMode = PlayMode.STRUM,
        accent: Boolean = false,
        onNote: ((stringIndex: Int, midi: Int) -> Unit)? = null
    ) {
        playChord(chord.rootPc, chord.quality, mode, accent, onNote)
    }

    fun playChord(
        rootPc: Int,
        quality: ChordQuality,
        mode: PlayMode = PlayMode.STRUM,
        accent: Boolean = false,
        onNote: ((stringIndex: Int, midi: Int) -> Unit)? = null
    ) {
        var notes = voicingNotes(rootPc, quality)
        if (notes.isEmpty()) notes = fallbackNotes(rootPc, quality)
        if (notes.isEmpty()) return

        val ordered = when (mode) {
            PlayMode.STRUM -> notes.sortedBy { it.midi }
            PlayMode.ARPEGGIO -> arpeggioNotes(notes)
        }

        val withAccent = if (accent) {
            val highRoot = highRootNote(rootPc)
            if (highRoot != null) ordered + highRoot else ordered
        } else {
            ordered
        }

        val gap = if (mode == PlayMode.STRUM) STRUM_GAP_MS else ARPEGGIO_GAP_MS
        withAccent.forEachIndexed { index, note ->
            schedule(index * gap) {
                synth.pluck(note.stringIndex, note.midi)
                onNote?.invoke(note.stringIndex, note.midi)
            }
        }
    }

    fun stop() {
        scheduled.forEach { it.cancel() }
        scheduled.clear()
    }

    private fun voicingNotes(rootPc: Int, quality: ChordQuality): List<Note> {
        val diagram = ChordTheory.diagram(rootPc, quality, tuning, useFlats)
        val notes = ArrayList<Note>(diagram.strings.size)
        for (index in diagram.strings.indices) {
            val stringIndex = tuning.strings.lastIndex - index
            val string = tuning.strings.getOrNull(stringIndex) ?: continue
            val fret = diagram.strings[index].fret ?: continue
            notes.add(Note(stringIndex, string.openMidi + fret))
        }
        return notes
    }

    private fun fallbackNotes(rootPc: Int, quality: ChordQuality): List<Note> {
        val base = if (tuning.strings.size < 6) BASS_BASE_MIDI else GUITAR_BASE_MIDI
        val notes = ArrayList<Note>()
        for (tone in ChordTheory.tones(quality)) {
            val midi = base + ((rootPc + tone.semitone) % 12 + 12) % 12
            val position = SequencePositions.freePosition(midi, tuning, MAX_FRET)
                ?: SequencePositions.freePosition(midi + 12, tuning, MAX_FRET)
                ?: continue
            notes.add(Note(position.stringIndex, position.midi(tuning)))
        }
        return notes.distinctBy { it.midi }
    }

    private fun arpeggioNotes(notes: List<Note>): List<Note> {
        val ascending = notes.sortedBy { it.midi }
        if (ascending.size >= 4) return ascending
        val root = ascending.firstOrNull() ?: return ascending
        val octave = SequencePositions.freePosition(root.midi + 12, tuning, MAX_FRET)
            ?: return ascending
        val completed = ascending + Note(octave.stringIndex, octave.midi(tuning))
        return completed.sortedBy { it.midi }
    }

    private fun highRootNote(rootPc: Int): Note? {
        val base = if (tuning.strings.size < 6) BASS_BASE_MIDI else GUITAR_BASE_MIDI
        val low = base + ((rootPc % 12) + 12) % 12
        val position = SequencePositions.freePosition(low + 24, tuning, MAX_FRET)
            ?: SequencePositions.freePosition(low + 12, tuning, MAX_FRET)
            ?: return null
        return Note(position.stringIndex, position.midi(tuning))
    }

    private fun schedule(delayMs: Long, action: () -> Unit) {
        var task: Scheduler.Cancellable? = null
        task = scheduler.postDelayed(delayMs) {
            task?.let { scheduled.remove(it) }
            action()
        }
        task?.let { scheduled.add(it) }
    }

    private companion object {
        const val STRUM_GAP_MS = 46L
        const val ARPEGGIO_GAP_MS = 92L
        const val GUITAR_BASE_MIDI = 48
        const val BASS_BASE_MIDI = 28
        const val MAX_FRET = 22
    }
}
