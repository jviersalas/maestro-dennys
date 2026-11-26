package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.ChromaticVariant
import apk.mavier.dguitar.domain.model.ModulationSignal
import apk.mavier.dguitar.domain.model.ModulationTheory
import apk.mavier.dguitar.domain.model.ModulationType
import apk.mavier.dguitar.domain.model.NoteRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModulationTheoryTest {

    @Test
    fun distanceFollowsTheCircleOfFifths() {
        assertEquals(1, ModulationTheory.distance(0, 7))
        assertEquals(2, ModulationTheory.distance(0, 2))
        assertEquals(5, ModulationTheory.distance(0, 1))
        assertEquals(0, ModulationTheory.distance(0, 0))
    }

    @Test
    fun signalsGroupDistanceIntoTiers() {
        assertEquals(ModulationSignal.NEAR, ModulationTheory.signalFor(0))
        assertEquals(ModulationSignal.NEAR, ModulationTheory.signalFor(1))
        assertEquals(ModulationSignal.MEDIUM, ModulationTheory.signalFor(2))
        assertEquals(ModulationSignal.MEDIUM, ModulationTheory.signalFor(3))
        assertEquals(ModulationSignal.FAR, ModulationTheory.signalFor(4))
        assertEquals(ModulationSignal.FAR, ModulationTheory.signalFor(5))
    }

    @Test
    fun cToGBuildsTheClassicPivotBridge() {
        val bridge = ModulationTheory.bridge(0, 7, ModulationType.PIVOT)

        assertEquals(ModulationType.PIVOT, bridge.type)
        assertEquals(listOf(0, 9, 2, 7), bridge.steps.map { it.rootPc })
        assertEquals(listOf("C", "Am", "D7", "G"), bridge.steps.map { it.label(false) })
        assertEquals("vi", bridge.steps[1].originRoman)
        assertEquals("ii", bridge.steps[1].destinationRoman)
        assertTrue(bridge.steps[1].isPivot)
        assertEquals(2, bridge.changeIndex)
        assertTrue(bridge.changeStep?.belongsToDestination == true)
        assertFalse(bridge.changeStep?.belongsToOrigin == true)
    }

    @Test
    fun cToDUsesEmAsPivot() {
        val bridge = ModulationTheory.bridge(0, 2, ModulationType.PIVOT)

        assertEquals(listOf(0, 4, 9, 2), bridge.steps.map { it.rootPc })
        assertEquals(listOf("C", "Em", "A7", "D"), bridge.steps.map { it.label(false) })
        assertEquals("iii", bridge.steps[1].originRoman)
        assertEquals("ii", bridge.steps[1].destinationRoman)
        assertEquals(2, ModulationTheory.distance(0, 2))
    }

    @Test
    fun cToFUsesDmAsPivot() {
        val bridge = ModulationTheory.bridge(0, 5, ModulationType.PIVOT)

        assertEquals(listOf(0, 2, 0, 5), bridge.steps.map { it.rootPc })
        assertEquals(listOf("C", "Dm", "C7", "F"), bridge.steps.map { it.label(false) })
        assertEquals("vi", bridge.steps[1].destinationRoman)
    }

    @Test
    fun cToDbHasNoDiatonicPivot() {
        val availability = ModulationTheory.availability(0, 1)
        assertFalse(availability.pivotAvailable)

        val bridge = ModulationTheory.bridge(0, 1, ModulationType.PIVOT)
        assertEquals(ModulationType.DIRECT, bridge.type)
        assertEquals(listOf(0, 1), bridge.steps.map { it.rootPc })
        assertEquals(5, bridge.distance)
        assertEquals(ModulationSignal.FAR, bridge.signal)
    }

    @Test
    fun chromaticOriginImpulseMatchesTheDocumentExample() {
        val bridge = ModulationTheory.bridge(
            originPc = 0,
            destinationPc = 1,
            type = ModulationType.CHROMATIC,
            chromaticVariant = ChromaticVariant.ORIGIN_IMPULSE
        )

        assertEquals(listOf(0, 7, 1), bridge.steps.map { it.rootPc })
        assertEquals(listOf("C", "G7", "Db"), bridge.steps.map { it.label(true) })
        assertEquals("V7", bridge.steps[1].originRoman)
        assertEquals(null, bridge.steps[1].destinationRoman)
        assertEquals(2, bridge.changeIndex)
    }

    @Test
    fun chromaticDestinationDominantUsesTheRealV7() {
        val bridge = ModulationTheory.bridge(
            originPc = 0,
            destinationPc = 1,
            type = ModulationType.CHROMATIC,
            chromaticVariant = ChromaticVariant.DESTINATION_DOMINANT
        )

        assertEquals(listOf(0, 8, 1), bridge.steps.map { it.rootPc })
        assertEquals(listOf("C", "Ab7", "Db"), bridge.steps.map { it.label(true) })
        assertEquals(null, bridge.steps[1].originRoman)
        assertEquals("V7", bridge.steps[1].destinationRoman)
        assertEquals(1, bridge.changeIndex)
    }

    @Test
    fun directBridgeChangesAtTheDestinationTonic() {
        val bridge = ModulationTheory.bridge(0, 7, ModulationType.DIRECT)
        assertEquals(listOf("C", "G"), bridge.steps.map { it.label(false) })
        assertEquals(1, bridge.changeIndex)
        assertEquals("G", bridge.changeStep?.label(false))
    }

    @Test
    fun scaleMapSeparatesCommonAndNewNotes() {
        val map = ModulationTheory.scaleMap(0, 7)

        assertEquals(setOf(0, 2, 4, 5, 7, 9, 11), map.originPcs)
        assertEquals(setOf(7, 9, 11, 0, 2, 4, 6), map.destinationPcs)
        assertEquals(setOf(0, 2, 4, 7, 9, 11), map.commonPcs)
        assertEquals(setOf(5), map.originOnlyPcs)
        assertEquals(setOf(6), map.destinationOnlyPcs)
        assertEquals(NoteRelation.COMMON, map.relationOf(0))
        assertEquals(NoteRelation.ORIGIN_ONLY, map.relationOf(5))
        assertEquals(NoteRelation.DESTINATION_ONLY, map.relationOf(6))
        assertEquals(null, map.relationOf(1))
    }

    @Test
    fun scaleMapForDistantKeysStillSharesSomeNotes() {
        val map = ModulationTheory.scaleMap(0, 1)
        assertEquals(setOf(0, 5), map.commonPcs)
        assertNotNull(map.relationOf(0))
    }

    @Test
    fun cadenceEstablishesTheKeyBeforeTheChange() {
        val cadence = ModulationTheory.cadence(0)
        assertEquals(listOf(0, 5, 7, 0), cadence.map { it.rootPc })
        assertEquals(Chords.DOMINANT_7, cadence[2].quality)
    }
}
