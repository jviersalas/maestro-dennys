package apk.mavier.dguitar.data.preferences

import apk.mavier.dguitar.domain.model.ChordQuality
import apk.mavier.dguitar.domain.model.Chords

object HarmonicCircleCodec {

    fun parseDegrees(value: String?): Set<Int> =
        value.orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

    fun encodeDegrees(degrees: Set<Int>): String =
        degrees.sorted().joinToString(",")

    fun parseOverrides(value: String?): Map<Int, ChordQuality> {
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

    fun encodeOverrides(overrides: Map<Int, ChordQuality>): String =
        overrides.entries
            .sortedBy { it.key }
            .joinToString(";") { (degree, quality) ->
                "$degree:${Chords.all.indexOf(quality)}"
            }
}
