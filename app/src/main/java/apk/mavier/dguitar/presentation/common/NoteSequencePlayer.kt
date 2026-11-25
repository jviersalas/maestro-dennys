package apk.mavier.dguitar.presentation.common

data class NoteStep(
    val stringIndex: Int,
    val fret: Int,
    val midi: Int
)

class NoteSequencePlayer(private val scheduler: Scheduler) {

    interface Listener {
        fun onNoteStep(step: NoteStep, index: Int)
        fun onFinished()
    }

    var listener: Listener? = null

    private var task: Scheduler.Cancellable? = null
    private var steps: List<NoteStep> = emptyList()
    private var index = 0
    private var stepDelayMs = 0L

    val isPlaying: Boolean
        get() = task != null

    fun play(steps: List<NoteStep>, stepDelayMs: Long) {
        stop()
        if (steps.isEmpty()) return
        this.steps = steps
        this.stepDelayMs = stepDelayMs
        index = 0
        scheduleNext(0L)
    }

    fun stop() {
        task?.cancel()
        task = null
        steps = emptyList()
        index = 0
    }

    private fun scheduleNext(delayMs: Long) {
        task = scheduler.postDelayed(delayMs) {
            if (index >= steps.size) {
                stop()
                listener?.onFinished()
                return@postDelayed
            }
            val step = steps[index]
            index++
            listener?.onNoteStep(step, index - 1)
            if (isPlaying) scheduleNext(stepDelayMs)
        }
    }
}
