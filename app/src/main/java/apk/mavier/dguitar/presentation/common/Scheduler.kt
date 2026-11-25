package apk.mavier.dguitar.presentation.common

interface Scheduler {

    interface Cancellable {
        fun cancel()
    }

    fun postDelayed(delayMs: Long, action: () -> Unit): Cancellable
}
