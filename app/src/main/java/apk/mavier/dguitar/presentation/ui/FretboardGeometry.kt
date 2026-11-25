package apk.mavier.dguitar.presentation.ui

import kotlin.math.max
import kotlin.math.min

class FretboardGeometry private constructor(
    val gutterWidth: Float,
    val fretWidth: Float,
    val boardStartX: Float,
    val stringYs: FloatArray,
    val noteRadius: Float,
    val maxScroll: Float
) {

    fun fretX(fret: Int, scrollX: Float): Float =
        boardStartX + fret * fretWidth - scrollX

    fun cellCenterX(fret: Int, scrollX: Float): Float =
        boardStartX + (fret - 0.5f) * fretWidth - scrollX

    fun stringThickness(index: Int, density: Float): Float {
        val count = stringYs.size
        if (count <= 1) return dp(3f, density)
        return dp(1.4f + 3.2f * index / (count - 1), density)
    }

    fun clampScroll(scrollX: Float): Float = scrollX.coerceIn(0f, maxScroll)

    companion object {

        private fun dp(value: Float, density: Float): Float = value * density

        fun compute(
            width: Int,
            height: Int,
            stringCount: Int,
            density: Float,
            fretCount: Int
        ): FretboardGeometry? {
            if (width == 0 || height == 0) return null

            val gutterWidth = dp(50f, density)
            val fretWidth = dp(64f, density)
            val boardStartX = gutterWidth + dp(4f, density)

            val top = dp(22f, density)
            val bottom = (height - dp(66f, density)).coerceAtLeast(top + dp(40f, density))
            val count = stringCount.coerceAtLeast(1)

            val stringYs = FloatArray(count)
            if (count == 1) {
                stringYs[0] = (top + bottom) / 2f
            } else {
                val gap = (bottom - top) / (count - 1)
                for (index in 0 until count) {
                    stringYs[index] = top + gap * index
                }
            }

            val gap = if (count > 1) (bottom - top) / (count - 1) else dp(40f, density)
            val noteRadius = min(fretWidth * 0.34f, gap * 0.38f)

            val viewport = width - boardStartX
            val maxScroll = max(0f, fretCount * fretWidth - viewport)

            return FretboardGeometry(
                gutterWidth = gutterWidth,
                fretWidth = fretWidth,
                boardStartX = boardStartX,
                stringYs = stringYs,
                noteRadius = noteRadius,
                maxScroll = maxScroll
            )
        }
    }
}
