package apk.mavier.dguitar.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import apk.mavier.dguitar.R
import apk.mavier.dguitar.domain.model.ChordDiagramData
import apk.mavier.dguitar.presentation.theme.ChordColorProvider
import apk.mavier.dguitar.presentation.theme.NoteColorProvider
import kotlin.math.min

class ChordDiagramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var model: ChordDiagramData? = null

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val surfaceColor = context.getColor(R.color.surface_elevated)
    private val borderColor = context.getColor(R.color.chip_border)
    private val textPrimary = context.getColor(R.color.text_primary)
    private val textSecondary = context.getColor(R.color.text_secondary)
    private val textMuted = context.getColor(R.color.text_muted)
    private val accentTeal = context.getColor(R.color.teal)

    private val cardPath = Path()

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surfaceColor }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = borderColor
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        color = 0xFFFFFFFF.toInt()
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        color = 0xE6FFFFFF.toInt()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = 0xFF4A4459.toInt()
    }
    private val nutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        color = 0xFFE8E4F2.toInt()
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val barrePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 215
    }
    private val fretLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = dp(11f)
        color = textSecondary
    }
    private val notesLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        color = textMuted
    }
    private val notesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        color = textPrimary
    }
    private val notesAngloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f)
        color = textSecondary
    }

    fun setData(data: ChordDiagramData?) {
        model = data
        contentDescription = data?.let {
            "${it.title}, ${it.subtitle}. Notas: ${it.notesSolfege.joinToString(", ")}"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = model ?: return
        if (width == 0 || height == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val corner = dp(14f)

        canvas.drawRoundRect(0f, 0f, w, h, corner, corner, cardPaint)

        val micro = h < dp(100f)
        if (micro) {
            borderPaint.color = ChordColorProvider.baseColor(data.rootPc)
            borderPaint.strokeWidth = dp(2f)
            canvas.drawRoundRect(0f, 0f, w, h, corner, corner, borderPaint)
            drawDiagram(
                canvas, data, w, h,
                headerHeight = 0f,
                notesHeight = dp(4f),
                compact = false,
                mini = false,
                micro = true
            )
            return
        }

        borderPaint.color = borderColor
        borderPaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(0f, 0f, w, h, corner, corner, borderPaint)

        val mini = h < dp(150f)
        val compact = h < dp(230f) && !mini
        val headerHeight = when {
            mini -> dp(30f)
            compact -> dp(54f)
            else -> dp(68f)
        }
        val notesHeight = when {
            mini -> dp(20f)
            compact -> dp(48f)
            else -> dp(74f)
        }

        cardPath.reset()
        cardPath.addRoundRect(
            0f,
            0f,
            w,
            headerHeight,
            floatArrayOf(corner, corner, corner, corner, 0f, 0f, 0f, 0f),
            Path.Direction.CW
        )
        headerPaint.color = ChordColorProvider.baseColor(data.rootPc)
        canvas.drawPath(cardPath, headerPaint)

        val onHeader = NoteColorProvider.textColorFor(headerPaint.color)
        titlePaint.color = onHeader
        subtitlePaint.color = onHeader
        subtitlePaint.alpha = 225

        drawHeader(canvas, data, w, headerHeight, compact, mini)
        drawDiagram(canvas, data, w, h, headerHeight, notesHeight, compact, mini, micro = false)
        drawNotes(canvas, data, w, h, notesHeight, compact, mini)
    }

    private fun drawHeader(
        canvas: Canvas,
        data: ChordDiagramData,
        w: Float,
        headerHeight: Float,
        compact: Boolean,
        mini: Boolean
    ) {
        if (mini) {
            titlePaint.textSize = fittedTextSize(data.title, w - dp(16f), dp(18f), titlePaint)
            canvas.drawText(data.title, w / 2f, headerHeight * 0.72f, titlePaint)
            return
        }

        val titleStart = if (compact) dp(22f) else dp(30f)
        titlePaint.textSize = fittedTextSize(data.title, w - dp(32f), titleStart, titlePaint)
        val titleBaseline = if (compact) headerHeight * 0.55f else headerHeight * 0.5f
        canvas.drawText(data.title, w / 2f, titleBaseline, titlePaint)

        val subtitleStart = if (compact) dp(9f) else dp(12f)
        subtitlePaint.textSize = fittedTextSize(data.subtitle, w - dp(32f), subtitleStart, subtitlePaint)
        val subtitleBaseline = headerHeight - if (compact) dp(7f) else dp(11f)
        canvas.drawText(data.subtitle, w / 2f, subtitleBaseline, subtitlePaint)
    }

    private fun drawDiagram(
        canvas: Canvas,
        data: ChordDiagramData,
        w: Float,
        h: Float,
        headerHeight: Float,
        notesHeight: Float,
        compact: Boolean,
        mini: Boolean,
        micro: Boolean
    ) {
        val stringCount = data.strings.size
        if (stringCount == 0) return

        val left = when {
            micro -> dp(8f)
            mini -> dp(12f)
            compact -> dp(24f)
            else -> dp(36f)
        }
        val right = w - when {
            micro -> dp(8f)
            mini -> dp(10f)
            compact -> dp(16f)
            else -> dp(22f)
        }
        val step = if (stringCount > 1) (right - left) / (stringCount - 1) else 0f

        val markerSpace = when {
            micro -> dp(12f)
            mini -> dp(12f)
            compact -> dp(24f)
            else -> dp(40f)
        }
        val gridTop = headerHeight + markerSpace
        val gridBottom = h - notesHeight
        if (gridBottom <= gridTop) return

        val rows = data.fretSpan.coerceAtLeast(1)
        val rowHeight = (gridBottom - gridTop) / rows
        val markerOffset = when {
            micro -> dp(5f)
            mini -> dp(7f)
            compact -> dp(9f)
            else -> dp(12f)
        }
        val markerSize = when {
            micro -> dp(8f)
            mini -> dp(9f)
            compact -> dp(11f)
            else -> dp(14f)
        }

        for (index in 0 until stringCount) {
            val x = left + index * step
            canvas.drawLine(x, gridTop, x, gridBottom, gridPaint)
        }

        for (row in 0..rows) {
            val y = gridTop + row * rowHeight
            if (row == 0 && data.baseFret == 1) {
                canvas.drawLine(left, y, right, y, nutPaint)
            } else {
                canvas.drawLine(left, y, right, y, gridPaint)
            }
        }

        if (data.baseFret > 1 && !micro) {
            val baseline = gridTop + rowHeight / 2f -
                (fretLabelPaint.ascent() + fretLabelPaint.descent()) / 2f
            canvas.drawText("${data.baseFret}fr", left - dp(8f), baseline, fretLabelPaint)
        }

        for (index in 0 until stringCount) {
            val fret = data.strings[index].fret ?: continue
            val x = left + index * step
            val marker = when (fret) {
                0 -> "O"
                else -> null
            }
            if (marker != null) {
                markerPaint.color = accentTeal
                markerPaint.textSize = markerSize
                val baseline = gridTop - markerOffset
                canvas.drawText(marker, x, baseline, markerPaint)
            }
        }

        for (index in 0 until stringCount) {
            if (data.strings[index].fret != null) continue
            markerPaint.color = textMuted
            markerPaint.textSize = markerSize
            val baseline = gridTop - markerOffset
            canvas.drawText("X", left + index * step, baseline, markerPaint)
        }

        drawBarre(canvas, data, left, step, gridTop, rowHeight, rows)

        val maxRadius = min(rowHeight * 0.45f, step * 0.34f)
            .coerceAtLeast(
                when {
                    micro -> dp(3.5f)
                    mini -> dp(4f)
                    compact -> dp(5f)
                    else -> dp(7f)
                }
            )
        val showDotLetters = !micro && (!mini || maxRadius >= dp(6f))
        for (index in 0 until stringCount) {
            val string = data.strings[index]
            val fret = string.fret ?: continue
            if (fret <= 0) continue
            if (data.barreFret != null && fret == data.barreFret) continue

            val row = fret - data.baseFret
            if (row < 0 || row >= rows) continue

            val x = left + index * step
            val y = gridTop + (row + 0.5f) * rowHeight
            val fill = string.roleSemitone?.let { NoteColorProvider.chordRoleColor(it) }
                ?: NoteColorProvider.ATTENUATED_FILL
            dotPaint.color = fill
            canvas.drawCircle(x, y, maxRadius, dotPaint)

            if (showDotLetters) {
                dotTextPaint.color = NoteColorProvider.textColorFor(fill)
                dotTextPaint.textSize = maxRadius * 0.95f
                val baseline = y - (dotTextPaint.ascent() + dotTextPaint.descent()) / 2f
                canvas.drawText(string.noteName, x, baseline, dotTextPaint)
            }
        }
    }

    private fun drawBarre(
        canvas: Canvas,
        data: ChordDiagramData,
        left: Float,
        step: Float,
        gridTop: Float,
        rowHeight: Float,
        rows: Int
    ) {
        val barre = data.barreFret ?: return
        val row = barre - data.baseFret
        if (row < 0 || row >= rows) return

        val columns = data.strings.indices.filter { index ->
            val fret = data.strings[index].fret
            fret != null && fret >= barre
        }
        if (columns.isEmpty()) return

        val startX = left + columns.first() * step
        val endX = left + columns.last() * step
        val y = gridTop + (row + 0.5f) * rowHeight

        barrePaint.color = ChordColorProvider.baseColor(data.rootPc)
        barrePaint.strokeWidth = rowHeight * 0.62f
        canvas.drawLine(startX, y, endX, y, barrePaint)
    }

    private fun drawNotes(
        canvas: Canvas,
        data: ChordDiagramData,
        w: Float,
        h: Float,
        notesHeight: Float,
        compact: Boolean,
        mini: Boolean
    ) {
        val solfege = data.notesSolfege.joinToString(" · ")
        if (mini) {
            val left = dp(8f)
            val right = w - dp(8f)
            notesPaint.textSize = fittedTextSize(solfege, right - left, dp(10f), notesPaint)
            val baseline = h - dp(6f)
            canvas.drawText(solfege, left, baseline, notesPaint)
            return
        }

        val side = if (compact) dp(18f) else dp(24f)
        val left = side
        val right = w - side
        val dividerY = h - notesHeight - dp(1f)

        canvas.drawLine(left, dividerY, right, dividerY, gridPaint)

        notesLabelPaint.textSize = if (compact) dp(8f) else dp(10f)
        notesLabelPaint.color = textMuted
        val labelBaseline = dividerY + if (compact) dp(12f) else dp(17f)
        canvas.drawText("NOTAS", left, labelBaseline, notesLabelPaint)

        val solfegeStart = if (compact) dp(14f) else dp(18f)
        notesPaint.textSize = fittedTextSize(solfege, right - left, solfegeStart, notesPaint)
        val solfegeBaseline = labelBaseline + if (compact) dp(15f) else dp(26f)
        canvas.drawText(solfege, left, solfegeBaseline, notesPaint)

        val anglo = data.notesAnglo.joinToString(" · ")
        val angloStart = if (compact) dp(9f) else dp(11f)
        notesAngloPaint.textSize = fittedTextSize(anglo, right - left, angloStart, notesAngloPaint)
        val angloBaseline = solfegeBaseline + if (compact) dp(13f) else dp(16f)
        canvas.drawText(anglo, left, angloBaseline, notesAngloPaint)
    }

    private fun fittedTextSize(text: String, maxWidth: Float, startSize: Float, paint: Paint): Float {
        var size = startSize
        paint.textSize = size
        while (paint.measureText(text) > maxWidth && size > dp(10f)) {
            size -= dp(1f)
            paint.textSize = size
        }
        return size
    }
}
