package apk.mavier.dguitar

import apk.mavier.dguitar.presentation.ui.FretboardGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FretboardGeometryTest {

    private val delta = 0.001f

    @Test
    fun sixStringsUseTheFretboardMeasurements() {
        val geometry = FretboardGeometry.compute(
            width = 1000,
            height = 600,
            stringCount = 6,
            density = 1f,
            fretCount = 22
        )
        assertNotNull(geometry)
        geometry!!
        assertEquals(50f, geometry.gutterWidth, delta)
        assertEquals(64f, geometry.fretWidth, delta)
        assertEquals(54f, geometry.boardStartX, delta)
        assertEquals(
            listOf(22f, 124.4f, 226.8f, 329.2f, 431.6f, 534f),
            geometry.stringYs.toList()
        )
        assertEquals(21.76f, geometry.noteRadius, delta)
        assertEquals(462f, geometry.maxScroll, delta)
    }

    @Test
    fun singleStringIsCenteredVertically() {
        val geometry = FretboardGeometry.compute(
            width = 1000,
            height = 600,
            stringCount = 1,
            density = 1f,
            fretCount = 22
        )!!
        assertEquals(listOf(278f), geometry.stringYs.toList())
        assertEquals(15.2f, geometry.noteRadius, delta)
    }

    @Test
    fun fretPositionsFollowTheScrollOffset() {
        val geometry = FretboardGeometry.compute(1000, 600, 6, 1f, 22)!!
        assertEquals(108f, geometry.fretX(1, 10f), delta)
        assertEquals(76f, geometry.cellCenterX(1, 10f), delta)
    }

    @Test
    fun stringThicknessGrowsFromHighToLowStrings() {
        val geometry = FretboardGeometry.compute(1000, 600, 6, 1f, 22)!!
        assertEquals(1.4f, geometry.stringThickness(0, 1f), delta)
        assertEquals(4.6f, geometry.stringThickness(5, 1f), delta)
    }

    @Test
    fun singleStringHasAFixedThickness() {
        val geometry = FretboardGeometry.compute(1000, 600, 1, 1f, 22)!!
        assertEquals(3f, geometry.stringThickness(0, 1f), delta)
    }

    @Test
    fun scrollIsClampedToTheAvailableRange() {
        val geometry = FretboardGeometry.compute(1000, 600, 6, 1f, 22)!!
        assertEquals(0f, geometry.clampScroll(-30f), delta)
        assertEquals(462f, geometry.clampScroll(900f), delta)
        assertEquals(200f, geometry.clampScroll(200f), delta)
    }

    @Test
    fun zeroSizedViewsHaveNoGeometryYet() {
        assertEquals(null, FretboardGeometry.compute(0, 600, 6, 1f, 22))
        assertEquals(null, FretboardGeometry.compute(1000, 0, 6, 1f, 22))
    }
}
