package apk.mavier.dguitar.presentation.theme

object NoteColorProvider {

    const val ROOT = 0xFF7C5CFF.toInt()
    const val NEUTRAL_FILL = 0xFFF3EFE6.toInt()
    const val NEUTRAL_TEXT = 0xFF161616.toInt()
    const val ATTENUATED_FILL = 0xFF9E9AA4.toInt()
    const val NEUTRAL_TEXT_LIGHT = 0xFFBDB7C8.toInt()

    private val SCALE_PALETTE = intArrayOf(
        0xFF2F9E8F.toInt(),
        0xFF3F8FD6.toInt(),
        0xFFCF9A2B.toInt(),
        0xFFCE5A5A.toInt(),
        0xFF5FA463.toInt(),
        0xFF9B5FC0.toInt(),
        0xFFD27A45.toInt()
    )

    fun scaleDegreeColor(degree: Int): Int {
        if (degree <= 1) return ROOT
        return SCALE_PALETTE[(degree - 2) % SCALE_PALETTE.size]
    }

    fun chordRoleColor(relativeSemitones: Int): Int = when (((relativeSemitones % 12) + 12) % 12) {
        0 -> ROOT
        3, 4 -> 0xFF3F8FD6.toInt()
        6, 7, 8 -> 0xFF2F9E8F.toInt()
        10, 11 -> 0xFFCF9A2B.toInt()
        else -> 0xFFCE5A5A.toInt()
    }

    fun textColorFor(fill: Int): Int {
        val red = (fill shr 16) and 0xFF
        val green = (fill shr 8) and 0xFF
        val blue = fill and 0xFF
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
        return if (luminance > 0.62) NEUTRAL_TEXT else 0xFFFFFFFF.toInt()
    }
}
