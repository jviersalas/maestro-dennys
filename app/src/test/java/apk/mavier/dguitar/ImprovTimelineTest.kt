package apk.mavier.dguitar

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.ImprovTimeline
import org.junit.Assert.assertEquals
import org.junit.Test

class ImprovTimelineTest {

    private val sections = listOf(
        ImprovSection(
            "A",
            listOf(
                ImprovChord(0, Chords.MAJOR, barsPerChord = 2),
                ImprovChord(7, Chords.MAJOR)
            )
        ),
        ImprovSection(
            "B",
            listOf(
                ImprovChord(9, Chords.MINOR, barsPerChord = 4)
            )
        )
    )

    @Test
    fun stepsAndBarsMatchTheImprovisationModel() {
        val timeline = ImprovTimeline.of(sections)
        assertEquals(3, timeline.steps.size)
        assertEquals(7, timeline.bars.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), timeline.bars.map { it.globalIndex })
    }

    @Test
    fun sectionsReportTheirFirstStep() {
        val timeline = ImprovTimeline.of(sections)
        assertEquals(0, timeline.firstStepOfSection(0))
        assertEquals(2, timeline.firstStepOfSection(1))
        assertEquals(3, timeline.firstStepOfSection(2))
    }

    @Test
    fun stepsReportTheirFirstBar() {
        val timeline = ImprovTimeline.of(sections)
        assertEquals(0, timeline.firstBarOfStep(0))
        assertEquals(2, timeline.firstBarOfStep(1))
        assertEquals(3, timeline.firstBarOfStep(2))
        assertEquals(-1, timeline.firstBarOfStep(-1))
        assertEquals(-1, timeline.firstBarOfStep(99))
    }

    @Test
    fun emptySectionsKeepTheirStartingOffset() {
        val emptyFirst = listOf(
            ImprovSection("Intro"),
            ImprovSection("A", listOf(ImprovChord(0, Chords.MAJOR)))
        )
        val timeline = ImprovTimeline.of(emptyFirst)
        assertEquals(1, timeline.steps.size)
        assertEquals(0, timeline.firstStepOfSection(0))
        assertEquals(0, timeline.firstStepOfSection(1))
    }
}
