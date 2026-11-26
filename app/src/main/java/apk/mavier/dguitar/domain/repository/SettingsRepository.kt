package apk.mavier.dguitar.domain.repository

import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.SoundProfile

interface SettingsRepository {
    fun load(): FretboardConfig
    fun save(config: FretboardConfig)
    fun loadSoundProfile(): SoundProfile
    fun saveSoundProfile(profile: SoundProfile)
    fun hasCompletedOnboarding(): Boolean
    fun setOnboardingCompleted(completed: Boolean)
}
