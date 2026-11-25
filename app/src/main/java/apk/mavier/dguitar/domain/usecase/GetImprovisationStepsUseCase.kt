package apk.mavier.dguitar.domain.usecase

import apk.mavier.dguitar.domain.model.ImprovBar
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.ImprovStep
import apk.mavier.dguitar.domain.model.ImprovTimeline
import apk.mavier.dguitar.domain.model.Improvisation

class GetImprovisationStepsUseCase {
    fun timeline(sections: List<ImprovSection>): ImprovTimeline =
        ImprovTimeline.of(sections)

    operator fun invoke(sections: List<ImprovSection>): List<ImprovStep> =
        Improvisation.steps(sections)

    fun bars(sections: List<ImprovSection>): List<ImprovBar> =
        Improvisation.bars(sections)

    fun totalBars(sections: List<ImprovSection>): Int =
        Improvisation.totalBars(sections)
}
