package apk.mavier.dguitar.presentation.common

/**
 * Timing engine shared by playback features that advance through a list of
 * steps. It owns scheduling, progress and completion; callers only provide the
 * list and how long each item should last.
 *
 * The interface is deliberately small: callers never see the internal clock,
 * pending tasks or completion bookkeeping.
 */
class PlaybackTimeline<T>(
    private val scheduler: Scheduler,
    private val durationMs: (item: T, bpm: Int) -> Long,
    private val tickMs: Long = DEFAULT_TICK_MS,
    private val progressMax: Int = DEFAULT_PROGRESS_MAX
) {

    interface Listener<in T> {
        fun onStep(index: Int, item: T)
        fun onProgress(progress: Int)
        fun onFinished()
    }

    var listener: Listener<T>? = null

    private var items: List<T> = emptyList()
    private var bpm = DEFAULT_BPM
    private var activeIndex = -1
    private var elapsedMs = 0L
    private var running = false
    private var stepTask: Scheduler.Cancellable? = null
    private var progressTask: Scheduler.Cancellable? = null

    val isRunning: Boolean
        get() = running

    val progress: Int
        get() {
            if (!running) return 0
            val duration = currentDurationMs()
            if (duration <= 0L) return 0
            val fraction = elapsedMs.toFloat() / duration.toFloat()
            return (fraction.coerceIn(0f, 1f) * progressMax).toInt()
        }

    fun start(items: List<T>, startIndex: Int, bpm: Int) {
        stop()
        if (items.isEmpty()) return
        this.items = items
        this.bpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
        activeIndex = startIndex.coerceIn(0, items.lastIndex)
        running = true
        enterStep()
    }

    fun stop() {
        running = false
        stepTask?.cancel()
        stepTask = null
        progressTask?.cancel()
        progressTask = null
        elapsedMs = 0L
    }

    private fun enterStep() {
        if (!running) return
        val item = items.getOrNull(activeIndex)
        if (item == null) {
            finish()
            return
        }
        elapsedMs = 0L
        listener?.onStep(activeIndex, item)
        stepTask?.cancel()
        stepTask = scheduler.postDelayed(currentDurationMs()) { advance() }
        progressTask?.cancel()
        scheduleProgressTick()
    }

    private fun advance() {
        if (!running) return
        val next = activeIndex + 1
        if (next >= items.size) {
            finish()
            return
        }
        activeIndex = next
        enterStep()
    }

    private fun scheduleProgressTick() {
        progressTask = scheduler.postDelayed(tickMs) {
            if (!running) return@postDelayed
            elapsedMs += tickMs
            listener?.onProgress(progress)
            if (elapsedMs < currentDurationMs()) scheduleProgressTick()
        }
    }

    private fun finish() {
        stop()
        listener?.onFinished()
    }

    private fun currentDurationMs(): Long {
        val item = items.getOrNull(activeIndex) ?: return beatDurationMs()
        return durationMs(item, bpm).coerceAtLeast(0L)
    }

    private fun beatDurationMs(): Long = 60_000L / bpm

    private companion object {
        const val DEFAULT_BPM = 80
        const val MIN_BPM = 40
        const val MAX_BPM = 220
        const val DEFAULT_TICK_MS = 50L
        const val DEFAULT_PROGRESS_MAX = 1000
    }
}
