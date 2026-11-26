package apk.mavier.dguitar

import android.app.Application
import apk.mavier.dguitar.di.AppContainer
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SoundFontRepository
import apk.mavier.dguitar.domain.usecase.BuildGuidedProgressionUseCase
import apk.mavier.dguitar.domain.usecase.BuildHarmonicCircleUseCase
import apk.mavier.dguitar.domain.usecase.GetImprovisationStepsUseCase

class DGuitarApp : Application(), DependencyProvider {

    private lateinit var container: AppContainer

    override val settingsRepository: SettingsRepository
        get() = container.settingsRepository

    override val improvisationRepository: ImprovisationRepository
        get() = container.improvisationRepository

    override val harmonicCircleUseCase: BuildHarmonicCircleUseCase
        get() = container.harmonicCircleUseCase

    override val improvisationStepsUseCase: GetImprovisationStepsUseCase
        get() = container.improvisationStepsUseCase

    override val guidedProgressionUseCase: BuildGuidedProgressionUseCase
        get() = container.guidedProgressionUseCase

    override val soundFontRepository: SoundFontRepository
        get() = container.soundFontRepository

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
