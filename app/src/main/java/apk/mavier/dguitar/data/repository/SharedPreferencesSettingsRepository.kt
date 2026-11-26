package apk.mavier.dguitar.data.repository

import android.content.Context
import android.content.SharedPreferences
import apk.mavier.dguitar.data.preferences.HarmonicCircleCodec
import apk.mavier.dguitar.data.preferences.PreferenceKeys
import apk.mavier.dguitar.data.preferences.SoundProfileCodec
import apk.mavier.dguitar.domain.model.CagedPatterns
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.model.SoundProfile
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.domain.repository.SettingsRepository

class SharedPreferencesSettingsRepository(context: Context) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PreferenceKeys.FILE, Context.MODE_PRIVATE)

    override fun load(): FretboardConfig {
        val mode = ViewMode.values().getOrElse(
            prefs.getInt(PreferenceKeys.MODE, ViewMode.SCALE.ordinal)
        ) { ViewMode.SCALE }
        val scale = Scales.all.getOrElse(
            prefs.getInt(PreferenceKeys.SCALE, Scales.all.indexOf(Scales.default))
        ) { Scales.default }
        val chord = Chords.all.getOrElse(
            prefs.getInt(PreferenceKeys.CHORD, 0)
        ) { Chords.MAJOR }
        val tuning = Tunings.all.getOrElse(
            prefs.getInt(PreferenceKeys.TUNING, 0)
        ) { Tunings.STANDARD }

        val safeMode = when {
            mode == ViewMode.IMPROV -> ViewMode.SCALE
            tuning.strings.size < 6 && mode == ViewMode.CHORD -> ViewMode.SCALE
            else -> mode
        }

        val rootPc = ((prefs.getInt(PreferenceKeys.ROOT, 0) % 12) + 12) % 12
        val patternValue = prefs.getInt(PreferenceKeys.PATTERN, 0)
        val chordPatternValue = prefs.getInt(PreferenceKeys.CHORD_PATTERN, 0)
        val selectedPattern = if (patternValue in 1..5) patternValue else null
        val chordPattern = if (chordPatternValue in 1..5) chordPatternValue else null

        return FretboardConfig(
            tuning = tuning,
            mode = safeMode,
            rootPc = rootPc,
            scale = scale,
            chord = chord,
            useFlats = prefs.getBoolean(PreferenceKeys.FLATS, true),
            colorsEnabled = prefs.getBoolean(PreferenceKeys.COLORS, true),
            showDegrees = prefs.getBoolean(PreferenceKeys.DEGREES, true),
            circleDegrees = HarmonicCircleCodec.parseDegrees(
                prefs.getString(PreferenceKeys.CIRCLE_DEGREES, "")
            ),
            circleSevenths = prefs.getBoolean(PreferenceKeys.CIRCLE_SEVENTHS, false),
            circleOverrides = HarmonicCircleCodec.parseOverrides(
                prefs.getString(PreferenceKeys.CIRCLE_OVERRIDES, "")
            ),
            selectedPattern = selectedPattern,
            selectedPatternStartFret = resolveStart(
                selectedPattern,
                prefs.getInt(PreferenceKeys.PATTERN_START_FRET, NO_FRET)
            ),
            chordPattern = chordPattern,
            chordPatternStartFret = resolveStart(
                chordPattern,
                prefs.getInt(PreferenceKeys.CHORD_PATTERN_START_FRET, NO_FRET)
            )
        )
    }

    override fun save(config: FretboardConfig) {
        prefs.edit()
            .putInt(PreferenceKeys.MODE, config.mode.ordinal)
            .putInt(PreferenceKeys.ROOT, config.rootPc)
            .putInt(PreferenceKeys.SCALE, Scales.all.indexOf(config.scale))
            .putInt(PreferenceKeys.CHORD, Chords.all.indexOf(config.chord))
            .putInt(PreferenceKeys.TUNING, Tunings.all.indexOf(config.tuning))
            .putBoolean(PreferenceKeys.FLATS, config.useFlats)
            .putBoolean(PreferenceKeys.COLORS, config.colorsEnabled)
            .putBoolean(PreferenceKeys.DEGREES, config.showDegrees)
            .putString(
                PreferenceKeys.CIRCLE_DEGREES,
                HarmonicCircleCodec.encodeDegrees(config.circleDegrees)
            )
            .putBoolean(PreferenceKeys.CIRCLE_SEVENTHS, config.circleSevenths)
            .putString(
                PreferenceKeys.CIRCLE_OVERRIDES,
                HarmonicCircleCodec.encodeOverrides(config.circleOverrides)
            )
            .remove(PreferenceKeys.LEGACY_OVERLAY_ROOTS)
            .remove(PreferenceKeys.LEGACY_OVERLAY_QUALITY)
            .putInt(PreferenceKeys.PATTERN, config.selectedPattern ?: 0)
            .putInt(
                PreferenceKeys.PATTERN_START_FRET,
                config.selectedPatternStartFret ?: NO_FRET
            )
            .putInt(PreferenceKeys.CHORD_PATTERN, config.chordPattern ?: 0)
            .putInt(
                PreferenceKeys.CHORD_PATTERN_START_FRET,
                config.chordPatternStartFret ?: NO_FRET
            )
            .apply()
    }

    override fun hasCompletedOnboarding(): Boolean =
        prefs.getBoolean(PreferenceKeys.ONBOARDING_COMPLETED, false)

    override fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.ONBOARDING_COMPLETED, completed).apply()
    }

    override fun loadSoundProfile(): SoundProfile =
        SoundProfileCodec.parse(prefs.getString(PreferenceKeys.SOUND_PROFILE, null))

    override fun saveSoundProfile(profile: SoundProfile) {
        prefs.edit()
            .putString(PreferenceKeys.SOUND_PROFILE, SoundProfileCodec.encode(profile))
            .apply()
    }

    private fun resolveStart(number: Int?, storedStart: Int): Int? {
        if (number == null) return null
        val occurrences = CagedPatterns.occurrencesFor(number)
        return occurrences.firstOrNull { it.startFret == storedStart }?.startFret
            ?: occurrences.minByOrNull { it.startFret }?.startFret
    }

    private companion object {
        const val NO_FRET = -1
    }
}
