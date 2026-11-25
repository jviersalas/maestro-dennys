package apk.mavier.dguitar.presentation.common

import android.os.Handler
import android.os.Looper

class HandlerScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : Scheduler {

    override fun postDelayed(delayMs: Long, action: () -> Unit): Scheduler.Cancellable {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMs)
        return object : Scheduler.Cancellable {
            override fun cancel() {
                handler.removeCallbacks(runnable)
            }
        }
    }
}
