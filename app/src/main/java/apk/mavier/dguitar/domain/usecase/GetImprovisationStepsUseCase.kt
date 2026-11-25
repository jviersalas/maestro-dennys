package apk.mavier.dguitar.domain.usecase

import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.ImprovStep
import apk.mavier.dguitar.domain.model.Improvisation

class GetImprovisationStepsUseCase {
    operator fun invoke(sections: List<ImprovSection>): List<ImprovStep> =
        Improvisation.steps(sections)
}
