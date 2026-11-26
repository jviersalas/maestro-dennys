package apk.mavier.dguitar.domain.di

import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SoundFontRepository
import apk.mavier.dguitar.domain.usecase.BuildGuidedProgressionUseCase
import apk.mavier.dguitar.domain.usecase.BuildHarmonicCircleUseCase
import apk.mavier.dguitar.domain.usecase.GetImprovisationStepsUseCase

interface DependencyProvider {
    val settingsRepository: SettingsRepository
    val improvisationRepository: ImprovisationRepository
    val harmonicCircleUseCase: BuildHarmonicCircleUseCase
    val improvisationStepsUseCase: GetImprovisationStepsUseCase
    val guidedProgressionUseCase: BuildGuidedProgressionUseCase
    val soundFontRepository: SoundFontRepository
}
