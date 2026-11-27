package apk.mavier.dguitar.presentation.common

import apk.mavier.dguitar.core.audio.AudioOutput
import apk.mavier.dguitar.domain.model.SoundKind
import apk.mavier.dguitar.domain.model.SoundProfile
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SoundFontRepository

/**
 * Owns the audio engine and the selected sound profile: resolving a profile
 * for the current instrument, loading its SF2 asynchronously, coping with
 * stale loads, and persisting changes. Activities only start/stop, play notes
 * and select a profile.
 */
class AudioSession(
    private val output: AudioOutput,
    private val settings: SettingsRepository,
    private val soundFonts: SoundFontRepository,
    private val guitar: () -> Boolean,
    private val isActive: () -> Boolean,
    private val onLoadFailed: () -> Unit
) {

    var profile: SoundProfile = settings.loadSoundProfile()
        private set

    private var loadedProfile: SoundProfile? = null
    private var loadingProfile: SoundProfile? = null
    private var failedProfile: SoundProfile? = null

    var bassMode: Boolean
        get() = output.bassMode
        set(value) {
            output.bassMode = value
        }

    fun start() {
        bassMode = !guitar()
        output.start()
        syncProfileFromSettings()
        refresh()
    }

    fun stop() {
        output.stop()
    }

    fun pluck(stringIndex: Int, midi: Int) {
        output.pluck(stringIndex, midi)
    }

    fun silence() {
        output.silence()
    }

    fun select(profile: SoundProfile) {
        this.profile = profile
        settings.saveSoundProfile(profile)
        resetFontState()
        refresh()
    }

    fun refresh() {
        val family = if (guitar()) SoundFontFamily.GUITAR else SoundFontFamily.BASS
        val resolved = SoundFontCatalog.resolve(profile, family)
        if (resolved != profile) {
            profile = resolved
            settings.saveSoundProfile(resolved)
        }
        if (profile.kind == SoundKind.SYNTHETIC) {
            if (loadedProfile != null || loadingProfile != null) {
                output.loadSoundFont(null)
                loadedProfile = null
                loadingProfile = null
            }
            return
        }
        if (loadedProfile == profile ||
            loadingProfile == profile ||
            failedProfile == profile
        ) {
            return
        }
        val target = profile
        output.loadSoundFont(null)
        loadingProfile = target
        soundFonts.load(target) { font ->
            if (loadingProfile == target) loadingProfile = null
            if (!isActive()) return@load
            if (profile != target) return@load
            if (font != null) {
                output.loadSoundFont(font)
                loadedProfile = target
                failedProfile = null
            } else {
                loadedProfile = null
                failedProfile = target
                onLoadFailed()
            }
        }
    }

    private fun syncProfileFromSettings() {
        val stored = settings.loadSoundProfile()
        if (stored != profile) {
            profile = stored
            resetFontState()
        }
    }

    private fun resetFontState() {
        output.loadSoundFont(null)
        loadedProfile = null
        loadingProfile = null
        failedProfile = null
    }
}
