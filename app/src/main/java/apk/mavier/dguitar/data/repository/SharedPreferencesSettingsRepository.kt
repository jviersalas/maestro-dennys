package apk.mavier.dguitar.data.repository

import android.content.Context
import android.content.SharedPreferences
import apk.mavier.dguitar.data.preferences.PreferenceKeys
import apk.mavier.dguitar.domain.model.ChordQuality
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.Scales
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

        return FretboardConfig(
            tuning = tuning,
            mode = safeMode,
            rootPc = rootPc,
            scale = scale,
            chord = chord,
            useFlats = prefs.getBoolean(PreferenceKeys.FLATS, true),
            colorsEnabled = prefs.getBoolean(PreferenceKeys.COLORS, true),
            showDegrees = prefs.getBoolean(PreferenceKeys.DEGREES, true),
            circleDegrees = parseCircleDegrees(
                prefs.getString(PreferenceKeys.CIRCLE_DEGREES, "")
            ),
            circleSevenths = prefs.getBoolean(PreferenceKeys.CIRCLE_SEVENTHS, false),
            circleOverrides = parseCircleOverrides(
                prefs.getString(PreferenceKeys.CIRCLE_OVERRIDES, "")
            ),
            selectedPattern = if (patternValue in 1..5) patternValue else null,
            chordPattern = if (chordPatternValue in 1..5) chordPatternValue else null
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
                config.circleDegrees.sorted().joinToString(",")
            )
            .putBoolean(PreferenceKeys.CIRCLE_SEVENTHS, config.circleSevenths)
            .putString(
                PreferenceKeys.CIRCLE_OVERRIDES,
                encodeCircleOverrides(config.circleOverrides)
            )
            .remove(PreferenceKeys.LEGACY_OVERLAY_ROOTS)
            .remove(PreferenceKeys.LEGACY_OVERLAY_QUALITY)
            .putInt(PreferenceKeys.PATTERN, config.selectedPattern ?: 0)
            .putInt(PreferenceKeys.CHORD_PATTERN, config.chordPattern ?: 0)
            .apply()
    }

    private fun parseCircleDegrees(value: String?): Set<Int> =
        value.orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

    private fun parseCircleOverrides(value: String?): Map<Int, ChordQuality> {
        if (value.isNullOrBlank()) return emptyMap()
        return value.split(";").mapNotNull { part ->
            val pieces = part.split(":")
            if (pieces.size != 2) return@mapNotNull null
            val degree = pieces[0].trim().toIntOrNull() ?: return@mapNotNull null
            val index = pieces[1].trim().toIntOrNull() ?: return@mapNotNull null
            val quality = Chords.all.getOrNull(index) ?: return@mapNotNull null
            if (degree !in 1..7) null else degree to quality
        }.toMap()
    }

    private fun encodeCircleOverrides(overrides: Map<Int, ChordQuality>): String =
        overrides.entries
            .sortedBy { it.key }
            .joinToString(";") { (degree, quality) ->
                "$degree:${Chords.all.indexOf(quality)}"
            }
}
