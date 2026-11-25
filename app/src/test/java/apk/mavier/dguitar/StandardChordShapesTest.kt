package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.StandardChordShapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandardChordShapesTest {

    @Test
    fun dMajorIsXX0232() {
        val shape = StandardChordShapes.shapeFor(2, Chords.MAJOR)
        assertEquals(listOf<Int?>(null, null, 0, 2, 3, 2), shape?.fretsLowToHigh)
        assertNull(shape?.barreFret)
    }

    @Test
    fun aMajorIsX02220() {
        val shape = StandardChordShapes.shapeFor(9, Chords.MAJOR)
        assertEquals(listOf<Int?>(null, 0, 2, 2, 2, 0), shape?.fretsLowToHigh)
    }

    @Test
    fun cMajorIsX32010() {
        val shape = StandardChordShapes.shapeFor(0, Chords.MAJOR)
        assertEquals(listOf<Int?>(null, 3, 2, 0, 1, 0), shape?.fretsLowToHigh)
    }

    @Test
    fun fMajorHasBarreAtFirstFret() {
        val shape = StandardChordShapes.shapeFor(5, Chords.MAJOR)
        assertEquals(listOf<Int?>(1, 3, 3, 2, 1, 1), shape?.fretsLowToHigh)
        assertEquals(1, shape?.barreFret)
    }

    @Test
    fun bMinorHasBarreAtSecondFret() {
        val shape = StandardChordShapes.shapeFor(11, Chords.MINOR)
        assertEquals(listOf<Int?>(null, 2, 4, 4, 3, 2), shape?.fretsLowToHigh)
        assertEquals(2, shape?.barreFret)
    }

    @Test
    fun g7IsOpen() {
        val shape = StandardChordShapes.shapeFor(7, Chords.DOMINANT_7)
        assertEquals(listOf<Int?>(3, 2, 0, 0, 0, 1), shape?.fretsLowToHigh)
    }

    @Test
    fun unmappedQualityFallsBack() {
        assertNull(StandardChordShapes.shapeFor(0, Chords.MAJOR_7))
    }

    @Test
    fun missingMajorRootsUseStandardBarreShapes() {
        assertEquals(listOf<Int?>(null, 4, 6, 6, 6, 4), StandardChordShapes.shapeFor(1, Chords.MAJOR)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(null, 6, 8, 8, 8, 6), StandardChordShapes.shapeFor(3, Chords.MAJOR)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(2, 4, 4, 3, 2, 2), StandardChordShapes.shapeFor(6, Chords.MAJOR)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(4, 6, 6, 5, 4, 4), StandardChordShapes.shapeFor(8, Chords.MAJOR)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(null, 1, 3, 3, 3, 1), StandardChordShapes.shapeFor(10, Chords.MAJOR)?.fretsLowToHigh)
    }

    @Test
    fun missingMinorRootsUseStandardBarreShapes() {
        assertEquals(listOf<Int?>(null, 3, 5, 5, 4, 3), StandardChordShapes.shapeFor(0, Chords.MINOR)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(null, 6, 8, 8, 7, 6), StandardChordShapes.shapeFor(3, Chords.MINOR)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(1, 3, 3, 1, 1, 1), StandardChordShapes.shapeFor(5, Chords.MINOR)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(null, 1, 3, 3, 2, 1), StandardChordShapes.shapeFor(10, Chords.MINOR)?.fretsLowToHigh)
    }

    @Test
    fun dRowUsesNaturalOpenShapes() {
        assertEquals(listOf<Int?>(null, null, 0, 2, 0, 2), StandardChordShapes.shapeFor(2, Chords.SIXTH)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(null, null, 0, 2, 0, 1), StandardChordShapes.shapeFor(2, Chords.MINOR_6)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(null, null, 0, 2, 1, 1), StandardChordShapes.shapeFor(2, Chords.MINOR_7)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(null, null, 0, 2, 2, 2), StandardChordShapes.shapeFor(2, Chords.MAJOR_7)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(null, null, 0, 1, 3, 1), StandardChordShapes.shapeFor(2, Chords.DIMINISHED)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(null, null, 0, 3, 3, 2), StandardChordShapes.shapeFor(2, Chords.AUGMENTED)?.fretsLowToHigh)
        assertEquals(listOf<Int?>(null, null, 0, 2, 3, 3), StandardChordShapes.shapeFor(2, Chords.SUS_4)?.fretsLowToHigh)
    }
}
