package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Scales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalesCatalogTest {

    @Test
    fun includesTheFullCatalog() {
        assertTrue(Scales.all.size >= 50)
        assertEquals(Scales.all.size, Scales.all.map { it.name }.distinct().size)
    }

    @Test
    fun bluesMinorIntervals() {
        assertEquals(listOf(0, 3, 5, 6, 7, 10), Scales.BLUES_MINOR.intervals)
    }

    @Test
    fun bebopAndChromaticHaveExtraNotes() {
        assertEquals(8, Scales.BEBOP_DOMINANT.intervals.size)
        assertEquals(12, Scales.CHROMATIC.intervals.size)
    }

    @Test
    fun dorianSequenceGoesUpAndDown() {
        assertEquals(
            listOf(0, 2, 3, 5, 7, 9, 10, 9, 7, 5, 3, 2, 0),
            Scales.DORIAN.exampleSequence
        )
    }

    @Test
    fun bluesSequenceMatchesTheTable() {
        assertEquals(
            listOf(0, 3, 5, 6, 7, 10, 7, 6, 5, 3, 0),
            Scales.BLUES_MINOR.exampleSequence
        )
    }

    @Test
    fun everyScaleHasFormulaAndSensibleIntervals() {
        for (scale in Scales.all) {
            assertTrue("Falta fórmula en ${scale.name}", scale.formula.isNotBlank())
            assertTrue("Intervalos vacíos en ${scale.name}", scale.intervals.isNotEmpty())
            assertTrue(
                "Intervalos fuera de rango en ${scale.name}",
                scale.intervals.all { it in 0..11 }
            )
            assertEquals(
                "Secuencia incorrecta en ${scale.name}",
                scale.intervals.size * 2 - 1,
                scale.exampleSequence.size
            )
        }
    }
}
