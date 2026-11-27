package apk.mavier.dguitar.presentation.common

import android.app.Activity
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import apk.mavier.dguitar.R
import apk.mavier.dguitar.databinding.ViewChordPickerBinding
import apk.mavier.dguitar.domain.model.ChordQuality
import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.SongChord
import apk.mavier.dguitar.presentation.theme.ChordColorProvider

/**
 * Reusable chord picker panel: root grid, quality grid, optional bass and a
 * live preview. It knows nothing about dialogs, so the songbook, the chord
 * module and improvisation can embed it in different containers.
 */
class ChordPickerPanel(
    private val activity: Activity,
    private val binding: ViewChordPickerBinding,
    private val useFlats: Boolean,
    private val allowBass: Boolean,
    initial: SongChord,
    private val onPreview: ((SongChord) -> Unit)? = null,
    private val onChanged: ((SongChord) -> Unit)? = null
) {

    private var rootPc = initial.rootPc.coerceIn(0, 11)
    private var quality = initial.quality
    private var bassPc = initial.bassPc
    private var showAllQualities = initial.quality !in COMMON_QUALITIES
    private var showBass = allowBass && initial.bassPc != null

    private val rootChips = mutableListOf<Pair<Int, TextView>>()
    private val qualityChips = mutableListOf<Pair<ChordQuality, TextView>>()
    private val bassChips = mutableListOf<Pair<Int?, TextView>>()

    var selection: SongChord = SongChord(rootPc, quality, bassPc)
        private set

    init {
        renderRootGrid()
        renderQualityGrid()
        if (showBass) renderBassGrid()
        updateBassVisibility()
        binding.previewButton.visibility =
            if (onPreview != null) View.VISIBLE else View.GONE
        binding.previewButton.setOnClickListener {
            onPreview?.invoke(selection)
        }
        binding.moreQualitiesButton.setOnClickListener {
            val next = !showAllQualities
            showAllQualities =
                if (!next && quality !in COMMON_QUALITIES) true else next
            renderQualityGrid()
            updateSelections()
        }
        binding.bassToggleButton.setOnClickListener {
            showBass = !showBass
            if (showBass && bassChips.isEmpty()) renderBassGrid()
            updateBassVisibility()
            updateSelections()
        }
        updatePreview()
    }

    private fun createChip(label: String, subtitle: String? = null): TextView =
        TextView(activity).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = if (subtitle == null) 1 else 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(activity.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_picker_chip)
            isClickable = true
            isFocusable = true
            if (subtitle == null) {
                text = label
                minHeight = dp(48)
                setPadding(dp(4), 0, dp(4), 0)
                layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                    bottomMargin = dp(4)
                }
            } else {
                text = SpannableStringBuilder(label).apply {
                    append("\n")
                    val subtitleStart = length
                    append(subtitle)
                    setSpan(
                        RelativeSizeSpan(0.62f),
                        subtitleStart,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    setSpan(
                        ForegroundColorSpan(
                            activity.getColor(R.color.text_secondary)
                        ),
                        subtitleStart,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                minHeight = dp(56)
                setPadding(dp(2), dp(3), dp(2), dp(3))
                layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                    bottomMargin = dp(4)
                }
            }
        }

    private fun addRow(container: LinearLayout, chips: List<TextView>) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        chips.forEach { row.addView(it) }
        container.addView(row)
    }

    private fun qualityLabel(chordQuality: ChordQuality): String = when {
        chordQuality.symbol.isEmpty() ->
            activity.getString(R.string.song_chord_major_short)
        chordQuality.symbol == "m" ->
            activity.getString(R.string.song_chord_minor_short)
        else -> chordQuality.symbol
    }

    private fun updateSelections() {
        rootChips.forEach { (pc, chip) -> chip.isSelected = pc == rootPc }
        qualityChips.forEach { (value, chip) -> chip.isSelected = value == quality }
        bassChips.forEach { (pc, chip) -> chip.isSelected = pc == bassPc }
    }

    private fun updatePreview() {
        selection = SongChord(rootPc, quality, bassPc)
        binding.previewName.text = selection.label(useFlats)
        binding.previewName.setTextColor(
            ChordColorProvider.labelColor(selection.rootPc)
        )
        binding.previewNotes.text =
            "${selection.reading(useFlats)} · ${selection.notesText(useFlats)}"
        updateSelections()
        onChanged?.invoke(selection)
    }

    private fun renderRootGrid() {
        binding.rootGrid.removeAllViews()
        rootChips.clear()
        (0..11).chunked(ROOT_COLUMNS).forEach { rowItems ->
            val rowChips = rowItems.map { pc ->
                val name = Pitch.name(pc, useFlats)
                val solfege = Pitch.solfegeName(pc, useFlats)
                val chip = createChip(name, solfege).apply {
                    setTextColor(ChordColorProvider.labelColor(pc))
                    contentDescription = activity.getString(
                        R.string.song_chord_root_description,
                        "$name ($solfege)"
                    )
                    setOnClickListener {
                        rootPc = pc
                        updatePreview()
                    }
                }
                rootChips.add(pc to chip)
                chip
            }
            addRow(binding.rootGrid, rowChips)
        }
    }

    private fun renderQualityGrid() {
        binding.qualityGrid.removeAllViews()
        qualityChips.clear()
        val qualities = if (showAllQualities) Chords.all else COMMON_QUALITIES
        qualities.chunked(QUALITY_COLUMNS).forEach { rowItems ->
            val rowChips = rowItems.map { value ->
                val chip = createChip(qualityLabel(value)).apply {
                    contentDescription = activity.getString(
                        R.string.song_chord_quality_description,
                        value.name
                    )
                    setOnClickListener {
                        quality = value
                        updatePreview()
                    }
                }
                qualityChips.add(value to chip)
                chip
            }
            addRow(binding.qualityGrid, rowChips)
        }
        binding.moreQualitiesButton.text = activity.getString(
            if (showAllQualities) {
                R.string.song_chord_fewer_types
            } else {
                R.string.song_chord_more_types
            }
        )
        binding.moreQualitiesButton.isSelected = showAllQualities
    }

    private fun renderBassGrid() {
        binding.bassGrid.removeAllViews()
        bassChips.clear()
        val options = listOf<Int?>(null) + (0..11)
        options.chunked(BASS_COLUMNS).forEach { rowItems ->
            val rowChips = rowItems.map { pc ->
                val label = pc?.let { Pitch.name(it, useFlats) }
                    ?: activity.getString(R.string.song_chord_no_bass)
                val subtitle = pc?.let { Pitch.solfegeName(it, useFlats) }.orEmpty()
                val chip = createChip(label, subtitle).apply {
                    contentDescription = pc?.let {
                        activity.getString(
                            R.string.song_chord_bass_description,
                            "$label (${Pitch.solfegeName(it, useFlats)})"
                        )
                    } ?: label
                    setOnClickListener {
                        bassPc = pc
                        updatePreview()
                    }
                }
                bassChips.add(pc to chip)
                chip
            }
            addRow(binding.bassGrid, rowChips)
        }
    }

    private fun updateBassVisibility() {
        binding.bassGroup.visibility =
            if (showBass) View.VISIBLE else View.GONE
        binding.bassToggleButton.visibility =
            if (allowBass) View.VISIBLE else View.GONE
        binding.bassToggleButton.text = activity.getString(
            if (showBass) {
                R.string.song_chord_bass_hide
            } else {
                R.string.song_chord_bass_optional
            }
        )
        binding.bassToggleButton.isSelected = showBass
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        val COMMON_QUALITIES = listOf(
            Chords.MAJOR,
            Chords.MINOR,
            Chords.DOMINANT_7,
            Chords.MAJOR_7,
            Chords.MINOR_7,
            Chords.POWER,
            Chords.SUS_2,
            Chords.SUS_4,
            Chords.DIMINISHED,
            Chords.AUGMENTED
        )
        const val ROOT_COLUMNS = 4
        const val QUALITY_COLUMNS = 5
        const val BASS_COLUMNS = 7
    }
}
