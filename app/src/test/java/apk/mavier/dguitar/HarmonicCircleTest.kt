package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.HarmonicCircle
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.model.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarmonicCircleTest {

    @Test
    fun cMajorTriadsFollowTheDiatonicGrades() {
        val chords = HarmonicCircle.chords(Scales.MAJOR, 0)
        assertEquals(7, chords.size)
        assertEquals(listOf(0, 2, 4, 5, 7, 9, 11), chords.map { it.rootPc })
        assertEquals(
            listOf(
                Chords.MAJOR,
                Chords.MINOR,
                Chords.MINOR,
                Chords.MAJOR,
                Chords.MAJOR,
                Chords.MINOR,
                Chords.DIMINISHED
            ),
            chords.map { it.quality }
        )
    }

    @Test
    fun cMajorSeventhsFollowTheDiatonicGrades() {
        val chords = HarmonicCircle.chords(Scales.MAJOR, 0, sevenths = true)
        assertEquals(
            listOf(
                Chords.MAJOR_7,
                Chords.MINOR_7,
                Chords.MINOR_7,
                Chords.MAJOR_7,
                Chords.DOMINANT_7,
                Chords.MINOR_7,
                Chords.HALF_DIMINISHED
            ),
            chords.map { it.quality }
        )
    }

    @Test
    fun aNaturalMinorUsesTheRelativeMajorQualities() {
        val chords = HarmonicCircle.chords(Scales.NATURAL_MINOR, 9)
        assertEquals(listOf(9, 11, 0, 2, 4, 5, 7), chords.map { it.rootPc })
        assertEquals(
            listOf(
                Chords.MINOR,
                Chords.DIMINISHED,
                Chords.MAJOR,
                Chords.MINOR,
                Chords.MINOR,
                Chords.MAJOR,
                Chords.MAJOR
            ),
            chords.map { it.quality }
        )
    }

    @Test
    fun aHarmonicMinorHasAugmentedAndDiminishedGrades() {
        val chords = HarmonicCircle.chords(Scales.HARMONIC_MINOR, 9)
        assertEquals(Chords.MINOR, chords[0].quality)
        assertEquals(Chords.DIMINISHED, chords[1].quality)
        assertEquals(Chords.AUGMENTED, chords[2].quality)
        assertEquals(Chords.MINOR, chords[3].quality)
        assertEquals(Chords.MAJOR, chords[4].quality)
        assertEquals(Chords.MAJOR, chords[5].quality)
        assertEquals(Chords.DIMINISHED, chords[6].quality)
    }

    @Test
    fun dMajorLetsSelectBmGDA() {
        val config = FretboardConfig(
            mode = ViewMode.SCALE,
            rootPc = 2,
            scale = Scales.MAJOR,
            circleDegrees = setOf(1, 4, 5, 6)
        )
        val chords = HarmonicCircle.effectiveChords(config)
        assertEquals(listOf(2, 7, 9, 11), chords.map { it.rootPc })
        assertEquals(
            listOf(Chords.MAJOR, Chords.MAJOR, Chords.MAJOR, Chords.MINOR),
            chords.map { it.quality }
        )
    }

    @Test
    fun overrideChangesOnlyTheSelectedDegree() {
        val config = FretboardConfig(
            mode = ViewMode.SCALE,
            rootPc = 0,
            scale = Scales.MAJOR,
            circleDegrees = setOf(1, 2),
            circleOverrides = mapOf(1 to Chords.MINOR)
        )
        val chords = HarmonicCircle.effectiveChords(config)
        assertEquals(2, chords.size)
        assertEquals(Chords.MINOR, chords[0].quality)
        assertEquals(Chords.MINOR, chords[1].quality)
    }

    @Test
    fun nonHeptatonicScalesHaveNoCircle() {
        assertTrue(HarmonicCircle.chords(Scales.PENTATONIC_MAJOR, 0).isEmpty())
        assertTrue(HarmonicCircle.chords(Scales.BLUES_MINOR, 0).isEmpty())
    }
}
