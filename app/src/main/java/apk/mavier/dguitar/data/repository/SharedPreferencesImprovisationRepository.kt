package apk.mavier.dguitar.data.repository

import android.content.Context
import android.content.SharedPreferences
import apk.mavier.dguitar.data.preferences.PreferenceKeys
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.ImprovInstrument
import apk.mavier.dguitar.domain.model.ImprovProgression
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesImprovisationRepository(context: Context) :
    ImprovisationRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PreferenceKeys.FILE, Context.MODE_PRIVATE)

    override fun loadProgressions(instrument: ImprovInstrument): List<ImprovProgression> {
        val raw = prefs.getString(progressionsKey(instrument), null)
        if (raw != null) {
            val parsed = runCatching { parseProgressions(raw) }.getOrElse { emptyList() }
            if (parsed.isNotEmpty()) return parsed
        }

        val migrated = listOf(
            ImprovProgression(
                id = DEFAULT_PROGRESSION_ID,
                name = "Improvisación 1",
                sections = loadLegacySections(instrument)
            )
        )
        saveProgressions(instrument, migrated)
        saveSelectedProgressionId(instrument, DEFAULT_PROGRESSION_ID)
        return migrated
    }

    override fun saveProgressions(
        instrument: ImprovInstrument,
        progressions: List<ImprovProgression>
    ) {
        val array = JSONArray()
        for (progression in progressions) {
            val objectValue = JSONObject()
            objectValue.put("id", progression.id)
            objectValue.put("name", progression.name)
            objectValue.put("sections", sectionsToJson(progression.sections))
            array.put(objectValue)
        }
        prefs.edit()
            .putString(progressionsKey(instrument), array.toString())
            .apply()
    }

    override fun loadSelectedProgressionId(instrument: ImprovInstrument): String? =
        prefs.getString(selectedProgressionKey(instrument), null)

    override fun saveSelectedProgressionId(instrument: ImprovInstrument, id: String) {
        prefs.edit()
            .putString(selectedProgressionKey(instrument), id)
            .apply()
    }

    override fun loadBpm(instrument: ImprovInstrument): Int =
        prefs.getInt(bpmKey(instrument), DEFAULT_BPM).coerceIn(MIN_BPM, MAX_BPM)

    override fun saveBpm(instrument: ImprovInstrument, bpm: Int) {
        prefs.edit()
            .putInt(bpmKey(instrument), bpm.coerceIn(MIN_BPM, MAX_BPM))
            .apply()
    }

    override fun loadShowDegrees(): Boolean =
        prefs.getBoolean(PreferenceKeys.DEGREES, true)

    override fun saveShowDegrees(show: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.DEGREES, show).apply()
    }

    override fun loadScaleRoot(): Int =
        prefs.getInt(PreferenceKeys.IMPROV_SCALE_ROOT, NO_SCALE_ROOT)

    override fun saveScaleRoot(rootPc: Int) {
        prefs.edit()
            .putInt(PreferenceKeys.IMPROV_SCALE_ROOT, ((rootPc % 12) + 12) % 12)
            .apply()
    }

    override fun loadScaleIndex(): Int =
        prefs.getInt(
            PreferenceKeys.IMPROV_SCALE,
            Scales.all.indexOf(Scales.PENTATONIC_MINOR)
        )

    override fun saveScaleIndex(index: Int) {
        prefs.edit().putInt(PreferenceKeys.IMPROV_SCALE, index).apply()
    }

    override fun loadShowScale(): Boolean =
        prefs.getBoolean(PreferenceKeys.IMPROV_SHOW_SCALE, true)

    override fun saveShowScale(show: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.IMPROV_SHOW_SCALE, show).apply()
    }

    override fun loadShowChord(): Boolean =
        prefs.getBoolean(PreferenceKeys.IMPROV_SHOW_CHORD, true)

    override fun saveShowChord(show: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.IMPROV_SHOW_CHORD, show).apply()
    }

    override fun loadShowColors(): Boolean =
        prefs.getBoolean(PreferenceKeys.IMPROV_COLORS, true)

    override fun saveShowColors(show: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.IMPROV_COLORS, show).apply()
    }

    override fun loadMetronome(): Boolean =
        prefs.getBoolean(PreferenceKeys.IMPROV_METRONOME, true)

    override fun saveMetronome(enabled: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.IMPROV_METRONOME, enabled).apply()
    }

    private fun loadLegacySections(instrument: ImprovInstrument): List<ImprovSection> {
        val raw = prefs.getString(legacySectionsKey(instrument), null)
            ?: return defaultSections()
        return runCatching {
            val sections = parseSections(JSONArray(raw))
            if (sections.isEmpty()) defaultSections() else sections
        }.getOrElse { defaultSections() }
    }

    private fun parseProgressions(raw: String): List<ImprovProgression> {
        val array = JSONArray(raw)
        val result = mutableListOf<ImprovProgression>()
        for (index in 0 until array.length()) {
            val objectValue = array.optJSONObject(index) ?: continue
            val sections = parseSections(objectValue.optJSONArray("sections") ?: JSONArray())
            result.add(
                ImprovProgression(
                    id = objectValue.optString("id", "progression-$index")
                        .ifBlank { "progression-$index" },
                    name = objectValue.optString("name", "Improvisación ${index + 1}"),
                    sections = sections.ifEmpty { defaultSections() }
                )
            )
        }
        return result
    }

    private fun parseSections(array: JSONArray): List<ImprovSection> {
        val result = mutableListOf<ImprovSection>()
        for (sectionIndex in 0 until array.length()) {
            val sectionObject = array.optJSONObject(sectionIndex) ?: continue
            val name = sectionObject.optString("name", "Sección ${sectionIndex + 1}")
            val chords = mutableListOf<ImprovChord>()
            val chordsArray = sectionObject.optJSONArray("chords")
            if (chordsArray != null) {
                for (chordIndex in 0 until chordsArray.length()) {
                    val chordObject = chordsArray.optJSONObject(chordIndex) ?: continue
                    val root = chordObject.optInt("root", -1)
                    val quality = Chords.all.getOrNull(chordObject.optInt("quality", -1))
                    if (root in 0..11 && quality != null) {
                        val principal = chordObject.optBoolean("principal", false)
                        val hits = chordObject
                            .optInt("hits", ImprovChord.DEFAULT_HITS_PER_BAR)
                            .takeIf { it in ImprovChord.HITS_OPTIONS }
                            ?: ImprovChord.DEFAULT_HITS_PER_BAR
                        val bars = chordObject
                            .optInt("bars", ImprovChord.DEFAULT_BARS_PER_CHORD)
                            .takeIf { it in ImprovChord.BARS_OPTIONS }
                            ?: ImprovChord.DEFAULT_BARS_PER_CHORD
                        chords.add(ImprovChord(root, quality, principal, hits, bars))
                    }
                }
            }
            result.add(ImprovSection(name, chords))
        }
        return result
    }

    private fun sectionsToJson(sections: List<ImprovSection>): JSONArray {
        val array = JSONArray()
        for (section in sections) {
            val sectionObject = JSONObject()
            sectionObject.put("name", section.name)
            val chords = JSONArray()
            for (chord in section.chords) {
                val chordObject = JSONObject()
                chordObject.put("root", chord.rootPc)
                chordObject.put("quality", Chords.all.indexOf(chord.quality))
                chordObject.put("principal", chord.principalOnly)
                chordObject.put("hits", chord.normalizedHitsPerBar)
                chordObject.put("bars", chord.normalizedBarsPerChord)
                chords.put(chordObject)
            }
            sectionObject.put("chords", chords)
            array.put(sectionObject)
        }
        return array
    }

    private fun progressionsKey(instrument: ImprovInstrument): String =
        when (instrument) {
            ImprovInstrument.GUITAR -> PreferenceKeys.IMPROV_PROGRESSIONS_GUITAR
            ImprovInstrument.BASS -> PreferenceKeys.IMPROV_PROGRESSIONS_BASS
        }

    private fun selectedProgressionKey(instrument: ImprovInstrument): String =
        when (instrument) {
            ImprovInstrument.GUITAR -> PreferenceKeys.IMPROV_SELECTED_PROGRESSION_GUITAR
            ImprovInstrument.BASS -> PreferenceKeys.IMPROV_SELECTED_PROGRESSION_BASS
        }

    private fun legacySectionsKey(instrument: ImprovInstrument): String =
        when (instrument) {
            ImprovInstrument.GUITAR -> PreferenceKeys.IMPROV_SECTIONS_GUITAR
            ImprovInstrument.BASS -> PreferenceKeys.IMPROV_SECTIONS
        }

    private fun bpmKey(instrument: ImprovInstrument): String =
        when (instrument) {
            ImprovInstrument.GUITAR -> PreferenceKeys.IMPROV_BPM_GUITAR
            ImprovInstrument.BASS -> PreferenceKeys.IMPROV_BPM
        }

    private fun defaultSections(): List<ImprovSection> =
        listOf(ImprovSection("Intro"))

    private companion object {
        const val DEFAULT_PROGRESSION_ID = "default"
        const val DEFAULT_BPM = 80
        const val MIN_BPM = 40
        const val MAX_BPM = 220
        const val NO_SCALE_ROOT = -1
    }
}
