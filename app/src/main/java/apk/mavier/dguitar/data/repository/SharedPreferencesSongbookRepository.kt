package apk.mavier.dguitar.data.repository

import android.content.Context
import android.content.SharedPreferences
import apk.mavier.dguitar.data.format.SongChordJson
import apk.mavier.dguitar.data.preferences.PreferenceKeys
import apk.mavier.dguitar.domain.model.Song
import apk.mavier.dguitar.domain.model.SongBar
import apk.mavier.dguitar.domain.model.SongPart
import apk.mavier.dguitar.domain.model.SongPartType
import apk.mavier.dguitar.domain.model.Songbook
import apk.mavier.dguitar.domain.repository.SongbookRepository
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesSongbookRepository(context: Context) : SongbookRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PreferenceKeys.FILE, Context.MODE_PRIVATE)

    override fun loadSongs(): List<Song> {
        val raw = prefs.getString(PreferenceKeys.SONGBOOK_SONGS, null)
        var stored = if (raw == null) {
            emptyList()
        } else {
            runCatching { parseSongs(raw) }.getOrElse { emptyList() }
        }

        stored = migrateExamplesIfNeeded(stored)

        val pendingExamples = mutableListOf<Song>()
        if (!prefs.getBoolean(PreferenceKeys.SONGBOOK_HEY_JUDE_SEEDED, false)) {
            pendingExamples.add(Songbook.heyJudeSong())
            prefs.edit()
                .putBoolean(PreferenceKeys.SONGBOOK_HEY_JUDE_SEEDED, true)
                .apply()
        }
        if (!prefs.getBoolean(PreferenceKeys.SONGBOOK_YESTERDAY_SEEDED, false)) {
            pendingExamples.add(Songbook.yesterdaySong())
            prefs.edit()
                .putBoolean(PreferenceKeys.SONGBOOK_YESTERDAY_SEEDED, true)
                .apply()
        }
        if (pendingExamples.isNotEmpty()) {
            val missing = pendingExamples.filter { example ->
                stored.none { it.id == example.id }
            }
            if (missing.isNotEmpty()) {
                val merged = missing + stored
                saveSongs(merged)
                return merged
            }
        }
        return stored
    }

    private fun migrateExamplesIfNeeded(songs: List<Song>): List<Song> {
        val version = prefs.getInt(PreferenceKeys.SONGBOOK_EXAMPLES_VERSION, 1)
        if (version >= EXAMPLES_VERSION) return songs
        val replacements = listOf(
            Songbook.heyJudeSong(),
            Songbook.yesterdaySong()
        ).associateBy { it.id }
        var changed = false
        val migrated = songs.mapNotNull { song ->
            if (song.id == Songbook.EXAMPLE_ID &&
                song.title == "Atado a un Sentimiento" &&
                song.artist == "Miguel Mateos"
            ) {
                changed = true
                null
            } else {
                val replacement = replacements[song.id]
                if (replacement != null) {
                    changed = true
                    replacement
                } else {
                    song
                }
            }
        }
        if (changed) saveSongs(migrated)
        prefs.edit()
            .putInt(PreferenceKeys.SONGBOOK_EXAMPLES_VERSION, EXAMPLES_VERSION)
            .apply()
        return migrated
    }

    override fun saveSongs(songs: List<Song>) {
        prefs.edit()
            .putString(PreferenceKeys.SONGBOOK_SONGS, songsToJson(songs).toString())
            .apply()
    }

    private fun parseSongs(raw: String): List<Song> {
        val array = JSONArray(raw)
        val result = mutableListOf<Song>()
        for (index in 0 until array.length()) {
            val objectValue = array.optJSONObject(index) ?: continue
            val id = objectValue.optString("id", "song-$index")
                .ifBlank { "song-$index" }
            val title = objectValue.optString("title", "Canción ${index + 1}")
                .ifBlank { "Canción ${index + 1}" }
            val parts = parseParts(objectValue.optJSONArray("parts") ?: JSONArray())
            result.add(
                Song(
                    id = id,
                    title = title,
                    artist = objectValue.optString("artist", ""),
                    bpm = Songbook.normalizeBpm(
                        objectValue.optInt("bpm", Songbook.DEFAULT_BPM)
                    ),
                    parts = parts
                )
            )
        }
        return result
    }

    private fun parseParts(array: JSONArray): List<SongPart> {
        val result = mutableListOf<SongPart>()
        for (index in 0 until array.length()) {
            val objectValue = array.optJSONObject(index) ?: continue
            val type = runCatching {
                SongPartType.valueOf(objectValue.optString("type", SongPartType.VERSO.name))
            }.getOrElse { SongPartType.VERSO }
            val name = objectValue.optString("name", type.label)
                .ifBlank { type.label }
            val bars = parseBarsOrLegacyChords(objectValue)
            result.add(
                SongPart(
                    name = name,
                    type = type,
                    bars = bars,
                    lyrics = objectValue.optString("lyrics", ""),
                    repeatCount = Songbook.normalizeRepeatCount(
                        objectValue.optInt("repeat", 1)
                    )
                )
            )
        }
        return result
    }

    private fun parseBarsOrLegacyChords(partObject: JSONObject): List<SongBar> {
        val barsArray = partObject.optJSONArray("bars")
        if (barsArray != null) {
            return parseBars(barsArray)
        }
        val legacyChords = SongChordJson.decodeList(
            partObject.optJSONArray("chords")
        )
        return legacyChords
            .chunked(SongBar.MAX_CHORDS)
            .map { SongBar(it) }
    }

    private fun parseBars(array: JSONArray): List<SongBar> {
        val result = mutableListOf<SongBar>()
        for (index in 0 until array.length()) {
            val chordsArray = array.optJSONArray(index) ?: continue
            result.add(
                SongBar(
                    SongChordJson.decodeList(chordsArray)
                        .take(SongBar.MAX_CHORDS)
                )
            )
        }
        return result
    }

    private fun songsToJson(songs: List<Song>): JSONArray {
        val array = JSONArray()
        for (song in songs) {
            val objectValue = JSONObject()
            objectValue.put("id", song.id)
            objectValue.put("title", song.title)
            objectValue.put("artist", song.artist)
            objectValue.put("bpm", Songbook.normalizeBpm(song.bpm))
            objectValue.put("parts", partsToJson(song.parts))
            array.put(objectValue)
        }
        return array
    }

    private fun partsToJson(parts: List<SongPart>): JSONArray {
        val array = JSONArray()
        for (part in parts) {
            val objectValue = JSONObject()
            objectValue.put("name", part.name)
            objectValue.put("type", part.type.name)
            objectValue.put("lyrics", part.lyrics)
            objectValue.put("repeat", part.normalizedRepeatCount)
            objectValue.put("bars", barsToJson(part.normalizedBars))
            array.put(objectValue)
        }
        return array
    }

    private fun barsToJson(bars: List<SongBar>): JSONArray {
        val array = JSONArray()
        for (bar in bars) {
            array.put(SongChordJson.encodeList(bar.normalizedChords))
        }
        return array
    }

    private companion object {
        const val EXAMPLES_VERSION = 5
    }
}
