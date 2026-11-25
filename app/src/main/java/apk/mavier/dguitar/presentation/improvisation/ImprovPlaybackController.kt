package apk.mavier.dguitar.presentation.improvisation

import apk.mavier.dguitar.domain.model.ImprovBar
import apk.mavier.dguitar.presentation.common.Scheduler

class ImprovPlaybackController(
    private val scheduler: Scheduler,
    private val tickMs: Long = DEFAULT_TICK_MS,
    private val progressMax: Int = DEFAULT_PROGRESS_MAX
) {

    interface Listener {
        fun onBarChanged(barIndex: Int)
        fun onProgress(progress: Int)
        fun onFinished()
    }

    var listener: Listener? = null

    private var bars: List<ImprovBar> = emptyList()
    private var bpm = DEFAULT_BPM
    private var activeBar = -1
    private var elapsedMs = 0L
    private var running = false
    private var barTask: Scheduler.Cancellable? = null
    private var progressTask: Scheduler.Cancellable? = null

    val isRunning: Boolean
        get() = running

    val progress: Int
        get() {
            if (!running) return 0
            val duration = barDurationMs()
            if (duration <= 0L) return 0
            val fraction = elapsedMs.toFloat() / duration.toFloat()
            return (fraction.coerceIn(0f, 1f) * progressMax).toInt()
        }

    fun start(bars: List<ImprovBar>, startBar: Int, bpm: Int) {
        stop()
        if (bars.isEmpty()) return
        this.bars = bars
        this.bpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
        activeBar = startBar.coerceIn(0, bars.lastIndex)
        running = true
        enterBar()
    }

    fun stop() {
        running = false
        barTask?.cancel()
        barTask = null
        progressTask?.cancel()
        progressTask = null
        elapsedMs = 0L
    }

    private fun enterBar() {
        if (!running) return
        if (activeBar !in bars.indices) {
            finish()
            return
        }
        elapsedMs = 0L
        listener?.onBarChanged(activeBar)
        barTask?.cancel()
        barTask = scheduler.postDelayed(barDurationMs()) { advanceBar() }
        progressTask?.cancel()
        scheduleProgressTick()
    }

    private fun advanceBar() {
        if (!running) return
        val next = activeBar + 1
        if (next >= bars.size) {
            finish()
            return
        }
        activeBar = next
        enterBar()
    }

    private fun scheduleProgressTick() {
        progressTask = scheduler.postDelayed(tickMs) {
            if (!running) return@postDelayed
            elapsedMs += tickMs
            listener?.onProgress(progress)
            if (elapsedMs < barDurationMs()) scheduleProgressTick()
        }
    }

    private fun finish() {
        stop()
        listener?.onFinished()
    }

    private fun barDurationMs(): Long = beatDurationMs() * BEATS_PER_BAR

    private fun beatDurationMs(): Long = 60_000L / bpm

    private companion object {
        const val DEFAULT_BPM = 80
        const val MIN_BPM = 40
        const val MAX_BPM = 220
        const val BEATS_PER_BAR = 4L
        const val DEFAULT_TICK_MS = 50L
        const val DEFAULT_PROGRESS_MAX = 1000
    }
}
