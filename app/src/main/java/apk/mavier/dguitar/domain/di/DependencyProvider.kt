package apk.mavier.dguitar.domain.di

import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.usecase.BuildHarmonicCircleUseCase
import apk.mavier.dguitar.domain.usecase.GetImprovisationStepsUseCase

interface DependencyProvider {
    val settingsRepository: SettingsRepository
    val improvisationRepository: ImprovisationRepository
    val harmonicCircleUseCase: BuildHarmonicCircleUseCase
    val improvisationStepsUseCase: GetImprovisationStepsUseCase
}
