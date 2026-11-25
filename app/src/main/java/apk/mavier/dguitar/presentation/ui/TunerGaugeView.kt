package apk.mavier.dguitar.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class TunerGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val maxPoints = 90
    private val history = ArrayDeque<Float>()
    private var signal = false

    private val green = 0xFF3DDC97.toInt()
    private val amber = 0xFFFFC857.toInt()
    private val red = 0xFFFF6B6B.toInt()
    private val dim = 0xFF8E88A3.toInt()

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2E293D.toInt()
        strokeWidth = dp(1.5f)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2E293D.toInt()
        strokeWidth = dp(1.5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val waveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(9f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF17141F.toInt()
    }
    private val circleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textSize = dp(13f)
    }
    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF8E88A3.toInt()
        textSize = dp(24f)
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    fun updateReading(cents: Float, hasSignal: Boolean) {
        signal = hasSignal
        if (hasSignal) {
            history.addFirst(cents.coerceIn(-50f, 50f))
            while (history.size > maxPoints) history.removeLast()
        }
        invalidate()
    }

    fun reset() {
        history.clear()
        signal = false
        invalidate()
    }

    private fun currentCents(): Float = history.firstOrNull() ?: 0f

    private fun accentColor(): Int = when {
        !signal -> dim
        abs(currentCents()) <= 5f -> green
        abs(currentCents()) <= 20f -> amber
        else -> red
    }

    private fun centsToX(cents: Float, halfWidth: Float): Float {
        val clamped = cents.coerceIn(-50f, 50f)
        return clamped / 50f * halfWidth
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val circleRadius = dp(17f)
        val top = paddingTop + circleRadius + dp(8f)
        val axisY = height - paddingBottom - dp(32f)
        if (axisY <= top + dp(40f)) return

        val halfWidth = min(width * 0.22f, dp(64f))
        val accent = accentColor()

        canvas.drawLine(centerX, top, centerX, axisY, centerLinePaint)

        val axisLeft = centerX - halfWidth - dp(28f)
        val axisRight = centerX + halfWidth + dp(28f)
        canvas.drawLine(axisLeft, axisY, axisRight, axisY, axisPaint)
        canvas.drawText("♭", axisLeft, axisY - dp(6f), axisLabelPaint)
        canvas.drawText("♯", axisRight, axisY - dp(6f), axisLabelPaint)

        if (history.size >= 2) {
            val path = Path()
            val glowPath = Path()
            var started = false
            var index = 0
            for (value in history) {
                val y = top + (axisY - top) * index / (maxPoints - 1).toFloat()
                val x = centerX + centsToX(value, halfWidth)
                if (!started) {
                    path.moveTo(x, y)
                    glowPath.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                    glowPath.lineTo(x, y)
                }
                index++
                if (index >= maxPoints) break
            }

            waveGlowPaint.color = accent
            waveGlowPaint.alpha = 45
            canvas.drawPath(glowPath, waveGlowPaint)

            wavePaint.color = accent
            wavePaint.alpha = 235
            canvas.drawPath(path, wavePaint)
        }

        val cents = currentCents()
        val circleX = centerX + centsToX(cents, halfWidth)
        circleStrokePaint.color = accent
        circleFillPaint.color = 0xFF17141F.toInt()

        canvas.drawCircle(circleX, top, circleRadius, circleFillPaint)
        canvas.drawCircle(circleX, top, circleRadius, circleStrokePaint)

        valuePaint.color = accent
        val label = if (signal) cents.roundToInt().toString() else "—"
        val baseline = top - (valuePaint.descent() + valuePaint.ascent()) / 2f
        canvas.drawText(label, circleX, baseline, valuePaint)
    }
}
