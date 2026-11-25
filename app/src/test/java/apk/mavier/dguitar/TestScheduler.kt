package apk.mavier.dguitar

import apk.mavier.dguitar.presentation.common.Scheduler

class TestScheduler : Scheduler {

    private data class Task(val time: Long, val id: Long, val action: () -> Unit)

    private val tasks = mutableListOf<Task>()
    private val cancelled = mutableSetOf<Long>()
    private var nextId = 0L
    private var now = 0L

    override fun postDelayed(delayMs: Long, action: () -> Unit): Scheduler.Cancellable {
        val id = nextId++
        tasks.add(Task(now + delayMs, id, action))
        return object : Scheduler.Cancellable {
            override fun cancel() {
                cancelled.add(id)
            }
        }
    }

    fun advanceBy(deltaMs: Long) {
        val target = now + deltaMs
        while (true) {
            val next = tasks
                .asSequence()
                .filter { it.id !in cancelled && it.time <= target }
                .minByOrNull { it.time } ?: break
            tasks.remove(next)
            now = next.time
            next.action()
        }
        now = target
    }
}
