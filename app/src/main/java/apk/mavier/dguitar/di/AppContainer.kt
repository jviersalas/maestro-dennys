package apk.mavier.dguitar.di

import android.content.Context
import apk.mavier.dguitar.data.repository.AndroidSoundFontRepository
import apk.mavier.dguitar.data.repository.SharedPreferencesImprovisationRepository
import apk.mavier.dguitar.data.repository.SharedPreferencesSettingsRepository
import apk.mavier.dguitar.data.repository.SharedPreferencesSongbookRepository
import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SongbookRepository
import apk.mavier.dguitar.domain.repository.SoundFontRepository
import apk.mavier.dguitar.domain.usecase.BuildGuidedProgressionUseCase
import apk.mavier.dguitar.domain.usecase.BuildHarmonicCircleUseCase
import apk.mavier.dguitar.domain.usecase.BuildCircleOfFifthsUseCase
import apk.mavier.dguitar.domain.usecase.BuildModulationUseCase
import apk.mavier.dguitar.domain.usecase.GetImprovisationStepsUseCase

class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository =
        SharedPreferencesSettingsRepository(context)

    val improvisationRepository: ImprovisationRepository =
        SharedPreferencesImprovisationRepository(context)

    val songbookRepository: SongbookRepository =
        SharedPreferencesSongbookRepository(context)

    val harmonicCircleUseCase: BuildHarmonicCircleUseCase =
        BuildHarmonicCircleUseCase()

    val circleOfFifthsUseCase: BuildCircleOfFifthsUseCase =
        BuildCircleOfFifthsUseCase()

    val modulationUseCase: BuildModulationUseCase =
        BuildModulationUseCase()

    val improvisationStepsUseCase: GetImprovisationStepsUseCase =
        GetImprovisationStepsUseCase()

    val guidedProgressionUseCase: BuildGuidedProgressionUseCase =
        BuildGuidedProgressionUseCase()

    val soundFontRepository: SoundFontRepository =
        AndroidSoundFontRepository(context)
}
