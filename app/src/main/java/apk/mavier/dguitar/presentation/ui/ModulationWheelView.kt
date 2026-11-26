package apk.mavier.dguitar.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.animation.ValueAnimator
import androidx.core.graphics.withRotation
import apk.mavier.dguitar.R
import apk.mavier.dguitar.domain.model.CircleKey
import apk.mavier.dguitar.domain.model.CircleOfFifths
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Wheel used by the modulation module. The outer ring holds the major keys and
 * the inner ring their relative minors. Tapping plays a key, holding it opens
 * the note detail and starts a slow "breathing" pulse.
 */
class ModulationWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onKeySelected: ((Int) -> Unit)? = null
    var onKeyLongPressed: ((Int) -> Unit)? = null

    private val keys: List<CircleKey> = CircleOfFifths.keys
    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    var selectedIndex: Int = 0
        set(value) {
            val normalized = normalize(value)
            if (field == normalized) return
            field = normalized
            updateContentDescription()
            invalidate()
        }

    var useFlats: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateContentDescription()
            invalidate()
        }

    var compact: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private var pressedIndex = -1
    private var longPressTriggered = false
    private var pulseIndex = -1
    private var pulseValue = 0f
    private var pulseAnimator: ValueAnimator? = null

    private val longPressRunnable = Runnable {
        val index = pressedIndex
        if (index < 0) return@Runnable
        longPressTriggered = true
        if (index != selectedIndex) {
            selectedIndex = index
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        }
        startPulse(index)
        onKeyLongPressed?.invoke(index)
        performLongClick()
    }

    private val surfaceColor = context.getColor(R.color.surface_elevated)
    private val borderColor = context.getColor(R.color.chip_border)
    private val textPrimary = context.getColor(R.color.text_primary)
    private val textSecondary = context.getColor(R.color.text_secondary)
    private val textMuted = context.getColor(R.color.text_muted)
    private val warmColor = context.getColor(R.color.circle_chord)
    private val warmActive = context.getColor(R.color.circle_active)
    private val coolColor = context.getColor(R.color.circle_flat)
    private val accentColor = context.getColor(R.color.accent)
    private val onActiveText = context.getColor(R.color.surface_elevated)

    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = borderColor
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surfaceColor }
    private val centerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = borderColor
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val centerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        color = textPrimary
    }
    private val centerModePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        color = textSecondary
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = accentColor
    }

    private val arcRect = RectF()

    init {
        isClickable = true
        isFocusable = true
        isLongClickable = true
        updateContentDescription()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val radius = min(right - left, bottom - top) / 2f - dp(2f)
        if (radius <= 0f) return

        val outerInner = radius * OUTER_INNER
        val outerOuter = radius * OUTER_OUTER
        val middleInner = radius * MIDDLE_INNER
        val middleOuter = radius * MIDDLE_OUTER
        val centerRadius = radius * CENTER_RADIUS

        drawBand(canvas, cx, cy, outerInner, outerOuter, surfaceColor)
        drawBand(canvas, cx, cy, middleInner, middleOuter, surfaceColor)

        val key = keys[selectedIndex]
        for (index in keys.indices) {
            val start = -90f + index * SECTOR_DEGREES - SECTOR_DEGREES / 2f
            val pressed = index == pressedIndex
            val selected = index == selectedIndex

            val majorFill = when {
                pressed -> accentColor
                selected -> warmActive
                else -> dim(warmColor, 0.20f)
            }
            drawSector(canvas, cx, cy, outerInner, outerOuter, start, majorFill)

            val minorFill = when {
                pressed -> accentColor
                selected -> coolColor
                else -> coolColor
            }
            drawSector(
                canvas,
                cx,
                cy,
                middleInner,
                middleOuter,
                start,
                dim(minorFill, if (selected) 0.82f else 0.28f)
            )

            if (index == pulseIndex) {
                pulsePaint.alpha = (45 + 145 * pulseValue).toInt().coerceIn(0, 255)
                pulsePaint.strokeWidth = dp(3f) + dp(5f) * pulseValue
                arcRect.set(
                    cx - (outerOuter + dp(2f)),
                    cy - (outerOuter + dp(2f)),
                    cx + (outerOuter + dp(2f)),
                    cy + (outerOuter + dp(2f))
                )
                canvas.drawArc(arcRect, start, SECTOR_SWEEP, false, pulsePaint)
            }
        }

        drawRingOutline(canvas, cx, cy, outerInner, outerOuter)
        drawRingOutline(canvas, cx, cy, middleInner, middleOuter)

        val fontScale = resources.configuration.fontScale.coerceIn(1f, 1.3f)
        for (index in keys.indices) {
            val angle = -90f + index * SECTOR_DEGREES
            val selected = index == selectedIndex
            val row = keys[index]

            textPaint.textSize = radius * MAJOR_TEXT * fontScale
            textPaint.color = if (selected) onActiveText else textPrimary
            drawRingText(
                canvas,
                cx,
                cy,
                angle,
                (outerInner + outerOuter) / 2f,
                row.majorName(useFlats),
                textPaint
            )

            textPaint.textSize = radius * MINOR_TEXT * fontScale
            textPaint.color = if (selected) onActiveText else textMuted
            drawRingText(
                canvas,
                cx,
                cy,
                angle,
                (middleInner + middleOuter) / 2f,
                row.minorName(useFlats),
                textPaint
            )
        }

        canvas.drawCircle(cx, cy, centerRadius, centerPaint)
        canvas.drawCircle(cx, cy, centerRadius, centerBorderPaint)

        centerTitlePaint.textSize = centerRadius * CENTER_TITLE * fontScale
        centerModePaint.textSize = centerRadius * CENTER_MODE * fontScale
        val centerName = key.majorName(useFlats)
        canvas.drawText(
            centerName,
            cx,
            cy - centerRadius * 0.05f + centerTitlePaint.textSize / 3f,
            centerTitlePaint
        )
        if (!compact) {
            canvas.drawText(
                context.getString(R.string.circle_mode_major),
                cx,
                cy + centerRadius * 0.48f,
                centerModePaint
            )
        }
    }

    private fun drawBand(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        inner: Float,
        outer: Float,
        color: Int
    ) {
        bandPaint.color = color
        bandPaint.strokeWidth = outer - inner
        canvas.drawCircle(cx, cy, (inner + outer) / 2f, bandPaint)
    }

    private fun drawSector(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        inner: Float,
        outer: Float,
        start: Float,
        color: Int
    ) {
        bandPaint.color = color
        bandPaint.strokeWidth = outer - inner
        arcRect.set(
            cx - (inner + outer) / 2f,
            cy - (inner + outer) / 2f,
            cx + (inner + outer) / 2f,
            cy + (inner + outer) / 2f
        )
        canvas.drawArc(arcRect, start, SECTOR_SWEEP, false, bandPaint)
    }

    private fun drawRingOutline(canvas: Canvas, cx: Float, cy: Float, inner: Float, outer: Float) {
        canvas.drawCircle(cx, cy, inner, ringPaint)
        canvas.drawCircle(cx, cy, outer, ringPaint)
    }

    private fun drawRingText(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        angleDeg: Float,
        radius: Float,
        text: String,
        paint: Paint
    ) {
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val x = cx + radius * cos(angleRad).toFloat()
        val y = cy + radius * sin(angleRad).toFloat()
        val rotation = textRotation(angleDeg + 90f)
        val metrics = paint.fontMetrics
        val baselineOffset = -(metrics.ascent + metrics.descent) / 2f

        canvas.withRotation(rotation, x, y) {
            drawText(text, x, y + baselineOffset, paint)
        }
    }

    private fun textRotation(raw: Float): Float {
        var normalized = ((raw % 360f) + 360f) % 360f
        if (normalized > 360f - 0.01f) normalized = 0f
        return when {
            normalized <= 90f -> normalized
            normalized <= 270f -> normalized - 180f
            else -> normalized - 360f
        }
    }

    private fun dim(color: Int, alpha: Float): Int {
        val a = ((color ushr 24) * alpha).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    private fun sectorAt(x: Float, y: Float): Int? {
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val radius = min(right - left, bottom - top) / 2f - dp(2f)
        if (radius <= 0f) return null

        val dx = x - cx
        val dy = y - cy
        val distance = hypot(dx, dy)
        if (distance < radius * CENTER_RADIUS || distance > radius) return null

        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        val raw = ((angle + 90f) / SECTOR_DEGREES).roundToInt()
        return normalize(raw)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = sectorAt(event.x, event.y) ?: -1
                longPressTriggered = false
                if (pressedIndex >= 0) {
                    postDelayed(longPressRunnable, LONG_PRESS_MS)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val current = sectorAt(event.x, event.y) ?: -1
                if (current != pressedIndex) {
                    pressedIndex = current
                    removeCallbacks(longPressRunnable)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                val index = sectorAt(event.x, event.y)
                pressedIndex = -1
                stopPulse()
                invalidate()
                if (index != null && !longPressTriggered) {
                    select(index)
                    performClick()
                }
                longPressTriggered = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                pressedIndex = -1
                stopPulse()
                longPressTriggered = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun performLongClick(): Boolean {
        super.performLongClick()
        return true
    }

    private fun select(index: Int) {
        if (index != selectedIndex) {
            selectedIndex = index
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        }
        onKeySelected?.invoke(index)
    }

    fun stopPulseAnimation() {
        stopPulse()
        invalidate()
    }

    private fun startPulse(index: Int) {
        pulseAnimator?.cancel()
        pulseIndex = index
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PULSE_DURATION_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseValue = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseIndex = -1
        pulseValue = 0f
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        stopPulse()
        super.onDetachedFromWindow()
    }

    private fun updateContentDescription() {
        val key = keys[selectedIndex]
        val description = context.getString(
            R.string.modulation_key_description,
            key.majorName(useFlats),
            key.minorName(useFlats)
        )
        contentDescription = context.getString(
            R.string.modulation_wheel_description,
            description
        )
    }

    private fun normalize(value: Int): Int = ((value % keys.size) + keys.size) % keys.size

    private companion object {
        const val SECTOR_DEGREES = 30f
        const val SECTOR_SWEEP = 28.4f
        const val LONG_PRESS_MS = 450L
        const val PULSE_DURATION_MS = 900L

        const val OUTER_INNER = 0.68f
        const val OUTER_OUTER = 0.99f
        const val MIDDLE_INNER = 0.42f
        const val MIDDLE_OUTER = 0.67f
        const val CENTER_RADIUS = 0.40f

        const val MAJOR_TEXT = 0.105f
        const val MINOR_TEXT = 0.075f
        const val CENTER_TITLE = 0.48f
        const val CENTER_MODE = 0.22f
    }
}
