package apk.mavier.dguitar.di

import android.content.Context
import apk.mavier.dguitar.data.repository.SharedPreferencesImprovisationRepository
import apk.mavier.dguitar.data.repository.SharedPreferencesSettingsRepository
import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.usecase.BuildHarmonicCircleUseCase
import apk.mavier.dguitar.domain.usecase.GetImprovisationStepsUseCase

class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository =
        SharedPreferencesSettingsRepository(context)

    val improvisationRepository: ImprovisationRepository =
        SharedPreferencesImprovisationRepository(context)

    val harmonicCircleUseCase: BuildHarmonicCircleUseCase =
        BuildHarmonicCircleUseCase()

    val improvisationStepsUseCase: GetImprovisationStepsUseCase =
        GetImprovisationStepsUseCase()
}
