package apk.mavier.dguitar.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import apk.mavier.dguitar.domain.model.CagedPatternOccurrence
import apk.mavier.dguitar.domain.model.CagedPatterns
import apk.mavier.dguitar.domain.model.ChordVoicing
import apk.mavier.dguitar.domain.model.ChordVoicingResolver
import apk.mavier.dguitar.domain.model.VoicingKey
import apk.mavier.dguitar.presentation.theme.ChordColorProvider
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.Improvisation
import apk.mavier.dguitar.presentation.theme.NoteColorProvider
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.ViewMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class FretboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onNotePlayed: ((stringIndex: Int, midi: Int) -> Unit)? = null
    var onPatternSelected: ((pattern: Int?) -> Unit)? = null

    var config: FretboardConfig = FretboardConfig()
        set(value) {
            field = value
            playedString = -1
            playedFret = -1
            renderModel = null
            renderModelConfig = null
            voicingCache = null
            voicingConfig = null
            improvLines = emptyList()
            improvLinesConfig = null
            ensureGeometry()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6A4A3A.toInt() }
    private val boardEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = 0xFF3A241C.toInt()
        alpha = 210
    }
    private val nutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8DFC9.toInt() }
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD8D1C2.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val stringGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9B8CFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC6B8A5.toInt()
        strokeWidth = dp(2f)
        alpha = 215
    }
    private val inlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFFFFF }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x33000000
    }
    private val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val openPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val openTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val fretNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF8E88A3.toInt()
        textSize = dp(12f)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val patternNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(9f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val degreePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(9f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val degreeBadgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF014111C.toInt()
    }
    private val degreeBadgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = 0xFFFFD54F.toInt()
    }
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(2f)
    }
    private val chordRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(2.8f)
    }
    private val chordFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rootRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        color = 0xFFF7F2E7.toInt()
        alpha = 235
    }
    private val barrePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF231713.toInt()
        alpha = 230
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x55FFD54F
    }
    private val improvRolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(9f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val improvLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(2.6f)
        alpha = 185
    }

    private val scroller = OverScroller(context)
    private val velocityTracker = VelocityTracker.obtain()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var scrollX = 0f
    private var maxScroll = 0f
    private var gutterWidth = 0f
    private var fretWidth = 0f
    private var boardStartX = 0f
    private var noteRadius = 0f
    private var stringYs = FloatArray(0)
    private var geometry: FretboardGeometry? = null
    private val segmentPath = Path()
    private val starPath = Path()
    private val improvPath = Path()
    private val boardRect = RectF()
    private val ringRect = RectF()
    private val badgeRect = RectF()

    private var renderModel: FretboardRenderModel? = null
    private var renderModelConfig: FretboardConfig? = null
    private var voicingCache: ChordVoicing? = null
    private var voicingConfig: FretboardConfig? = null
    private var voicingBucket = Int.MIN_VALUE
    private var improvLines: List<List<VoicingKey>> = emptyList()
    private var improvLinesConfig: FretboardConfig? = null

    private var downX = 0f
    private var downY = 0f
    private var scrollStart = 0f
    private var dragging = false

    private var playedString = -1
    private var playedFret = -1

    companion object {
        const val FRET_COUNT = 22
        private val INLAY_FRETS = intArrayOf(3, 5, 7, 9, 15, 17, 19, 21)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            boardPaint.shader = LinearGradient(
                0f,
                0f,
                0f,
                h.toFloat(),
                intArrayOf(0xFF6A4A3A.toInt(), 0xFF4E3328.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        ensureGeometry()
    }

    private fun ensureGeometry() {
        val computed = FretboardGeometry.compute(
            width = width,
            height = height,
            stringCount = config.tuning.strings.size,
            density = density,
            fretCount = FRET_COUNT
        ) ?: return

        geometry = computed
        gutterWidth = computed.gutterWidth
        fretWidth = computed.fretWidth
        boardStartX = computed.boardStartX
        noteRadius = computed.noteRadius
        stringYs = computed.stringYs
        maxScroll = computed.maxScroll
        scrollX = computed.clampScroll(scrollX)
    }

    private fun currentCenterFret(): Float {
        if (width <= 0 || fretWidth <= 0f) return Float.NEGATIVE_INFINITY
        val viewport = width - boardStartX
        return (scrollX + viewport / 2f) / fretWidth + 0.5f
    }

    private fun currentRenderModel(): FretboardRenderModel {
        val cached = renderModel
        if (cached != null && renderModelConfig === config) return cached
        val built = FretboardRenderModel.build(config)
        renderModel = built
        renderModelConfig = config
        return built
    }

    private fun currentVoicing(): ChordVoicing {
        val centerFret = currentCenterFret()
        val bucket = if (config.chordPattern == null) 0 else centerFret.toInt()
        val cached = voicingCache
        if (cached != null && voicingConfig === config && voicingBucket == bucket) {
            return cached
        }
        val computed = ChordVoicingResolver.voicing(config, centerFret)
        voicingCache = computed
        voicingConfig = config
        voicingBucket = bucket
        return computed
    }

    private fun currentImprovLines(): List<List<VoicingKey>> {
        if (improvLinesConfig === config) return improvLines

        val computed = if (config.mode != ViewMode.IMPROV || config.improvOverlayScale) {
            emptyList()
        } else {
            val active = config.improvActive
            if (active == null) {
                emptyList()
            } else {
                val unique = ArrayList<List<VoicingKey>>()
                for (occurrence in CagedPatterns.occurrences) {
                    val positions = improvFormationPositions(config, active, occurrence)
                    if (positions.size >= 2 && unique.none { it == positions }) {
                        unique.add(positions)
                    }
                }
                unique
            }
        }

        improvLines = computed
        improvLinesConfig = config
        return computed
    }

    private fun fretX(fret: Int): Float = geometry?.fretX(fret, scrollX) ?: 0f

    private fun cellCenterX(fret: Int): Float = geometry?.cellCenterX(fret, scrollX) ?: 0f

    private fun stringThickness(index: Int): Float =
        geometry?.stringThickness(index, density) ?: dp(3f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ensureGeometry()
        if (stringYs.isEmpty()) return

        val model = currentRenderModel()
        val chordVoicing = currentVoicing()
        val topY = stringYs.first()
        val bottomY = stringYs.last()
        val boardTop = topY - noteRadius - dp(8f)
        val boardBottom = bottomY + noteRadius + dp(8f)

        canvas.save()
        canvas.clipRect(boardStartX, 0f, width.toFloat(), height.toFloat())

        boardRect.set(boardStartX, boardTop, width.toFloat(), boardBottom)
        canvas.drawRoundRect(boardRect, dp(12f), dp(12f), boardPaint)
        canvas.drawRoundRect(boardRect, dp(12f), dp(12f), boardEdgePaint)

        drawInlays(canvas)

        for (fret in 1..FRET_COUNT) {
            val x = fretX(fret)
            if (x < boardStartX - 1f || x > width + 1f) continue
            canvas.drawLine(x, boardTop + dp(2f), x, boardBottom - dp(2f), fretPaint)
        }

        canvas.drawRect(boardStartX - dp(5f), boardTop, boardStartX, boardBottom, nutPaint)

        for (i in stringYs.indices) {
            stringPaint.strokeWidth = stringThickness(i)
            canvas.drawLine(boardStartX, stringYs[i], width.toFloat(), stringYs[i], stringPaint)
        }

        if (playedString in stringYs.indices) {
            stringGlowPaint.strokeWidth = stringThickness(playedString) + dp(3.5f)
            stringGlowPaint.alpha = 210
            canvas.drawLine(
                boardStartX,
                stringYs[playedString],
                width.toFloat(),
                stringYs[playedString],
                stringGlowPaint
            )
        }

        drawChordBarres(canvas, chordVoicing)
        drawImprovLine(canvas)
        drawNotes(canvas, chordVoicing.positions, model)

        for (fret in 1..FRET_COUNT) {
            val x = cellCenterX(fret)
            if (x < boardStartX - fretWidth || x > width + fretWidth) continue
            canvas.drawText(fret.toString(), x, height - dp(30f), fretNumberPaint)
        }

        drawSegmentBars(canvas)

        canvas.restore()

        drawGutter(canvas, model, chordVoicing)
    }

    private fun drawInlays(canvas: Canvas) {
        val centerY = (stringYs.first() + stringYs.last()) / 2f
        val radius = noteRadius * 0.9f

        for (fret in INLAY_FRETS) {
            val x = cellCenterX(fret)
            if (x < boardStartX - radius || x > width + radius) continue
            drawStar(canvas, x, centerY, radius, inlayPaint)
        }

        val x12 = cellCenterX(12)
        if (x12 >= boardStartX - radius && x12 <= width + radius) {
            val offset = (stringYs.last() - stringYs.first()) * 0.18f
            val dot = radius * 0.55f
            canvas.drawCircle(x12, centerY - offset, dot, inlayPaint)
            canvas.drawCircle(x12, centerY + offset, dot, inlayPaint)
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        starPath.reset()
        val points = 5
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) radius else radius * 0.45f
            val angle = Math.PI / 2.0 + i * Math.PI / points
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy - (r * sin(angle)).toFloat()
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        canvas.drawPath(starPath, paint)
    }

    private fun patternSegments(): List<PatternSegment> {
        if (config.mode == ViewMode.FREE || config.mode == ViewMode.IMPROV) return emptyList()
        return PatternSegments.layout(
            occurrences = CagedPatterns.occurrences,
            boardStartX = boardStartX,
            fretWidth = fretWidth,
            scrollX = scrollX,
            width = width,
            height = height,
            density = density
        )
    }

    private fun drawSegmentBars(canvas: Canvas) {
        val segments = patternSegments()
        if (segments.isEmpty()) return

        val selected = if (config.mode == ViewMode.CHORD) config.chordPattern else config.selectedPattern
        val hook = PatternSegments.hookSize(density)

        for (segment in segments) {
            if (!PatternSegments.isDrawable(segment, density)) continue

            val isActive = selected != null && segment.number == selected
            segmentPaint.color = if (isActive) 0xFFA18CFF.toInt() else 0xFF6A637D.toInt()
            segmentPaint.strokeWidth = if (isActive) dp(2.5f) else dp(1.6f)
            segmentPaint.alpha = if (isActive) 255 else 220

            segmentPath.reset()
            segmentPath.moveTo(segment.left, segment.barY - hook)
            segmentPath.quadTo(segment.left, segment.barY, segment.left + hook, segment.barY)
            segmentPath.lineTo(segment.right - hook, segment.barY)
            segmentPath.quadTo(segment.right, segment.barY, segment.right, segment.barY - hook)
            canvas.drawPath(segmentPath, segmentPaint)

            val labelX = ((segment.left + segment.right) / 2f)
                .coerceIn(boardStartX, width.toFloat())
            patternNumberPaint.color = if (isActive) 0xFFD7CDFF.toInt() else 0xFF9A93AC.toInt()
            patternNumberPaint.alpha = 255
            canvas.drawText(
                segment.number.toString(),
                labelX,
                segment.barY - dp(5f),
                patternNumberPaint
            )
        }
    }

    private fun hitPatternSegment(x: Float, y: Float): Int? =
        PatternSegments.hit(patternSegments(), x, y, density)

    fun currentSequenceOccurrence(): CagedPatternOccurrence? {
        val pattern = config.selectedPattern
        if (pattern == null) {
            return CagedPatterns.occurrences.minByOrNull { it.startFret }
        }

        val occurrences = CagedPatterns.occurrencesFor(pattern)
        if (occurrences.isEmpty()) {
            return CagedPatterns.occurrences.minByOrNull { it.startFret }
        }
        val lowest = occurrences.minByOrNull { it.startFret }
        if (occurrences.size == 1) return lowest

        ensureGeometry()
        if (width <= 0 || fretWidth <= 0f) return lowest

        val viewport = width - boardStartX
        val centerFret = (scrollX + viewport / 2f) / fretWidth + 0.5f
        return occurrences.firstOrNull {
            centerFret >= it.startFret && centerFret <= it.endFret
        } ?: occurrences.minByOrNull { occurrence ->
            when {
                centerFret < occurrence.startFret -> occurrence.startFret - centerFret
                centerFret > occurrence.endFret -> centerFret - occurrence.endFret
                else -> 0f
            }
        } ?: lowest
    }
    fun currentChordFormationPositions(): List<Pair<Int, Int>> {
        val formation = currentVoicing().positions
        return formation
            .sortedByDescending { it.stringIndex }
            .map { it.stringIndex to it.fret }
    }

    private fun improvOccurrence(cfg: FretboardConfig): CagedPatternOccurrence? {
        val active = cfg.improvActive ?: return null
        return ChordVoicingResolver.bestDefaultOccurrence(
            cfg.copy(chord = active.quality, rootPc = active.rootPc)
        )
    }

    private fun improvFormationPositions(
        cfg: FretboardConfig,
        active: ImprovChord,
        occurrence: CagedPatternOccurrence
    ): List<VoicingKey> = ChordVoicingResolver.buildChordFormation(
        cfg.copy(chord = active.quality, rootPc = active.rootPc),
        occurrence
    ).sortedBy { cfg.tuning.strings[it.stringIndex].openMidi + it.fret }

    private fun improvLinePositions(cfg: FretboardConfig): List<VoicingKey> {
        val active = cfg.improvActive ?: return emptyList()
        val occurrence = improvOccurrence(cfg) ?: return emptyList()
        return improvFormationPositions(cfg, active, occurrence)
    }

    fun improvArpeggioPositions(): List<Pair<Int, Int>> =
        improvLinePositions(config).map { it.stringIndex to it.fret }

    fun focusImprovShape() {
        val occurrence = improvOccurrence(config) ?: return
        focusFretRange(occurrence.startFret, occurrence.endFret)
    }

    private fun drawImprovLine(canvas: Canvas) {
        val lines = currentImprovLines()
        if (lines.isEmpty()) return
        val active = config.improvActive ?: return

        improvLinePaint.color = ChordColorProvider.baseColor(active.rootPc)
        improvLinePaint.strokeWidth = dp(1.7f)
        improvLinePaint.alpha = 150

        for (positions in lines) {
            improvPath.reset()
            var first = true
            for (position in positions) {
                val x = if (position.fret == 0) {
                    gutterWidth * 0.5f
                } else {
                    cellCenterX(position.fret)
                }
                val y = stringYs.getOrNull(position.stringIndex) ?: continue
                if (first) {
                    improvPath.moveTo(x, y)
                    first = false
                } else {
                    improvPath.lineTo(x, y)
                }
            }
            if (!first) canvas.drawPath(improvPath, improvLinePaint)
        }
    }


    private fun drawChordRings(
        canvas: Canvas,
        x: Float,
        y: Float,
        baseRadius: Float,
        hits: List<ChordHit>
    ) {
        if (hits.isEmpty()) return

        val radius = baseRadius + dp(2.5f)
        chordRingPaint.strokeWidth = dp(1.35f)
        if (hits.size == 1) {
            chordRingPaint.color = hits[0].color
            chordRingPaint.alpha = 165
            canvas.drawCircle(x, y, radius, chordRingPaint)
            return
        }

        ringRect.set(x - radius, y - radius, x + radius, y + radius)
        val sweep = 360f / hits.size
        val gap = 12f
        for (index in hits.indices) {
            chordRingPaint.color = hits[index].color
            chordRingPaint.alpha = 150
            val start = -90f + index * sweep + gap / 2f
            canvas.drawArc(ringRect, start, sweep - gap, false, chordRingPaint)
        }
    }

    private fun drawDegreeBadge(
        canvas: Canvas,
        label: String,
        x: Float,
        noteY: Float,
        radius: Float,
        color: Int,
        textPaint: Paint
    ) {
        if (label.isBlank()) return
        val textWidth = textPaint.measureText(label)
        val textHeight = textPaint.descent() - textPaint.ascent()
        val padX = dp(3.5f)
        val padY = dp(1.2f)
        val width = textWidth + padX * 2f
        val height = textHeight + padY * 2f

        val angle = Math.PI / 4.0
        val distance = radius + dp(1.5f) + max(width, height) / 2f
        val centerX = x + (cos(angle) * distance).toFloat()
        val centerY = noteY - (sin(angle) * distance).toFloat()
        val rect = badgeRect
        rect.set(
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f
        )

        val margin = dp(2f)
        if (rect.top < margin) rect.offset(0f, margin - rect.top)
        if (rect.bottom > this.height - margin) {
            rect.offset(0f, (this.height - margin) - rect.bottom)
        }
        if (rect.left < margin) rect.offset(margin - rect.left, 0f)
        if (rect.right > this.width - margin) {
            rect.offset((this.width - margin) - rect.right, 0f)
        }

        canvas.drawRoundRect(rect, height / 2f, height / 2f, degreeBadgeFillPaint)
        degreeBadgeBorderPaint.color = color
        degreeBadgeBorderPaint.alpha = 235
        canvas.drawRoundRect(rect, height / 2f, height / 2f, degreeBadgeBorderPaint)

        textPaint.color = 0xFFF7F3FF.toInt()
        textPaint.alpha = 255
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, centerX, baseline, textPaint)
    }

    private fun drawDegreeBadges(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        hits: List<ChordHit>
    ) {
        for (hit in hits) {
            if (hit.role != 0) continue
            drawDegreeBadge(
                canvas,
                hit.degree.toString(),
                x,
                y,
                radius,
                ChordColorProvider.labelColor(hit.rootPc),
                degreePaint
            )
        }
    }

    private fun drawScaleDegreeBadge(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        cfg: FretboardConfig,
        degree: Int
    ) {
        if (degree <= 0 || !cfg.showDegrees) return
        val color = if (cfg.colorsEnabled) {
            NoteColorProvider.scaleDegreeColor(degree)
        } else {
            0xFFF5F2FF.toInt()
        }
        drawDegreeBadge(
            canvas,
            cfg.scale.degreeName(degree),
            x,
            y,
            radius,
            color,
            degreePaint
        )
    }

    private fun drawChordOnlyNote(
        canvas: Canvas,
        x: Float,
        y: Float,
        pitchClass: Int,
        cfg: FretboardConfig,
        hits: List<ChordHit>
    ) {
        val radius = noteRadius * 0.92f

        chordFillPaint.color = 0xFF17141F.toInt()
        chordFillPaint.alpha = 235
        canvas.drawCircle(x, y, radius, chordFillPaint)

        markerBorderPaint.color = hits.first().color
        markerBorderPaint.strokeWidth = dp(1.8f)
        markerBorderPaint.alpha = 235
        canvas.drawCircle(x, y, radius, markerBorderPaint)

        noteTextPaint.color = 0xFFF3EFFA.toInt()
        noteTextPaint.alpha = 255
        noteTextPaint.textSize = radius * 0.88f
        val baseline = y - (noteTextPaint.descent() + noteTextPaint.ascent()) / 2f
        canvas.drawText(Pitch.name(pitchClass, cfg.useFlats), x, baseline, noteTextPaint)

        drawChordRings(canvas, x, y, radius, hits)
        if (cfg.showDegrees) {
            drawDegreeBadges(canvas, x, y, radius, hits)
        }
    }

    private fun drawChordBarres(canvas: Canvas, voicing: ChordVoicing) {
        if (config.mode != ViewMode.CHORD) return
        val fret = voicing.barreFret ?: return
        if (fret <= 0) return

        val x = cellCenterX(fret)
        if (x < boardStartX - noteRadius || x > width + noteRadius) return

        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        if (voicing.barreAllStrings) {
            top = stringYs.firstOrNull() ?: return
            bottom = stringYs.lastOrNull() ?: return
        } else {
            for (position in voicing.positions) {
                if (position.fret != fret) continue
                val y = stringYs.getOrNull(position.stringIndex) ?: continue
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (top > bottom) return

        barrePaint.strokeWidth = noteRadius * 1.9f
        barrePaint.alpha = 230
        canvas.drawLine(x, top, x, bottom, barrePaint)
    }
    private fun drawNotes(
        canvas: Canvas,
        chordFormation: Set<VoicingKey>,
        model: FretboardRenderModel
    ) {
        val cfg = config
        val strings = cfg.tuning.strings
        val scaleSet = model.scalePitchClasses
        val chordSet = model.chordPitchClasses
        val selectedPattern = cfg.selectedPattern
        val selectedOccurrences = model.selectedOccurrences

        for (stringIndex in strings.indices) {
            val y = stringYs[stringIndex]
            for (fret in 1..FRET_COUNT) {
                val x = cellCenterX(fret)
                if (x < boardStartX - noteRadius || x > width + noteRadius) continue

                val pitchClass = Pitch.pitchClass(strings[stringIndex].openMidi + fret)
                when (cfg.mode) {
                    ViewMode.FREE -> drawNote(
                        canvas, x, y, pitchClass, cfg,
                        NoteColorProvider.NEUTRAL_FILL, showText = true, radiusScale = 1f
                    )

                    ViewMode.SCALE -> {
                        val degree = cfg.scale.degreeOf(cfg.rootPc, pitchClass)
                        val inScale = degree > 0 && pitchClass in scaleSet
                        val hits = model.hitsFor(pitchClass)

                        if (inScale) {
                            val insideSelectedPattern = selectedPattern == null ||
                                selectedOccurrences.any { fret in it }
                            if (insideSelectedPattern) {
                                val fill = if (cfg.colorsEnabled) {
                                    NoteColorProvider.scaleDegreeColor(degree)
                                } else {
                                    NoteColorProvider.NEUTRAL_FILL
                                }
                                drawNote(canvas, x, y, pitchClass, cfg, fill, showText = true, radiusScale = 1f)
                            } else {
                                drawDimmedNote(canvas, x, y, pitchClass, cfg)
                            }
                            if (hits.isNotEmpty()) {
                                drawChordRings(canvas, x, y, noteRadius, hits)
                            }
                            drawScaleDegreeBadge(canvas, x, y, noteRadius, cfg, degree)
                        } else if (hits.isNotEmpty()) {
                            drawChordOnlyNote(canvas, x, y, pitchClass, cfg, hits)
                        }
                    }

                    ViewMode.CHORD -> {
                        if (VoicingKey(stringIndex, fret) !in chordFormation) {
                            continue
                        }
                        val role = cfg.chord.roleOf(cfg.rootPc, pitchClass)
                        if (role != null && pitchClass in chordSet) {
                            val fill = if (cfg.colorsEnabled) {
                                NoteColorProvider.chordRoleColor(role)
                            } else {
                                NoteColorProvider.NEUTRAL_FILL
                            }
                            drawNote(canvas, x, y, pitchClass, cfg, fill, showText = true, radiusScale = 1f)
                        } else {
                            drawNote(
                                canvas, x, y, pitchClass, cfg,
                                NoteColorProvider.ATTENUATED_FILL, showText = false, radiusScale = 0.5f
                            )
                        }
                    }

                    ViewMode.IMPROV -> {
                        val active = cfg.improvActive
                        val next = cfg.improvNext
                        val activeContains = active?.contains(pitchClass) == true
                        val nextContains = next?.contains(pitchClass) == true

                        if (cfg.improvOverlayScale && cfg.improvShowScale) {
                            val degree = cfg.scale.degreeOf(cfg.rootPc, pitchClass)
                            if (degree > 0 && pitchClass in scaleSet) {
                                val fill = if (cfg.colorsEnabled) {
                                    NoteColorProvider.scaleDegreeColor(degree)
                                } else {
                                    NoteColorProvider.NEUTRAL_FILL
                                }
                                drawNote(
                                    canvas, x, y, pitchClass, cfg, fill,
                                    showText = true, radiusScale = 1f
                                )
                                val coveredByChord = cfg.improvShowChord && activeContains
                                if (!coveredByChord) {
                                    drawScaleDegreeBadge(canvas, x, y, noteRadius, cfg, degree)
                                }
                            }
                        }
                        if (cfg.improvShowChord) {
                            if (nextContains && !activeContains) {
                                drawImprovNote(
                                    canvas, x, y, pitchClass, next!!,
                                    dimmed = true, role = null
                                )
                            }
                            if (activeContains) {
                                val activeChord = active!!
                                drawImprovNote(
                                    canvas, x, y, pitchClass, activeChord,
                                    dimmed = false, role = activeChord.roleLabel(pitchClass)
                                )
                            }
                        }
                    }
                }

                val showImprovRoot = cfg.mode == ViewMode.IMPROV &&
                    cfg.improvOverlayScale && cfg.improvShowScale
                if (pitchClass == cfg.rootPc && (cfg.mode != ViewMode.IMPROV || showImprovRoot)) {
                    drawRootRing(canvas, x, y, noteRadius)
                }
            }
        }

        if (playedFret in 1..FRET_COUNT && playedString in strings.indices) {
            val x = cellCenterX(playedFret)
            val y = stringYs[playedString]
            glowPaint.color = 0x55FFD54F
            canvas.drawCircle(x, y, noteRadius + dp(6f), glowPaint)
            highlightPaint.alpha = 255
            canvas.drawCircle(x, y, noteRadius + dp(3f), highlightPaint)
        }
    }

    private fun drawNote(
        canvas: Canvas,
        x: Float,
        y: Float,
        pitchClass: Int,
        cfg: FretboardConfig,
        fill: Int,
        showText: Boolean,
        radiusScale: Float
    ) {
        val radius = noteRadius * radiusScale
        val attenuated = radiusScale < 1f

        markerPaint.color = fill
        markerPaint.alpha = if (attenuated) 90 else 255
        canvas.drawCircle(x, y, radius, markerPaint)

        markerBorderPaint.alpha = if (attenuated) 40 else 90
        canvas.drawCircle(x, y, radius, markerBorderPaint)

        if (!showText) return

        noteTextPaint.color = if (attenuated) {
            NoteColorProvider.NEUTRAL_TEXT_LIGHT
        } else {
            NoteColorProvider.textColorFor(fill)
        }
        noteTextPaint.alpha = if (attenuated) 140 else 255
        noteTextPaint.textSize = radius * 0.92f
        val baseline = y - (noteTextPaint.descent() + noteTextPaint.ascent()) / 2f
        canvas.drawText(Pitch.name(pitchClass, cfg.useFlats), x, baseline, noteTextPaint)
    }

    private fun drawRootRing(canvas: Canvas, x: Float, y: Float, radius: Float) {
        rootRingPaint.alpha = 235
        canvas.drawCircle(x, y, radius + dp(5f), rootRingPaint)
    }

    private fun drawImprovNote(
        canvas: Canvas,
        x: Float,
        y: Float,
        pitchClass: Int,
        chord: ImprovChord,
        dimmed: Boolean,
        role: String?
    ) {
        val radius = noteRadius * if (dimmed) 0.82f else 1f
        val color = ChordColorProvider.baseColor(chord.rootPc)

        markerPaint.color = color
        markerPaint.alpha = if (dimmed) 65 else 245
        canvas.drawCircle(x, y, radius, markerPaint)

        markerBorderPaint.color = color
        markerBorderPaint.strokeWidth = dp(1.6f)
        markerBorderPaint.alpha = if (dimmed) 45 else 190
        canvas.drawCircle(x, y, radius, markerBorderPaint)

        noteTextPaint.color = if (dimmed) {
            NoteColorProvider.NEUTRAL_TEXT_LIGHT
        } else {
            NoteColorProvider.textColorFor(color)
        }
        noteTextPaint.alpha = if (dimmed) 115 else 255
        noteTextPaint.textSize = radius * 0.9f
        val baseline = y - (noteTextPaint.descent() + noteTextPaint.ascent()) / 2f
        canvas.drawText(Pitch.name(pitchClass, config.useFlats), x, baseline, noteTextPaint)

        if (!dimmed && role != null && config.showDegrees) {
            drawDegreeBadge(
                canvas,
                role,
                x,
                y,
                radius,
                ChordColorProvider.labelColor(chord.rootPc),
                improvRolePaint
            )
        }
    }

    private fun drawDimmedNote(
        canvas: Canvas,
        x: Float,
        y: Float,
        pitchClass: Int,
        cfg: FretboardConfig
    ) {
        val radius = noteRadius * 0.8f

        markerPaint.color = NoteColorProvider.ATTENUATED_FILL
        markerPaint.alpha = 85
        canvas.drawCircle(x, y, radius, markerPaint)

        markerBorderPaint.alpha = 30
        canvas.drawCircle(x, y, radius, markerBorderPaint)

        noteTextPaint.color = 0xFFD8D2E2.toInt()
        noteTextPaint.alpha = 170
        noteTextPaint.textSize = radius * 0.86f
        val baseline = y - (noteTextPaint.descent() + noteTextPaint.ascent()) / 2f
        canvas.drawText(Pitch.name(pitchClass, cfg.useFlats), x, baseline, noteTextPaint)
    }

    private fun drawGutter(
        canvas: Canvas,
        model: FretboardRenderModel,
        chordVoicing: ChordVoicing
    ) {
        val strings = config.tuning.strings
        if (stringYs.isEmpty()) return
        val cx = gutterWidth * 0.5f
        val chordFormation = chordVoicing.positions
        val selected = if (config.mode == ViewMode.CHORD) config.chordPattern else config.selectedPattern
        val patternIncludesOpen = selected == null || CagedPatterns.includesOpenString(selected)

        for (stringIndex in strings.indices) {
            if (stringIndex >= stringYs.size) continue
            val y = stringYs[stringIndex]
            val played = playedFret == 0 && playedString == stringIndex
            val openPitchClass = Pitch.pitchClass(strings[stringIndex].openMidi)
            val activeOpen = config.mode == ViewMode.IMPROV &&
                config.improvShowChord &&
                config.improvActive?.contains(openPitchClass) == true
            val nextOpen = config.mode == ViewMode.IMPROV &&
                config.improvShowChord &&
                !activeOpen && config.improvNext?.contains(openPitchClass) == true

            val degree = config.scale.degreeOf(config.rootPc, openPitchClass)
            val improvScaleOpen = config.mode == ViewMode.IMPROV &&
                config.improvOverlayScale && config.improvShowScale && degree > 0
            val showScaleTone = config.mode == ViewMode.SCALE && degree > 0 && patternIncludesOpen
            val hasVoicedString = chordFormation.any { it.stringIndex == stringIndex }
            val openVoicedString = chordFormation.any {
                it.stringIndex == stringIndex && it.fret == 0
            }
            val chordVoicingRole = if (openVoicedString) {
                config.chord.roleOf(config.rootPc, openPitchClass)
            } else {
                null
            }

            val fill = when {
                played -> 0xFF7C5CFF.toInt()
                activeOpen -> ChordColorProvider.baseColor(config.improvActive?.rootPc ?: 0)
                nextOpen -> ChordColorProvider.baseColor(config.improvNext?.rootPc ?: 0)
                chordVoicingRole != null && config.colorsEnabled ->
                    NoteColorProvider.chordRoleColor(chordVoicingRole)
                chordVoicingRole != null -> NoteColorProvider.NEUTRAL_FILL
                improvScaleOpen && config.colorsEnabled -> NoteColorProvider.scaleDegreeColor(degree)
                improvScaleOpen -> NoteColorProvider.NEUTRAL_FILL
                showScaleTone && config.colorsEnabled -> NoteColorProvider.scaleDegreeColor(degree)
                showScaleTone -> NoteColorProvider.NEUTRAL_FILL
                else -> 0xFF1B1825.toInt()
            }
            openPaint.color = fill
            openPaint.alpha = if (nextOpen) 70 else 255
            canvas.drawCircle(cx, y, noteRadius * 0.9f, openPaint)

            openTextPaint.color = when {
                played -> 0xFFFFFFFF.toInt()
                activeOpen -> NoteColorProvider.textColorFor(fill)
                nextOpen -> NoteColorProvider.NEUTRAL_TEXT_LIGHT
                chordVoicingRole != null -> NoteColorProvider.textColorFor(fill)
                improvScaleOpen -> NoteColorProvider.textColorFor(fill)
                showScaleTone -> NoteColorProvider.textColorFor(fill)
                else -> 0xFFB9B3C9.toInt()
            }
            openTextPaint.alpha = if (nextOpen) 115 else 255
            openTextPaint.textSize = noteRadius * 0.82f
            val baseline = y - (openTextPaint.descent() + openTextPaint.ascent()) / 2f
            canvas.drawText(strings[stringIndex].name(config.useFlats), cx, baseline, openTextPaint)

            if (activeOpen && config.showDegrees) {
                val activeChord = config.improvActive
                val role = activeChord?.roleLabel(openPitchClass)
                if (activeChord != null && role != null) {
                    drawDegreeBadge(
                        canvas,
                        role,
                        cx,
                        y,
                        noteRadius * 0.9f,
                        ChordColorProvider.labelColor(activeChord.rootPc),
                        improvRolePaint
                    )
                }
            } else if (improvScaleOpen && config.showDegrees) {
                drawScaleDegreeBadge(canvas, cx, y, noteRadius * 0.9f, config, degree)
            }

            if (config.mode == ViewMode.CHORD && chordFormation.isNotEmpty()) {
                val marker = when {
                    openVoicedString -> "O"
                    !hasVoicedString -> "X"
                    else -> null
                }
                if (marker != null) {
                    indicatorPaint.color =
                        if (openVoicedString) 0xFF3DDC97.toInt() else 0xFF8E88A3.toInt()
                    indicatorPaint.alpha = 255
                    val indicatorBaseline =
                        y - (indicatorPaint.descent() + indicatorPaint.ascent()) / 2f
                    canvas.drawText(marker, dp(9f), indicatorBaseline, indicatorPaint)
                }
            }

            val openHits = model.hitsFor(openPitchClass)
            if (openHits.isNotEmpty()) {
                drawChordRings(canvas, cx, y, noteRadius * 0.9f, openHits)
            }
            if (config.mode == ViewMode.SCALE && degree > 0) {
                drawScaleDegreeBadge(canvas, cx, y, noteRadius * 0.9f, config, degree)
            }
            if (openPitchClass == config.rootPc &&
                config.mode != ViewMode.CHORD &&
                (config.mode != ViewMode.IMPROV || improvScaleOpen)
            ) {
                drawRootRing(canvas, cx, y, noteRadius * 0.9f)
            }
        }

        if (playedString in stringYs.indices && playedFret == 0) {
            glowPaint.color = 0x55FFD54F
            canvas.drawCircle(cx, stringYs[playedString], noteRadius + dp(4f), glowPaint)
            highlightPaint.alpha = 255
            canvas.drawCircle(cx, stringYs[playedString], noteRadius + dp(1f), highlightPaint)
        }
    }

    private fun nearestString(y: Float): Int? {
        if (stringYs.isEmpty()) return null
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in stringYs.indices) {
            val distance = abs(y - stringYs[i])
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return if (best >= 0) best else null
    }

    private fun hitAt(x: Float, y: Float): Pair<Int, Int>? {
        val stringIndex = nearestString(y) ?: return null
        if (x < boardStartX) return stringIndex to 0

        val contentX = x - boardStartX + scrollX
        val fret = (contentX / fretWidth).toInt() + 1
        if (fret < 1) return stringIndex to 0
        if (fret > FRET_COUNT) return null
        return stringIndex to fret
    }

    private fun playNote(stringIndex: Int, fret: Int) {
        val string = config.tuning.strings.getOrNull(stringIndex) ?: return
        playedString = stringIndex
        playedFret = fret
        onNotePlayed?.invoke(stringIndex, string.noteAt(fret))
        invalidate()
    }

    fun highlightNote(stringIndex: Int, fret: Int, reveal: Boolean = true) {
        playedString = stringIndex
        playedFret = fret
        if (reveal) {
            ensureGeometry()
            if (width > 0) {
                val viewport = width - boardStartX
                val target = (fret - 0.5f) * fretWidth - viewport / 2f
                scrollX = target.coerceIn(0f, maxScroll)
            }
        }
        invalidate()
    }

    fun focusFretRange(startFret: Int, endFret: Int) {
        ensureGeometry()
        if (width <= 0 || fretWidth <= 0f) return

        val start = startFret.coerceAtLeast(0)
        val end = endFret.coerceAtLeast(start)
        val rangeLeft = if (start == 0) 0f else (start - 1) * fretWidth
        val rangeRight = end * fretWidth

        val screenLeft = boardStartX + rangeLeft - scrollX
        val screenRight = boardStartX + rangeRight - scrollX
        if (screenLeft >= boardStartX && screenRight <= width.toFloat()) return

        val viewport = width - boardStartX
        val rangeWidth = rangeRight - rangeLeft
        val target = if (rangeWidth <= viewport) {
            rangeLeft - (viewport - rangeWidth) / 2f
        } else {
            rangeLeft
        }
        scrollX = target.coerceIn(0f, maxScroll)
        invalidate()
    }

    fun clearHighlight() {
        playedString = -1
        playedFret = -1
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        ensureGeometry()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                downX = event.x
                downY = event.y
                scrollStart = scrollX
                dragging = false
                velocityTracker.clear()
                velocityTracker.addMovement(event)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragging = true
                }
                if (dragging) {
                    scrollX = (scrollStart - dx).coerceIn(0f, maxScroll)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker.addMovement(event)
                if (!dragging) {
                    val tappedPattern = hitPatternSegment(event.x, event.y)
                    if (tappedPattern != null) {
                        val currentPattern = if (config.mode == ViewMode.CHORD) config.chordPattern else config.selectedPattern
                        val newSelection = if (currentPattern == tappedPattern) null else tappedPattern
                        onPatternSelected?.invoke(newSelection)
                        performClick()
                    } else {
                        hitAt(event.x, event.y)?.let { (stringIndex, fret) ->
                            playNote(stringIndex, fret)
                            performClick()
                        }
                    }
                } else {
                    velocityTracker.computeCurrentVelocity(1000)
                    val velocityX = velocityTracker.xVelocity
                    if (abs(velocityX) > dp(80f)) {
                        scroller.fling(
                            scrollX.toInt(), 0, (-velocityX).toInt(), 0,
                            0, maxScroll.toInt(), 0, 0
                        )
                        invalidate()
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = scroller.currX.toFloat().coerceIn(0f, maxScroll)
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        velocityTracker.recycle()
    }
}






