package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.presentation.ui.FretboardRenderModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FretboardRenderModelTest {

    @Test
    fun circleChordsProduceHitsPerPitchClass() {
        val config = FretboardConfig(
            mode = ViewMode.SCALE,
            rootPc = 0,
            scale = Scales.MAJOR,
            circleDegrees = (1..7).toSet()
        )
        val model = FretboardRenderModel.build(config)
        assertEquals(7, model.overlayChords.size)
        assertTrue(model.hitsFor(0).isNotEmpty())
        assertTrue(model.hitsFor(4).isNotEmpty())
        assertTrue(model.hitsFor(1).isEmpty())
    }

    @Test
    fun selectedPatternIsResolvedOnceIntoTheModel() {
        val config = FretboardConfig(
            mode = ViewMode.SCALE,
            selectedPattern = 1
        )
        val model = FretboardRenderModel.build(config)
        assertEquals(1, model.selectedOccurrences.size)
        assertTrue(model.selectedOccurrences.all { it.number == 1 })
    }

    @Test
    fun selectedStartFretKeepsOnlyThatOctave() {
        val config = FretboardConfig(
            mode = ViewMode.SCALE,
            selectedPattern = 1,
            selectedPatternStartFret = 12
        )
        val model = FretboardRenderModel.build(config)
        assertEquals(1, model.selectedOccurrences.size)
        assertEquals(12, model.selectedOccurrences.first().startFret)
    }

    @Test
    fun chordModeDoesNotResolveScalePatterns() {
        val config = FretboardConfig(
            mode = ViewMode.CHORD,
            selectedPattern = 1
        )
        val model = FretboardRenderModel.build(config)
        assertTrue(model.selectedOccurrences.isEmpty())
    }
}
