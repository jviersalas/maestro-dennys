package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.GuidedProgression
import apk.mavier.dguitar.domain.model.GuidedProgressions
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.usecase.BuildGuidedProgressionUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedProgressionsTest {

    private val useCase = BuildGuidedProgressionUseCase()

    @Test
    fun catalogIsWellFormed() {
        assertTrue(GuidedProgressions.all.isNotEmpty())
        for (progression in GuidedProgressions.all) {
            assertTrue(progression.chords.isNotEmpty())
            assertTrue(progression.degrees.isNotBlank())
            assertTrue(progression.description.isNotBlank())
            assertTrue(progression.totalBars > 0)
            for (chord in progression.chords) {
                assertTrue(chord.offset in 0..11)
                assertTrue(chord.bars in ImprovChord.BARS_OPTIONS)
            }
        }
    }

    @Test
    fun catalogIdsAreUnique() {
        val ids = GuidedProgressions.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun includesRequestedProgressions() {
        val ids = GuidedProgressions.all.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("pop-1564", "major-145", "blues-12", "andaluza")))
        assertNotNull(GuidedProgressions.byId("jazz-251"))
    }

    @Test
    fun transposesPopProgressionToAnyKey() {
        val progression = GuidedProgressions.byId("pop-1564")!!
        assertEquals(listOf(0, 7, 9, 5), progression.improvChords(0).map { it.rootPc })
        assertEquals(listOf(9, 4, 6, 2), progression.improvChords(9).map { it.rootPc })
        assertEquals(listOf("A", "E", "F#m", "D"), progression.labels(9, useFlats = false))
        assertEquals(listOf("La", "Mi", "Fa#m", "Re"), labelsInSolfege(progression, 9))
    }

    @Test
    fun andalusianCadenceUsesPhrygianDominant() {
        val progression = GuidedProgressions.byId("andaluza")!!
        assertEquals(listOf(9, 7, 5, 4), progression.improvChords(9).map { it.rootPc })
        assertEquals(listOf("Am", "G", "F", "E"), progression.labels(9, useFlats = false))
        assertEquals(Scales.PHRYGIAN_DOMINANT, progression.suggestedScale)
        assertEquals(9, progression.scaleRoot(9))
    }

    @Test
    fun twelveBarBluesHasTwelveBarsOfDominantSevenths() {
        val progression = GuidedProgressions.byId("blues-12")!!
        assertEquals(12, progression.totalBars)
        assertTrue(progression.chords.all { it.quality == Chords.DOMINANT_7 })
        assertEquals(Scales.BLUES_MINOR, progression.suggestedScale)
    }

    @Test
    fun useCaseBuildsAConcreteProgression() {
        val guided = GuidedProgressions.byId("major-145")!!
        val built = useCase.buildProgression(guided, keyRootPc = 2, useFlats = false)
        assertEquals("guided-major-145-2", built.id)
        assertEquals("I–IV–V · D", built.name)
        assertEquals(1, built.sections.size)
        assertEquals("Rock", built.sections.first().name)
        assertEquals(listOf(2, 7, 9), built.sections.first().chords.map { it.rootPc })
    }

    @Test
    fun guidedScaleRootStaysInRangeForEveryKey() {
        for (progression in GuidedProgressions.all) {
            for (key in 0..11) {
                assertTrue(progression.scaleRoot(key) in 0..11)
                assertTrue(progression.improvChords(key).all { it.rootPc in 0..11 })
            }
        }
    }

    private fun labelsInSolfege(
        progression: GuidedProgression,
        keyRootPc: Int
    ): List<String> = progression.chords.map { chord ->
        Pitch.solfegeName((keyRootPc + chord.offset) % 12, false) + chord.quality.symbol
    }
}
