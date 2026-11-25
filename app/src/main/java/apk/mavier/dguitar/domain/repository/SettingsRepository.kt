package apk.mavier.dguitar.domain.repository

import apk.mavier.dguitar.domain.model.FretboardConfig

interface SettingsRepository {
    fun load(): FretboardConfig
    fun save(config: FretboardConfig)
    fun hasCompletedOnboarding(): Boolean
    fun setOnboardingCompleted(completed: Boolean)
}
