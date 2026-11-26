package apk.mavier.dguitar.presentation.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import apk.mavier.dguitar.R

class ModulationPulseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private var colors: List<Int> = emptyList()
    private var changeIndex = -1
    private var activeIndex = -1
    private var pulseValue = 0f
    private var animator: ValueAnimator? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(9f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        color = context.getColor(R.color.text_primary)
    }
    private val markerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.modulation_common)
    }
    private val changeLabel = context.getString(R.string.modulation_change_marker)
    private val rect = RectF()

    init {
        contentDescription = context.getString(R.string.modulation_pulse_description)
    }

    fun setBars(barColors: List<Int>, changeIndex: Int) {
        this.colors = barColors
        this.changeIndex = changeIndex
        activeIndex = -1
        pulseValue = 0f
        invalidate()
    }

    fun pulse(index: Int) {
        if (index !in colors.indices) return
        animator?.cancel()
        activeIndex = index
        pulseValue = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PULSE_MS
            addUpdateListener {
                pulseValue = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun reset() {
        animator?.cancel()
        animator = null
        activeIndex = -1
        pulseValue = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (colors.isEmpty()) return

        val count = colors.size
        val padding = dp(8f)
        val gap = dp(6f)
        val available = width - padding * 2f - gap * (count - 1).coerceAtLeast(0)
        val barWidth = available / count
        if (barWidth <= 0f) return

        val baseTop = height * 0.34f
        val maxHeight = height * 0.56f

        for (index in colors.indices) {
            val left = padding + index * (barWidth + gap)
            val right = left + barWidth
            val extra = if (index == activeIndex) maxHeight * pulseValue else 0f
            val top = (baseTop - extra).coerceAtLeast(dp(14f))
            val bottom = height - padding

            barPaint.color = colors[index]
            barPaint.alpha = if (index == activeIndex) 255 else 155
            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, dp(6f), dp(6f), barPaint)

            if (index == changeIndex) {
                val cx = (left + right) / 2f
                canvas.drawCircle(cx, dp(7f), dp(2.4f), markerDotPaint)
                canvas.drawText(
                    changeLabel,
                    cx,
                    height - dp(1f),
                    markerPaint
                )
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val PULSE_MS = 280L
    }
}
