package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.Song
import apk.mavier.dguitar.domain.model.SongBar
import apk.mavier.dguitar.domain.model.SongChord
import apk.mavier.dguitar.domain.model.SongPart
import apk.mavier.dguitar.domain.model.SongPartType
import apk.mavier.dguitar.domain.model.SongStep
import apk.mavier.dguitar.domain.model.Songbook
import apk.mavier.dguitar.presentation.songbook.SongPlaybackController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongPlaybackControllerTest {

    private class Recorder : SongPlaybackController.Listener {
        val chords = mutableListOf<Int>()
        val progress = mutableListOf<Int>()
        var finished = false

        override fun onChordChanged(stepIndex: Int) {
            chords.add(stepIndex)
        }

        override fun onProgress(progress: Int) {
            this.progress.add(progress)
        }

        override fun onFinished() {
            finished = true
        }
    }

    private fun steps(count: Int = 2): List<SongStep> {
        val song = Song(
            id = "song",
            title = "Canción",
            parts = listOf(
                SongPart(
                    name = "Verso",
                    type = SongPartType.VERSO,
                    bars = (0 until count)
                        .map { SongChord(it, Chords.MAJOR) }
                        .chunked(4)
                        .map { SongBar(it) }
                )
            )
        )
        return Songbook.steps(song)
    }

    @Test
    fun startsOnTheRequestedChord() {
        val scheduler = TestScheduler()
        val controller = SongPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder

        controller.start(steps(), startIndex = 0, bpm = 120)

        assertEquals(listOf(0), recorder.chords)
        assertTrue(controller.isRunning)
    }

    @Test
    fun advancesOneChordAfterItsShareOfTheBar() {
        val scheduler = TestScheduler()
        val controller = SongPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(steps(), startIndex = 0, bpm = 120)

        scheduler.advanceBy(999)
        assertEquals(listOf(0), recorder.chords)

        scheduler.advanceBy(1)
        assertEquals(listOf(0, 1), recorder.chords)
    }

    @Test
    fun reportsProgressAcrossTheChord() {
        val scheduler = TestScheduler()
        val controller = SongPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(steps(), startIndex = 0, bpm = 120)

        scheduler.advanceBy(500)
        assertEquals(500, recorder.progress.last())
        assertEquals(500, controller.progress)
    }

    @Test
    fun finishesAfterTheLastChord() {
        val scheduler = TestScheduler()
        val controller = SongPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(steps(), startIndex = 0, bpm = 120)

        scheduler.advanceBy(2_000)

        assertTrue(recorder.finished)
        assertFalse(controller.isRunning)
        assertEquals(0, controller.progress)
    }

    @Test
    fun stopCancelsPendingWork() {
        val scheduler = TestScheduler()
        val controller = SongPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(steps(), startIndex = 0, bpm = 120)

        scheduler.advanceBy(500)
        controller.stop()
        scheduler.advanceBy(10_000)

        assertEquals(listOf(0), recorder.chords)
        assertFalse(recorder.finished)
        assertFalse(controller.isRunning)
    }
}
