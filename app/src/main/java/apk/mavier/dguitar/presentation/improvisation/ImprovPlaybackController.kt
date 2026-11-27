package apk.mavier.dguitar.presentation.improvisation

import apk.mavier.dguitar.domain.model.ImprovBar
import apk.mavier.dguitar.presentation.common.PlaybackTimeline
import apk.mavier.dguitar.presentation.common.Scheduler

/**
 * Plays improvisation bars. The shared [PlaybackTimeline] owns scheduling and
 * progress; this adapter only defines how long each bar lasts.
 */
class ImprovPlaybackController(
    scheduler: Scheduler,
    tickMs: Long = DEFAULT_TICK_MS,
    progressMax: Int = DEFAULT_PROGRESS_MAX
) {

    interface Listener {
        fun onBarChanged(barIndex: Int)
        fun onProgress(progress: Int)
        fun onFinished()
    }

    private val timeline = PlaybackTimeline<ImprovBar>(
        scheduler = scheduler,
        durationMs = { bar, bpm ->
            60_000L / bpm * BEATS_PER_BAR * bar.step.chord.normalizedBarsPerChord
        },
        tickMs = tickMs,
        progressMax = progressMax
    )

    var listener: Listener? = null
        set(value) {
            field = value
            timeline.listener = value?.let { target ->
                object : PlaybackTimeline.Listener<ImprovBar> {
                    override fun onStep(index: Int, item: ImprovBar) {
                        target.onBarChanged(index)
                    }

                    override fun onProgress(progress: Int) {
                        target.onProgress(progress)
                    }

                    override fun onFinished() {
                        target.onFinished()
                    }
                }
            }
        }

    val isRunning: Boolean
        get() = timeline.isRunning

    val progress: Int
        get() = timeline.progress

    fun start(bars: List<ImprovBar>, startBar: Int, bpm: Int) {
        timeline.start(bars, startBar, bpm)
    }

    fun stop() {
        timeline.stop()
    }

    private companion object {
        const val BEATS_PER_BAR = 4L
        const val DEFAULT_TICK_MS = 50L
        const val DEFAULT_PROGRESS_MAX = 1000
    }
}
