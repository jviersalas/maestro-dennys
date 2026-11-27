package apk.mavier.dguitar.presentation.songbook

import apk.mavier.dguitar.domain.model.SongStep
import apk.mavier.dguitar.presentation.common.PlaybackTimeline
import apk.mavier.dguitar.presentation.common.Scheduler

/**
 * Plays the chord sequence of a song. Timing, progress and completion live in
 * [PlaybackTimeline]; this adapter only maps song steps to their duration.
 */
class SongPlaybackController(
    scheduler: Scheduler,
    tickMs: Long = DEFAULT_TICK_MS,
    progressMax: Int = DEFAULT_PROGRESS_MAX
) {

    interface Listener {
        fun onChordChanged(stepIndex: Int)
        fun onProgress(progress: Int)
        fun onFinished()
    }

    private val timeline = PlaybackTimeline<SongStep>(
        scheduler = scheduler,
        durationMs = { step, bpm -> (60_000L / bpm * step.durationBeats).toLong() },
        tickMs = tickMs,
        progressMax = progressMax
    )

    var listener: Listener? = null
        set(value) {
            field = value
            timeline.listener = value?.let { target ->
                object : PlaybackTimeline.Listener<SongStep> {
                    override fun onStep(index: Int, item: SongStep) {
                        target.onChordChanged(index)
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

    fun start(steps: List<SongStep>, startIndex: Int, bpm: Int) {
        timeline.start(steps, startIndex, bpm)
    }

    fun stop() {
        timeline.stop()
    }

    private companion object {
        const val DEFAULT_TICK_MS = 50L
        const val DEFAULT_PROGRESS_MAX = 1000
    }
}
