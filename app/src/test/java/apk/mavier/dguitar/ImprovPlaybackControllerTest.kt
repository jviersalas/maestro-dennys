package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.ImprovBar
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.Improvisation
import apk.mavier.dguitar.presentation.improvisation.ImprovPlaybackController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImprovPlaybackControllerTest {

    private class Recorder : ImprovPlaybackController.Listener {
        val bars = mutableListOf<Int>()
        val progress = mutableListOf<Int>()
        var finished = false

        override fun onBarChanged(barIndex: Int) {
            bars.add(barIndex)
        }

        override fun onProgress(progress: Int) {
            this.progress.add(progress)
        }

        override fun onFinished() {
            finished = true
        }
    }

    private fun bars(count: Int = 2): List<ImprovBar> {
        val chords = (0 until count).map { ImprovChord(it, Chords.MAJOR) }
        return Improvisation.bars(listOf(ImprovSection("A", chords)))
    }

    @Test
    fun startsOnTheRequestedBar() {
        val scheduler = TestScheduler()
        val controller = ImprovPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder

        controller.start(bars(), startBar = 0, bpm = 120)

        assertEquals(listOf(0), recorder.bars)
        assertTrue(controller.isRunning)
    }

    @Test
    fun advancesOneBarEveryTwoSecondsAt120Bpm() {
        val scheduler = TestScheduler()
        val controller = ImprovPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(bars(), startBar = 0, bpm = 120)

        scheduler.advanceBy(1_999)
        assertEquals(listOf(0), recorder.bars)

        scheduler.advanceBy(1)
        assertEquals(listOf(0, 1), recorder.bars)
    }

    @Test
    fun reportsProgressAcrossTheBar() {
        val scheduler = TestScheduler()
        val controller = ImprovPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(bars(), startBar = 0, bpm = 120)

        scheduler.advanceBy(1_000)
        assertEquals(500, recorder.progress.last())
        assertEquals(500, controller.progress)
    }

    @Test
    fun finishesAfterTheLastBar() {
        val scheduler = TestScheduler()
        val controller = ImprovPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(bars(), startBar = 0, bpm = 120)

        scheduler.advanceBy(4_000)

        assertTrue(recorder.finished)
        assertFalse(controller.isRunning)
        assertEquals(0, controller.progress)
    }

    @Test
    fun stopCancelsPendingWork() {
        val scheduler = TestScheduler()
        val controller = ImprovPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(bars(), startBar = 0, bpm = 120)

        scheduler.advanceBy(500)
        controller.stop()
        scheduler.advanceBy(10_000)

        assertEquals(listOf(0), recorder.bars)
        assertFalse(recorder.finished)
        assertFalse(controller.isRunning)
    }
}
