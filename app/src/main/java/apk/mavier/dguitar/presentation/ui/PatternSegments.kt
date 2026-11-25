package apk.mavier.dguitar.presentation.ui

import apk.mavier.dguitar.domain.model.CagedPatternOccurrence
import kotlin.math.abs

data class PatternSegment(
    val number: Int,
    val lane: Int,
    val left: Float,
    val right: Float,
    val barY: Float
)

object PatternSegments {

    private const val POSITION_LANES = 2
    private const val EDGE_DP = 3f
    private const val HOOK_DP = 5f
    private const val LANE_BASE_DP = 12f
    private const val LANE_GAP_DP = 8f
    private const val MIN_DRAW_WIDTH_DP = 8f
    private const val HIT_TOLERANCE_DP = 12f

    private fun dp(value: Float, density: Float): Float = value * density

    fun assignLanes(occurrences: List<CagedPatternOccurrence>): IntArray {
        val laneEnds = IntArray(POSITION_LANES) { -1 }
        val lanes = IntArray(occurrences.size)
        for (index in occurrences.indices) {
            val occurrence = occurrences[index]
            var lane = -1
            for (candidate in 0 until POSITION_LANES) {
                if (laneEnds[candidate] <= occurrence.startFret) {
                    lane = candidate
                    break
                }
            }
            if (lane == -1) lane = index % POSITION_LANES
            lanes[index] = lane
            if (occurrence.endFret > laneEnds[lane]) laneEnds[lane] = occurrence.endFret
        }
        return lanes
    }

    fun layout(
        occurrences: List<CagedPatternOccurrence>,
        boardStartX: Float,
        fretWidth: Float,
        scrollX: Float,
        width: Int,
        height: Int,
        density: Float
    ): List<PatternSegment> {
        if (occurrences.isEmpty()) return emptyList()

        val lanes = assignLanes(occurrences)
        val edge = dp(EDGE_DP, density)
        val laneBase = height - dp(LANE_BASE_DP, density)
        val laneGap = dp(LANE_GAP_DP, density)
        val result = ArrayList<PatternSegment>(occurrences.size)

        for (index in occurrences.indices) {
            val occurrence = occurrences[index]
            val left = if (occurrence.startFret == 0) {
                boardStartX
            } else {
                (boardStartX + (occurrence.startFret - 1) * fretWidth - scrollX + edge)
                    .coerceAtLeast(boardStartX)
            }
            var right = boardStartX + occurrence.endFret * fretWidth - scrollX - edge
            if (right < boardStartX || left > width) continue

            right = right.coerceAtMost(width.toFloat())
            val barY = laneBase - lanes[index] * laneGap
            result.add(
                PatternSegment(
                    number = occurrence.number,
                    lane = lanes[index],
                    left = left,
                    right = right,
                    barY = barY
                )
            )
        }
        return result
    }

    fun hit(segments: List<PatternSegment>, x: Float, y: Float, density: Float): Int? {
        val tolerance = dp(HIT_TOLERANCE_DP, density)
        var bestNumber: Int? = null
        var bestDistance = Float.MAX_VALUE

        for (segment in segments) {
            val distance = abs(y - segment.barY)
            if (x in segment.left..segment.right && distance <= tolerance && distance < bestDistance) {
                bestDistance = distance
                bestNumber = segment.number
            }
        }
        return bestNumber
    }

    fun isDrawable(segment: PatternSegment, density: Float): Boolean =
        segment.right - segment.left >= dp(MIN_DRAW_WIDTH_DP, density)

    fun hookSize(density: Float): Float = dp(HOOK_DP, density)
}
