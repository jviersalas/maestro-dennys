package apk.mavier.dguitar.presentation.improvisation

interface ImprovScheduler {

    interface Cancellable {
        fun cancel()
    }

    fun postDelayed(delayMs: Long, action: () -> Unit): Cancellable
}
