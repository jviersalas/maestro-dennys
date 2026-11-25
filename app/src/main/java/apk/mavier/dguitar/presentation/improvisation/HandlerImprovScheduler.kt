package apk.mavier.dguitar.presentation.improvisation

import android.os.Handler
import android.os.Looper

class HandlerImprovScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : ImprovScheduler {

    override fun postDelayed(delayMs: Long, action: () -> Unit): ImprovScheduler.Cancellable {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMs)
        return object : ImprovScheduler.Cancellable {
            override fun cancel() {
                handler.removeCallbacks(runnable)
            }
        }
    }
}
