package apk.mavier.dguitar

import apk.mavier.dguitar.presentation.common.PlaybackTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTimelineTest {

    private class Recorder : PlaybackTimeline.Listener<Int> {
        val steps = mutableListOf<Int>()
        val progress = mutableListOf<Int>()
        var finished = false

        override fun onStep(index: Int, item: Int) {
            steps.add(index)
        }

        override fun onProgress(progress: Int) {
            this.progress.add(progress)
        }

        override fun onFinished() {
            finished = true
        }
    }

    private fun timeline(scheduler: TestScheduler): PlaybackTimeline<Int> =
        PlaybackTimeline(
            scheduler = scheduler,
            durationMs = { _, _ -> 1_000L }
        )

    @Test
    fun startsOnRequestedItemAndAdvances() {
        val scheduler = TestScheduler()
        val timeline = timeline(scheduler)
        val recorder = Recorder()
        timeline.listener = recorder

        timeline.start(listOf(10, 20), startIndex = 0, bpm = 120)
        assertEquals(listOf(0), recorder.steps)
        assertTrue(timeline.isRunning)

        scheduler.advanceBy(999)
        assertEquals(listOf(0), recorder.steps)

        scheduler.advanceBy(1)
        assertEquals(listOf(0, 1), recorder.steps)
    }

    @Test
    fun reportsProgressUntilTheItemEnds() {
        val scheduler = TestScheduler()
        val timeline = timeline(scheduler)
        val recorder = Recorder()
        timeline.listener = recorder
        timeline.start(listOf(10, 20), startIndex = 0, bpm = 120)

        scheduler.advanceBy(500)

        assertEquals(500, recorder.progress.last())
        assertEquals(500, timeline.progress)
    }

    @Test
    fun finishesAfterTheLastItem() {
        val scheduler = TestScheduler()
        val timeline = timeline(scheduler)
        val recorder = Recorder()
        timeline.listener = recorder
        timeline.start(listOf(10, 20), startIndex = 0, bpm = 120)

        scheduler.advanceBy(2_000)

        assertTrue(recorder.finished)
        assertFalse(timeline.isRunning)
        assertEquals(0, timeline.progress)
    }

    @Test
    fun stopCancelsPendingWork() {
        val scheduler = TestScheduler()
        val timeline = timeline(scheduler)
        val recorder = Recorder()
        timeline.listener = recorder
        timeline.start(listOf(10, 20), startIndex = 0, bpm = 120)

        scheduler.advanceBy(500)
        timeline.stop()
        scheduler.advanceBy(10_000)

        assertEquals(listOf(0), recorder.steps)
        assertFalse(recorder.finished)
        assertFalse(timeline.isRunning)
    }
}
