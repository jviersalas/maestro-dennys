package apk.mavier.dguitar.data.format

import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.SongChord
import org.json.JSONArray
import org.json.JSONObject

/**
 * The persisted and portable song files share the same chord shape:
 * `{ "root": 7, "quality": "", "bass": -1 }`. Keeping this mapping in one
 * module prevents the internal songbook and the .MDennys exchange format from
 * drifting apart.
 */
object SongChordJson {

    fun encode(chord: SongChord): JSONObject = JSONObject().apply {
        put("root", ((chord.rootPc % 12) + 12) % 12)
        put("quality", chord.quality.symbol)
        put("bass", chord.bassPc ?: -1)
    }

    fun encodeList(chords: List<SongChord>): JSONArray = JSONArray().apply {
        chords.forEach { put(encode(it)) }
    }

    fun decode(objectValue: JSONObject): SongChord? {
        val root = objectValue.optInt("root", -1)
        if (root !in 0..11) return null
        val symbol = objectValue.optString("quality", "")
        val quality = Chords.all.firstOrNull { it.symbol == symbol } ?: Chords.MAJOR
        val bass = objectValue.optInt("bass", -1).takeIf { it in 0..11 }
        return SongChord(root, quality, bass)
    }

    fun decodeList(array: JSONArray?): List<SongChord> {
        if (array == null) return emptyList()
        val result = mutableListOf<SongChord>()
        for (index in 0 until array.length()) {
            val objectValue = array.optJSONObject(index) ?: continue
            decode(objectValue)?.let(result::add)
        }
        return result
    }
}
