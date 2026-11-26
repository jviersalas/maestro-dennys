package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.CircleOfFifths
import apk.mavier.dguitar.domain.model.Scales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleOfFifthsTest {

    @Test
    fun keysFollowTheCircleOfFifths() {
        assertEquals(
            listOf(0, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10, 5),
            CircleOfFifths.keys.map { it.majorPc }
        )
    }

    @Test
    fun relativeMinorIsThreeSemitonesBelowTheMajor() {
        CircleOfFifths.keys.forEach { key ->
            assertEquals((key.majorPc + 9) % 12, key.minorPc)
        }
        assertEquals("Am", CircleOfFifths.byIndex(0).minorName(useFlats = false))
        assertEquals("Em", CircleOfFifths.byIndex(1).minorName(useFlats = false))
    }

    @Test
    fun keySignaturesMatchTheTheory() {
        val c = CircleOfFifths.byIndex(0)
        assertTrue(c.signature(useFlats = false).isNatural)

        val g = CircleOfFifths.byIndex(1)
        assertEquals(listOf("F#"), g.signature(useFlats = false).accidentals)

        val d = CircleOfFifths.byIndex(2)
        assertEquals(listOf("F#", "C#"), d.signature(useFlats = false).accidentals)

        val f = CircleOfFifths.byIndex(11)
        assertEquals(listOf("Bb"), f.signature(useFlats = false).accidentals)
        assertFalse(f.signature(useFlats = false).sharps)

        val ab = CircleOfFifths.byIndex(8)
        assertEquals(listOf("Bb", "Eb", "Ab", "Db"), ab.signature(useFlats = false).accidentals)
    }

    @Test
    fun enharmonicPositionsKeepBothSpellings() {
        val fSharp = CircleOfFifths.byIndex(6)
        assertEquals("F#", fSharp.majorName(useFlats = false))
        assertEquals("Gb", fSharp.majorName(useFlats = true))
        assertEquals("D#m", fSharp.minorName(useFlats = false))
        assertEquals("Ebm", fSharp.minorName(useFlats = true))
        assertEquals(6, fSharp.signature(useFlats = false).count)
        assertEquals(6, fSharp.signature(useFlats = true).count)
        assertTrue(fSharp.signature(useFlats = false).sharps)
        assertFalse(fSharp.signature(useFlats = true).sharps)

        val cSharp = CircleOfFifths.byIndex(7)
        assertEquals("C#", cSharp.majorName(useFlats = false))
        assertEquals("Db", cSharp.majorName(useFlats = true))
        assertEquals(7, cSharp.signature(useFlats = false).count)
        assertEquals(5, cSharp.signature(useFlats = true).count)

        val aFlat = CircleOfFifths.byIndex(8)
        assertEquals("Ab", aFlat.majorName(useFlats = false))
        assertEquals("Ab", aFlat.majorName(useFlats = true))
    }

    @Test
    fun cMajorHasTheExpectedDiatonicChords() {
        val key = CircleOfFifths.byIndex(0)
        val chords = CircleOfFifths.diatonicChords(key, minor = false)
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
    fun gMajorHasFSharpDiminished() {
        val key = CircleOfFifths.byIndex(1)
        val chords = CircleOfFifths.diatonicChords(key, minor = false)
        assertEquals(listOf(7, 9, 11, 0, 2, 4, 6), chords.map { it.rootPc })
        assertEquals(Chords.DIMINISHED, chords[6].quality)
    }

    @Test
    fun aNaturalMinorHasTheExpectedDiatonicChords() {
        val key = CircleOfFifths.byIndex(0)
        val chords = CircleOfFifths.diatonicChords(key, minor = true)
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
    fun romanNumeralsReflectChordQuality() {
        val major = CircleOfFifths.diatonicChords(CircleOfFifths.byIndex(0), minor = false)
        assertEquals(
            listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"),
            major.mapIndexed { index, chord ->
                CircleOfFifths.romanFor(index, chord.quality)
            }
        )

        val minor = CircleOfFifths.diatonicChords(CircleOfFifths.byIndex(0), minor = true)
        assertEquals(
            listOf("i", "ii°", "III", "iv", "v", "VI", "VII"),
            minor.mapIndexed { index, chord ->
                CircleOfFifths.romanFor(index, chord.quality)
            }
        )
    }

    @Test
    fun spelledScalesRespectTheKeyLetter() {
        val c = CircleOfFifths.byIndex(0)
        assertEquals(
            listOf("C", "D", "E", "F", "G", "A", "B"),
            c.spelledScale(useFlats = false, minor = false)
        )

        val g = CircleOfFifths.byIndex(1)
        assertEquals(
            listOf("G", "A", "B", "C", "D", "E", "F#"),
            g.spelledScale(useFlats = false, minor = false)
        )

        val fSharp = CircleOfFifths.byIndex(6)
        assertEquals(
            listOf("F#", "G#", "A#", "B", "C#", "D#", "E#"),
            fSharp.spelledScale(useFlats = false, minor = false)
        )
        assertEquals(
            listOf("Gb", "Ab", "Bb", "Cb", "Db", "Eb", "F"),
            fSharp.spelledScale(useFlats = true, minor = false)
        )

        val aMinor = CircleOfFifths.byIndex(0).spelledScale(useFlats = false, minor = true)
        assertEquals(
            listOf("A", "B", "C", "D", "E", "F", "G"),
            aMinor
        )
        assertEquals(Scales.NATURAL_MINOR, CircleOfFifths.byIndex(0).modeScale(minor = true))
    }

    @Test
    fun majorPitchClassMapsToTheRightSector() {
        assertEquals(1, CircleOfFifths.indexForMajorPc(7))
        assertEquals(0, CircleOfFifths.indexForMajorPc(0))
        assertEquals(11, CircleOfFifths.indexForMajorPc(5))
    }

    @Test
    fun progressionsExistForBothModes() {
        assertTrue(CircleOfFifths.progressions(minor = false).isNotEmpty())
        assertTrue(CircleOfFifths.progressions(minor = true).isNotEmpty())
    }

    @Test
    fun cMajorLightsTheHarmonicCluster() {
        val key = CircleOfFifths.byIndex(0)
        val highlight = CircleOfFifths.highlight(key, minor = false)
        assertEquals(setOf(0, 11, 1), highlight.majorIndices)
        assertEquals(setOf(0, 11, 1), highlight.minorIndices)
    }

    @Test
    fun aMinorLightsTheSameHarmonicCluster() {
        val key = CircleOfFifths.byIndex(0)
        val highlight = CircleOfFifths.highlight(key, minor = true)
        assertEquals(setOf(0, 11, 1), highlight.majorIndices)
        assertEquals(setOf(0, 11, 1), highlight.minorIndices)
    }
}
