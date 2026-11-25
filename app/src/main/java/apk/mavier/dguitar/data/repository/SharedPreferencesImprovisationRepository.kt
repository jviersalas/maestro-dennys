package apk.mavier.dguitar.data.repository

import android.content.Context
import android.content.SharedPreferences
import apk.mavier.dguitar.data.preferences.PreferenceKeys
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.ImprovInstrument
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesImprovisationRepository(context: Context) :
    ImprovisationRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PreferenceKeys.FILE, Context.MODE_PRIVATE)

    override fun loadSections(instrument: ImprovInstrument): List<ImprovSection> {
        val raw = prefs.getString(sectionsKey(instrument), null)
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
            if (result.isEmpty()) defaultSections() else result
        } catch (_: Exception) {
            defaultSections()
        }
    }

    override fun saveSections(instrument: ImprovInstrument, sections: List<ImprovSection>) {
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
        prefs.edit()
            .putString(sectionsKey(instrument), array.toString())
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

    private fun sectionsKey(instrument: ImprovInstrument): String =
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
        const val DEFAULT_BPM = 80
        const val MIN_BPM = 40
        const val MAX_BPM = 220
        const val NO_SCALE_ROOT = -1
    }
}
