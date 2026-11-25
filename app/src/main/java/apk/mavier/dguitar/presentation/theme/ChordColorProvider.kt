package apk.mavier.dguitar.presentation.theme

object ChordColorProvider {

    private val BASE_COLORS = intArrayOf(
        0xFFD4A017.toInt(), // C
        0xFFA66A1E.toInt(), // Db
        0xFF43A047.toInt(), // D  (verde)
        0xFF00897B.toInt(), // Eb
        0xFF7CB342.toInt(), // E
        0xFF1E88E5.toInt(), // F
        0xFF8E24AA.toInt(), // Gb
        0xFFE53935.toInt(), // G
        0xFFD81B60.toInt(), // Ab
        0xFF42A5F5.toInt(), // A  (azul)
        0xFF6D4C41.toInt(), // Bb
        0xFFFB8C00.toInt()  // B
    )

    fun baseColor(rootPc: Int): Int {
        val pc = ((rootPc % 12) + 12) % 12
        return BASE_COLORS[pc]
    }

    /**
     * A legible version of the chord colour for text labels. Keeps the hue
     * but lightens very dark chords so the letter stays readable on dark UI.
     */
    fun labelColor(rootPc: Int): Int {
        val color = baseColor(rootPc)
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
        return if (luminance < 0.34) lighten(color, 0.42f) else color
    }

    fun shadeForRole(color: Int, relativeSemitones: Int): Int {
        val amount = when (((relativeSemitones % 12) + 12) % 12) {
            0 -> 0f
            3, 4 -> 0.30f
            6, 7, 8 -> 0.48f
            9 -> 0.22f
            10, 11 -> 0.16f
            else -> 0.38f
        }
        return lighten(color, amount)
    }

    private fun lighten(color: Int, amount: Float): Int {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val newRed = (red + (255 - red) * amount).toInt().coerceIn(0, 255)
        val newGreen = (green + (255 - green) * amount).toInt().coerceIn(0, 255)
        val newBlue = (blue + (255 - blue) * amount).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (newRed shl 16) or (newGreen shl 8) or newBlue
    }
}
