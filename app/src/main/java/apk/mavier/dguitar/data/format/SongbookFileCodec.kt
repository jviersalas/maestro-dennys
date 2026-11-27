package apk.mavier.dguitar.data.format

import apk.mavier.dguitar.domain.model.Song
import apk.mavier.dguitar.domain.model.SongBar
import apk.mavier.dguitar.domain.model.SongPart
import apk.mavier.dguitar.domain.model.SongPartType
import apk.mavier.dguitar.domain.model.Songbook
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Portable DGuitar songbook file. A .MDennys file is JSON with this shape:
 *
 * {
 *   "format": "MDennys",
 *   "version": 1,
 *   "songs": [{
 *     "title": "...", "artist": "...", "bpm": 96,
 *     "parts": [{
 *       "name": "Verso 1", "type": "VERSO", "lyrics": "", "repeat": 1,
 *       "bars": [[{"root": 5, "quality": "", "bass": -1}]]
 *     }]
 *   }]
 * }
 */
object SongbookFileCodec {

    const val FORMAT = "MDennys"
    const val VERSION = 1
    const val EXTENSION = "MDennys"
    const val MIME_TYPE = "application/octet-stream"

    fun encode(songs: List<Song>): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        val songArray = JSONArray()
        songs.forEach { songArray.put(songToJson(it)) }
        root.put("songs", songArray)
        return root.toString(2)
    }

    fun decode(raw: String): List<Song> {
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) {
            "El archivo no es un cancionero .MDennys"
        }
        val version = root.optInt("version", 0)
        require(version in 1..VERSION) {
            "Versión de archivo no compatible"
        }
        val songArray = root.optJSONArray("songs")
            ?: throw IllegalArgumentException("El archivo no contiene canciones")
        val songs = mutableListOf<Song>()
        for (index in 0 until songArray.length()) {
            val songObject = songArray.optJSONObject(index) ?: continue
            songs.add(songFromJson(songObject, index))
        }
        require(songs.isNotEmpty()) {
            "El archivo no contiene canciones válidas"
        }
        return songs
    }

    private fun songToJson(song: Song): JSONObject {
        val objectValue = JSONObject()
        objectValue.put("title", song.title)
        objectValue.put("artist", song.artist)
        objectValue.put("bpm", Songbook.normalizeBpm(song.bpm))
        val partArray = JSONArray()
        song.parts.forEach { partArray.put(partToJson(it)) }
        objectValue.put("parts", partArray)
        return objectValue
    }

    private fun partToJson(part: SongPart): JSONObject {
        val objectValue = JSONObject()
        objectValue.put("name", part.name)
        objectValue.put("type", part.type.name)
        objectValue.put("lyrics", part.lyrics)
        objectValue.put("repeat", part.normalizedRepeatCount)
        val barArray = JSONArray()
        part.normalizedBars.forEach { barArray.put(SongChordJson.encodeList(it.normalizedChords)) }
        objectValue.put("bars", barArray)
        return objectValue
    }

    private fun songFromJson(objectValue: JSONObject, index: Int): Song {
        val title = objectValue.optString("title", "")
            .trim()
            .ifBlank { "Canción importada ${index + 1}" }
        val artist = objectValue.optString("artist", "").trim()
        val parts = parseParts(objectValue.optJSONArray("parts"))
            .ifEmpty {
                listOf(
                    SongPart(
                        name = "Verso 1",
                        type = SongPartType.VERSO,
                        bars = listOf(SongBar())
                    )
                )
            }
        return Song(
            id = UUID.randomUUID().toString(),
            title = title,
            artist = artist,
            bpm = Songbook.normalizeBpm(
                objectValue.optInt("bpm", Songbook.DEFAULT_BPM)
            ),
            parts = parts
        )
    }

    private fun parseParts(array: JSONArray?): List<SongPart> {
        if (array == null) return emptyList()
        val result = mutableListOf<SongPart>()
        for (index in 0 until array.length()) {
            val objectValue = array.optJSONObject(index) ?: continue
            val type = runCatching {
                SongPartType.valueOf(
                    objectValue.optString("type", SongPartType.VERSO.name)
                )
            }.getOrElse { SongPartType.VERSO }
            val name = objectValue.optString("name", type.label)
                .trim()
                .ifBlank { type.label }
            val bars = parseBars(objectValue.optJSONArray("bars"))
                .ifEmpty { listOf(SongBar()) }
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

    private fun parseBars(array: JSONArray?): List<SongBar> {
        if (array == null) return emptyList()
        val result = mutableListOf<SongBar>()
        for (index in 0 until array.length()) {
            val chordsArray = array.optJSONArray(index) ?: continue
            result.add(SongBar(SongChordJson.decodeList(chordsArray).take(SongBar.MAX_CHORDS)))
        }
        return result
    }
}
