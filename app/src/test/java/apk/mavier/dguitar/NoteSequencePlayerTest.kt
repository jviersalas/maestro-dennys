package apk.mavier.dguitar

import apk.mavier.dguitar.presentation.common.NoteSequencePlayer
import apk.mavier.dguitar.presentation.common.NoteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSequencePlayerTest {

    private class Recorder : NoteSequencePlayer.Listener {
        val steps = mutableListOf<Int>()
        var finished = false

        override fun onNoteStep(step: NoteStep, index: Int) {
            steps.add(step.midi)
        }

        override fun onFinished() {
            finished = true
        }
    }

    private fun steps(count: Int): List<NoteStep> =
        List(count) { NoteStep(stringIndex = it % 6, fret = it, midi = 60 + it) }

    @Test
    fun playsEveryStepOnItsDelay() {
        val scheduler = TestScheduler()
        val player = NoteSequencePlayer(scheduler)
        val recorder = Recorder()
        player.listener = recorder

        player.play(steps(3), stepDelayMs = 100)

        scheduler.advanceBy(0)
        assertEquals(listOf(60), recorder.steps)

        scheduler.advanceBy(100)
        assertEquals(listOf(60, 61), recorder.steps)

        scheduler.advanceBy(100)
        assertEquals(listOf(60, 61, 62), recorder.steps)

        scheduler.advanceBy(100)
        assertTrue(recorder.finished)
        assertFalse(player.isPlaying)
    }

    @Test
    fun stopCancelsTheRemainingSteps() {
        val scheduler = TestScheduler()
        val player = NoteSequencePlayer(scheduler)
        val recorder = Recorder()
        player.listener = recorder

        player.play(steps(3), stepDelayMs = 100)
        scheduler.advanceBy(0)
        player.stop()
        scheduler.advanceBy(10_000)

        assertEquals(listOf(60), recorder.steps)
        assertFalse(recorder.finished)
        assertFalse(player.isPlaying)
    }

    @Test
    fun emptySequenceDoesNothing() {
        val scheduler = TestScheduler()
        val player = NoteSequencePlayer(scheduler)
        val recorder = Recorder()
        player.listener = recorder

        player.play(emptyList(), stepDelayMs = 100)

        assertFalse(player.isPlaying)
        assertFalse(recorder.finished)
    }
}
