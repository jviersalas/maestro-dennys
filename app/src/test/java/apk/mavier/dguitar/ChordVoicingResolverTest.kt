package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.ChordVoicingResolver
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.domain.model.VoicingKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordVoicingResolverTest {

    private fun config(
        rootPc: Int,
        chord: apk.mavier.dguitar.domain.model.ChordQuality = Chords.MAJOR,
        pattern: Int? = null,
        tuning: apk.mavier.dguitar.domain.model.Tuning = Tunings.STANDARD,
        mode: ViewMode = ViewMode.CHORD
    ) = FretboardConfig(
        mode = mode,
        rootPc = rootPc,
        chord = chord,
        tuning = tuning,
        chordPattern = pattern
    )

    @Test
    fun cMajorUsesTheStandardOpenShape() {
        val voicing = ChordVoicingResolver.voicing(config(0), centerFret = 0f)
        assertEquals(
            setOf(
                VoicingKey(4, 3),
                VoicingKey(3, 2),
                VoicingKey(2, 0),
                VoicingKey(1, 1),
                VoicingKey(0, 0)
            ),
            voicing.positions
        )
        assertNull(voicing.barreFret)
    }

    @Test
    fun fMajorIsABarreAcrossAllStrings() {
        val voicing = ChordVoicingResolver.voicing(config(5), centerFret = 0f)
        assertEquals(1, voicing.barreFret)
        assertTrue(voicing.barreAllStrings)
        assertEquals(6, voicing.positions.size)
    }

    @Test
    fun nonStandardTuningFallsBackToTheHeuristic() {
        val voicing = ChordVoicingResolver.voicing(
            config(0, tuning = Tunings.DADGAD),
            centerFret = 0f
        )
        assertTrue(voicing.positions.isNotEmpty())
        assertTrue(voicing.positions.all { it.fret in 0..22 })
    }

    @Test
    fun nonChordModesHaveNoVoicing() {
        val voicing = ChordVoicingResolver.voicing(
            config(0, mode = ViewMode.SCALE),
            centerFret = 0f
        )
        assertTrue(voicing.positions.isEmpty())
    }

    @Test
    fun barreIsDetectedWhenThreeOrMoreStringsShareAFret() {
        val formation = setOf(
            VoicingKey(5, 1),
            VoicingKey(4, 1),
            VoicingKey(3, 1),
            VoicingKey(2, 2),
            VoicingKey(1, 1),
            VoicingKey(0, 1)
        )
        assertEquals(1, ChordVoicingResolver.detectBarreFret(formation))
    }

    @Test
    fun activeOccurrenceFollowsTheViewport() {
        val cfg = config(0, pattern = 3)
        val occurrence = ChordVoicingResolver.activeChordOccurrence(cfg, centerFret = 7f)
        assertNotNull(occurrence)
        assertEquals(5, occurrence!!.startFret)
        assertEquals(9, occurrence.endFret)
    }
}
