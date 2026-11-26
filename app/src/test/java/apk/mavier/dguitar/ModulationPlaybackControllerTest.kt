package apk.mavier.dguitar

import apk.mavier.dguitar.presentation.modulation.ModulationPlaybackController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModulationPlaybackControllerTest {

    private class Recorder : ModulationPlaybackController.Listener {
        val chords = mutableListOf<Int>()
        val beats = mutableListOf<Pair<Int, Int>>()
        var finished = false

        override fun onChordStarted(index: Int) {
            chords.add(index)
        }

        override fun onBeat(chordIndex: Int, beatInChord: Int, accent: Boolean) {
            beats.add(chordIndex to beatInChord)
        }

        override fun onFinished() {
            finished = true
        }
    }

    @Test
    fun startsTheFirstChordImmediately() {
        val controller = ModulationPlaybackController(TestScheduler())
        val recorder = Recorder()
        controller.listener = recorder

        controller.start(chordCount = 3, bpm = 120)

        assertEquals(listOf(0), recorder.chords)
        assertEquals(listOf(0 to 0), recorder.beats)
        assertTrue(controller.isRunning)
    }

    @Test
    fun movesToTheNextChordAfterTheConfiguredBeats() {
        val scheduler = TestScheduler()
        val controller = ModulationPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(chordCount = 3, bpm = 120, beatsPerChord = 2)

        scheduler.advanceBy(500)
        assertEquals(listOf(0), recorder.chords)

        scheduler.advanceBy(500)
        assertEquals(listOf(0, 1), recorder.chords)
        assertEquals(1 to 0, recorder.beats.last())
    }

    @Test
    fun finishesAfterTheLastChord() {
        val scheduler = TestScheduler()
        val controller = ModulationPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(chordCount = 2, bpm = 120, beatsPerChord = 2)

        scheduler.advanceBy(2_000)

        assertTrue(recorder.finished)
        assertFalse(controller.isRunning)
    }

    @Test
    fun stopCancelsPendingBeats() {
        val scheduler = TestScheduler()
        val controller = ModulationPlaybackController(scheduler)
        val recorder = Recorder()
        controller.listener = recorder
        controller.start(chordCount = 3, bpm = 120, beatsPerChord = 2)

        controller.stop()
        scheduler.advanceBy(5_000)

        assertEquals(listOf(0), recorder.chords)
        assertFalse(recorder.finished)
    }
}
