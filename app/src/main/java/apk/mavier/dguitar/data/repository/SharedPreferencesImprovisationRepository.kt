package apk.mavier.dguitar.data.repository

import android.content.Context
import android.content.SharedPreferences
import apk.mavier.dguitar.data.preferences.PreferenceKeys
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesImprovisationRepository(context: Context) :
    ImprovisationRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PreferenceKeys.FILE, Context.MODE_PRIVATE)

    override fun loadSections(): List<ImprovSection> {
        val raw = prefs.getString(PreferenceKeys.IMPROV_SECTIONS, null)
            ?: return defaultSections()
        return try {
            val array = JSONArray(raw)
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
                            chords.add(ImprovChord(root, quality, principal, hits))
                        }
                    }
                }
                result.add(ImprovSection(name, chords))
            }
            if (result.isEmpty()) defaultSections() else result
        } catch (_: Exception) {
            defaultSections()
        }
    }

    override fun saveSections(sections: List<ImprovSection>) {
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
                chords.put(chordObject)
            }
            sectionObject.put("chords", chords)
            array.put(sectionObject)
        }
        prefs.edit().putString(PreferenceKeys.IMPROV_SECTIONS, array.toString()).apply()
    }

    override fun loadBpm(): Int =
        prefs.getInt(PreferenceKeys.IMPROV_BPM, DEFAULT_BPM).coerceIn(MIN_BPM, MAX_BPM)

    override fun saveBpm(bpm: Int) {
        prefs.edit()
            .putInt(PreferenceKeys.IMPROV_BPM, bpm.coerceIn(MIN_BPM, MAX_BPM))
            .apply()
    }

    override fun loadShowDegrees(): Boolean =
        prefs.getBoolean(PreferenceKeys.DEGREES, true)

    override fun saveShowDegrees(show: Boolean) {
        prefs.edit().putBoolean(PreferenceKeys.DEGREES, show).apply()
    }

    private fun defaultSections(): List<ImprovSection> =
        listOf(ImprovSection("Intro"))

    private companion object {
        const val DEFAULT_BPM = 80
        const val MIN_BPM = 40
        const val MAX_BPM = 220
    }
}
