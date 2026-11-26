package apk.mavier.dguitar.presentation.modulation

import apk.mavier.dguitar.presentation.common.Scheduler

class ModulationPlaybackController(
    private val scheduler: Scheduler
) {

    interface Listener {
        fun onChordStarted(index: Int)
        fun onBeat(chordIndex: Int, beatInChord: Int, accent: Boolean)
        fun onFinished()
    }

    var listener: Listener? = null

    private var task: Scheduler.Cancellable? = null
    private var chordCount = 0
    private var chordIndex = 0
    private var beatInChord = 0
    private var beatsPerChord = DEFAULT_BEATS_PER_CHORD
    private var beatMs = DEFAULT_BEAT_MS
    private var running = false

    val isRunning: Boolean
        get() = running

    fun start(
        chordCount: Int,
        bpm: Int,
        beatsPerChord: Int = DEFAULT_BEATS_PER_CHORD
    ) {
        stop()
        if (chordCount <= 0) return
        this.chordCount = chordCount
        this.beatsPerChord = beatsPerChord.coerceAtLeast(1)
        this.beatMs = (60_000L / bpm.coerceIn(MIN_BPM, MAX_BPM)).coerceAtLeast(50L)
        chordIndex = 0
        beatInChord = 0
        running = true
        startChord()
    }

    fun stop() {
        running = false
        task?.cancel()
        task = null
    }

    private fun startChord() {
        if (!running) return
        if (chordIndex >= chordCount) {
            finish()
            return
        }
        beatInChord = 0
        listener?.onChordStarted(chordIndex)
        listener?.onBeat(chordIndex, beatInChord, accent = true)
        scheduleNextBeat()
    }

    private fun scheduleNextBeat() {
        task = scheduler.postDelayed(beatMs) {
            if (!running) return@postDelayed
            beatInChord++
            if (beatInChord >= beatsPerChord) {
                chordIndex++
                if (chordIndex >= chordCount) {
                    finish()
                } else {
                    startChord()
                }
                return@postDelayed
            }
            listener?.onBeat(chordIndex, beatInChord, accent = false)
            scheduleNextBeat()
        }
    }

    private fun finish() {
        running = false
        task = null
        listener?.onFinished()
    }

    private companion object {
        const val DEFAULT_BEATS_PER_CHORD = 2
        const val DEFAULT_BEAT_MS = 666L
        const val MIN_BPM = 30
        const val MAX_BPM = 300
    }
}
