package apk.mavier.dguitar.domain.model

data class ImprovTimeline(
    val steps: List<ImprovStep>,
    val bars: List<ImprovBar>,
    private val sectionStartSteps: IntArray,
    private val stepStartBars: IntArray
) {

    fun firstStepOfSection(sectionIndex: Int): Int =
        sectionStartSteps.getOrElse(sectionIndex) { steps.size }

    fun firstBarOfStep(stepIndex: Int): Int =
        if (stepIndex in stepStartBars.indices) stepStartBars[stepIndex] else -1

    companion object {
        fun of(sections: List<ImprovSection>): ImprovTimeline {
            val steps = Improvisation.steps(sections)
            val bars = Improvisation.bars(sections)

            val sectionStarts = IntArray(sections.size + 1)
            var stepOffset = 0
            sections.forEachIndexed { index, section ->
                sectionStarts[index] = stepOffset
                stepOffset += section.chords.size
            }
            sectionStarts[sections.size] = stepOffset

            val stepStarts = IntArray(steps.size)
            var barOffset = 0
            steps.forEachIndexed { index, step ->
                stepStarts[index] = barOffset
                barOffset += step.chord.normalizedBarsPerChord
            }

            return ImprovTimeline(steps, bars, sectionStarts, stepStarts)
        }
    }
}
