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
 * Three concentric rings of the circle of fifths: majors, relative minors and
 * the key signature. Tapping a sector selects that key.
 */
class CircleOfFifthsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onKeySelected: ((Int) -> Unit)? = null

    private val keys: List<CircleKey> = CircleOfFifths.keys
    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    var selectedIndex: Int = 0
        set(value) {
            val normalized = ((value % keys.size) + keys.size) % keys.size
            if (field == normalized) return
            field = normalized
            updateContentDescription()
            invalidate()
        }

    var minorMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
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

    var chordMajorIndices: Set<Int> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var chordMinorIndices: Set<Int> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private var pressedIndex = -1

    private val surfaceColor = context.getColor(R.color.surface_elevated)
    private val borderColor = context.getColor(R.color.chip_border)
    private val textPrimary = context.getColor(R.color.text_primary)
    private val textSecondary = context.getColor(R.color.text_secondary)
    private val textMuted = context.getColor(R.color.text_muted)
    private val activeColor = context.getColor(R.color.circle_active)
    private val chordColor = context.getColor(R.color.circle_chord)
    private val accentColor = context.getColor(R.color.accent)
    private val sharpColor = context.getColor(R.color.circle_sharp)
    private val flatColor = context.getColor(R.color.circle_flat)
    private val onActiveText = context.getColor(R.color.surface_elevated)

    private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        color = surfaceColor
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private val signaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
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

    private val outerRect = RectF()

    init {
        isClickable = true
        isFocusable = true
        updateContentDescription()
    }

    private fun ringBounds(cx: Float, cy: Float, radius: Float) {
        outerRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
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
        val innerInner = radius * INNER_INNER
        val innerOuter = radius * INNER_OUTER
        val centerRadius = radius * CENTER_RADIUS

        // Band backgrounds.
        drawBand(canvas, cx, cy, outerInner, outerOuter, surfaceColor)
        drawBand(canvas, cx, cy, middleInner, middleOuter, surfaceColor)
        drawBand(canvas, cx, cy, innerInner, innerOuter, surfaceColor)

        val key = keys[selectedIndex]
        val activeMajor = !minorMode
        val signature = key.signature(useFlats)
        val signatureColor = if (signature.isNatural) {
            textMuted
        } else if (signature.sharps) {
            sharpColor
        } else {
            flatColor
        }

        for (index in keys.indices) {
            val start = -90f + index * SECTOR_DEGREES - SECTOR_DEGREES / 2f
            val isPressed = index == pressedIndex
            val isSelected = index == selectedIndex
            val isChordMajor = index in chordMajorIndices
            val isChordMinor = index in chordMinorIndices

            // Major ring.
            val majorFill = when {
                isPressed -> accentColor
                isSelected && activeMajor -> activeColor
                isChordMajor -> chordColor
                isSelected -> chordColor
                else -> null
            }
            if (majorFill != null) {
                drawSector(canvas, cx, cy, outerInner, outerOuter, start, majorFill)
            }

            // Relative minor ring.
            val minorFill = when {
                isPressed -> accentColor
                isSelected && !activeMajor -> activeColor
                isChordMinor -> chordColor
                isSelected -> chordColor
                else -> null
            }
            if (minorFill != null) {
                drawSector(canvas, cx, cy, middleInner, middleOuter, start, minorFill)
            }

            // Key signature ring.
            val signatureFill = when {
                isPressed -> accentColor
                isSelected -> dim(signatureColor, 0.35f)
                else -> null
            }
            if (signatureFill != null) {
                drawSector(canvas, cx, cy, innerInner, innerOuter, start, signatureFill)
            }
        }

        drawRingOutline(canvas, cx, cy, outerInner, outerOuter)
        drawRingOutline(canvas, cx, cy, middleInner, middleOuter)
        drawRingOutline(canvas, cx, cy, innerInner, innerOuter)

        // Text per sector.
        val fontScale = resources.configuration.fontScale.coerceIn(1f, 1.3f)
        textPaint.textSize = radius * MAJOR_TEXT * fontScale
        signaturePaint.textSize = radius * SIGNATURE_TEXT * fontScale
        for (index in keys.indices) {
            val angle = -90f + index * SECTOR_DEGREES
            val selected = index == selectedIndex
            val pressed = index == pressedIndex
            val isChordMajor = index in chordMajorIndices
            val isChordMinor = index in chordMinorIndices
            val row = keys[index]

            textPaint.color = when {
                pressed -> textPrimary
                isChordMajor || (selected && activeMajor) -> onActiveText
                else -> textPrimary
            }
            drawRingText(
                canvas, cx, cy, angle,
                (outerInner + outerOuter) / 2f,
                row.majorName(useFlats),
                textPaint
            )

            textPaint.textSize = radius * MINOR_TEXT * fontScale
            textPaint.color = when {
                pressed -> textPrimary
                isChordMinor || (selected && !activeMajor) -> onActiveText
                else -> textSecondary
            }
            drawRingText(
                canvas, cx, cy, angle,
                (middleInner + middleOuter) / 2f,
                row.minorName(useFlats),
                textPaint
            )
            textPaint.textSize = radius * MAJOR_TEXT * fontScale

            val signatureText = signatureLabel(row, useFlats)
            signaturePaint.color = when {
                pressed -> textPrimary
                selected -> textPrimary
                else -> textMuted
            }
            drawRingText(
                canvas, cx, cy, angle,
                (innerInner + innerOuter) / 2f,
                signatureText,
                signaturePaint
            )
        }

        // Center disc with the active key.
        canvas.drawCircle(cx, cy, centerRadius, centerPaint)
        canvas.drawCircle(cx, cy, centerRadius, centerBorderPaint)
        val centerName = if (minorMode) {
            key.minorName(useFlats).removeSuffix("m")
        } else {
            key.majorName(useFlats)
        }
        centerTitlePaint.textSize = centerRadius * CENTER_TITLE * fontScale
        centerModePaint.textSize = centerRadius * CENTER_MODE * fontScale
        canvas.drawText(
            centerName,
            cx,
            cy - centerRadius * 0.05f + centerTitlePaint.textSize / 3f,
            centerTitlePaint
        )
        canvas.drawText(
            context.getString(
                if (minorMode) R.string.circle_mode_minor else R.string.circle_mode_major
            ),
            cx,
            cy + centerRadius * 0.45f,
            centerModePaint
        )
    }

    private fun drawBand(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        inner: Float,
        outer: Float,
        color: Int
    ) {
        sectorPaint.color = color
        sectorPaint.strokeWidth = outer - inner
        ringBounds(cx, cy, (inner + outer) / 2f)
        canvas.drawCircle(cx, cy, (inner + outer) / 2f, sectorPaint)
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
        targetPaint.color = color
        targetPaint.strokeWidth = outer - inner
        ringBounds(cx, cy, (inner + outer) / 2f)
        canvas.drawArc(outerRect, start, SECTOR_SWEEP, false, targetPaint)
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

    private fun signatureLabel(key: CircleKey, useFlats: Boolean): String {
        val signature = key.signature(useFlats)
        if (signature.isNatural) return "0"
        return "${signature.count}${if (signature.sharps) "#" else "b"}"
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
        return ((raw % keys.size) + keys.size) % keys.size
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = sectorAt(event.x, event.y) ?: -1
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val current = sectorAt(event.x, event.y) ?: -1
                if (current != pressedIndex) {
                    pressedIndex = current
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val index = sectorAt(event.x, event.y)
                pressedIndex = -1
                invalidate()
                if (index != null) {
                    select(index)
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
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

    private fun select(index: Int) {
        if (index == selectedIndex) {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
            return
        }
        selectedIndex = index
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        onKeySelected?.invoke(index)
    }

    private fun updateContentDescription() {
        val key = keys[selectedIndex]
        val major = context.getString(
            R.string.circle_major_short,
            key.majorName(useFlats)
        )
        val minor = context.getString(
            R.string.circle_minor_short,
            key.minorName(useFlats).removeSuffix("m")
        )
        val signature = key.signature(useFlats)
        val signatureText = when {
            signature.isNatural -> context.getString(R.string.circle_signature_none)
            signature.sharps && signature.count == 1 ->
                context.getString(
                    R.string.circle_signature_sharp_one,
                    signature.accidentals.joinToString(", ")
                )
            signature.sharps ->
                context.getString(
                    R.string.circle_signature_sharp_many,
                    signature.count,
                    signature.accidentals.joinToString(", ")
                )
            signature.count == 1 ->
                context.getString(
                    R.string.circle_signature_flat_one,
                    signature.accidentals.joinToString(", ")
                )
            else ->
                context.getString(
                    R.string.circle_signature_flat_many,
                    signature.count,
                    signature.accidentals.joinToString(", ")
                )
        }
        contentDescription = context.getString(
            R.string.circle_wheel_description,
            context.getString(R.string.circle_key_description, major, minor, signatureText)
        )
    }

    private companion object {
        const val SECTOR_DEGREES = 30f
        const val SECTOR_SWEEP = 28.4f

        const val OUTER_INNER = 0.70f
        const val OUTER_OUTER = 0.99f
        const val MIDDLE_INNER = 0.47f
        const val MIDDLE_OUTER = 0.69f
        const val INNER_INNER = 0.26f
        const val INNER_OUTER = 0.46f
        const val CENTER_RADIUS = 0.25f

        const val MAJOR_TEXT = 0.10f
        const val MINOR_TEXT = 0.08f
        const val SIGNATURE_TEXT = 0.065f
        const val CENTER_TITLE = 0.55f
        const val CENTER_MODE = 0.26f
    }
}
