package apk.mavier.dguitar.presentation.main

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import apk.mavier.dguitar.R
import apk.mavier.dguitar.databinding.DialogHarmonicCircleBinding
import apk.mavier.dguitar.domain.model.ChordQuality
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.HarmonicChord
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.usecase.BuildHarmonicCircleUseCase
import apk.mavier.dguitar.presentation.theme.ChordColorProvider

class HarmonicCircleDialogController(
    private val activity: AppCompatActivity,
    private val harmonicCircleUseCase: BuildHarmonicCircleUseCase,
    private val configProvider: () -> FretboardConfig,
    private val onConfigChange: (FretboardConfig) -> Unit
) {

    fun show() {
        val config = configProvider()
        if (!harmonicCircleUseCase.supports(config.scale)) return

        val dialogBinding = DialogHarmonicCircleBinding.inflate(activity.layoutInflater)
        MaterialAlertDialogBuilder(activity)
            .setView(dialogBinding.root)
            .setNegativeButton("Cerrar", null)
            .show()

        dialogBinding.seventhsButton.text = "Séptimas"
        dialogBinding.seventhsButton.setOnClickListener {
            val current = configProvider()
            onConfigChange(
                current.copy(
                    circleSevenths = !current.circleSevenths,
                    circleOverrides = emptyMap()
                )
            )
            render(dialogBinding)
        }
        dialogBinding.toggleAllButton.setOnClickListener {
            applyDegrees(dialogBinding, (1..7).toSet())
        }
        dialogBinding.toggleNoneButton.setOnClickListener {
            applyDegrees(dialogBinding, emptySet())
        }

        render(dialogBinding)
    }

    private fun applyDegrees(dialogBinding: DialogHarmonicCircleBinding, degrees: Set<Int>) {
        onConfigChange(configProvider().copy(circleDegrees = degrees))
        render(dialogBinding)
    }

    private fun render(dialogBinding: DialogHarmonicCircleBinding) {
        val config = configProvider()
        dialogBinding.circleSubtitle.text = buildString {
            append(Pitch.name(config.rootPc, config.useFlats))
            append(" ")
            append(config.scale.name)
            append(" · ")
            append(if (config.circleSevenths) "séptimas" else "tríadas")
        }
        dialogBinding.seventhsButton.isSelected = config.circleSevenths
        dialogBinding.toggleAllButton.isSelected = config.circleDegrees.size == 7
        dialogBinding.toggleNoneButton.isSelected = config.circleDegrees.isEmpty()

        val previousScroll = dialogBinding.degreeScroll.scrollY
        val allChords = harmonicCircleUseCase.chords(
            config.scale,
            config.rootPc,
            config.circleSevenths
        )
        dialogBinding.degreeList.removeAllViews()
        for (chord in allChords) {
            val quality = config.circleOverrides[chord.degree] ?: chord.quality
            val row = activity.layoutInflater.inflate(
                R.layout.item_harmonic_chord,
                dialogBinding.degreeList,
                false
            ) as TextView
            val selected = config.circleDegrees.contains(chord.degree)
            row.isSelected = selected
            row.text = buildDegreeText(config, chord, quality)
            row.contentDescription = buildString {
                append("Grado ")
                append(chord.degree)
                append(", ")
                append(Pitch.name(chord.rootPc, config.useFlats))
                append(" ")
                append(quality.name)
                append(if (selected) ", activado" else ", desactivado")
            }
            row.setOnClickListener {
                val degrees = if (selected) {
                    config.circleDegrees - chord.degree
                } else {
                    config.circleDegrees + chord.degree
                }
                applyDegrees(dialogBinding, degrees)
            }
            row.setOnLongClickListener {
                showDegreeQualityMenu(row, chord, dialogBinding)
                true
            }
            dialogBinding.degreeList.addView(row)
        }
        dialogBinding.degreeScroll.post {
            dialogBinding.degreeScroll.scrollTo(0, previousScroll)
        }
    }

    private fun buildDegreeText(
        config: FretboardConfig,
        chord: HarmonicChord,
        quality: ChordQuality
    ): CharSequence {
        val builder = SpannableStringBuilder()
        val degreeStart = builder.length
        builder.append(chord.degree.toString())
        builder.setSpan(
            StyleSpan(Typeface.BOLD),
            degreeStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.append("   ")
        val chordStart = builder.length
        builder.append(Pitch.name(chord.rootPc, config.useFlats))
        builder.append(quality.symbol)
        builder.setSpan(
            ForegroundColorSpan(ChordColorProvider.labelColor(chord.rootPc)),
            chordStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.append("   ")
        val qualityStart = builder.length
        builder.append(quality.name)
        builder.setSpan(
            ForegroundColorSpan(activity.getColor(R.color.text_secondary)),
            qualityStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val notes = quality.pitchClasses(chord.rootPc)
            .sortedBy { ((it - chord.rootPc) % 12 + 12) % 12 }
            .joinToString(" ") { Pitch.name(it, config.useFlats) }
        builder.append("   ·   ")
        val notesStart = builder.length
        builder.append(notes)
        builder.setSpan(
            ForegroundColorSpan(ChordColorProvider.labelColor(chord.rootPc)),
            notesStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            RelativeSizeSpan(0.88f),
            notesStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    private fun showDegreeQualityMenu(
        anchor: View,
        chord: HarmonicChord,
        dialogBinding: DialogHarmonicCircleBinding
    ) {
        val config = configProvider()
        val diatonic = harmonicCircleUseCase.chords(
            config.scale,
            config.rootPc,
            config.circleSevenths
        ).firstOrNull { it.degree == chord.degree }?.quality ?: chord.quality
        val major = if (config.circleSevenths) Chords.MAJOR_7 else Chords.MAJOR
        val minorQuality = if (config.circleSevenths) Chords.MINOR_7 else Chords.MINOR

        val popup = PopupMenu(activity, anchor)
        popup.menu.add(Menu.NONE, 0, 0, "Diatónica (${diatonic.name})")
        popup.menu.add(Menu.NONE, 1, 1, if (config.circleSevenths) "Mayor 7" else "Mayor")
        popup.menu.add(Menu.NONE, 2, 2, if (config.circleSevenths) "Menor 7" else "Menor")
        popup.setOnMenuItemClickListener { item ->
            val current = configProvider()
            val overrides = current.circleOverrides.toMutableMap()
            when (item.itemId) {
                0 -> overrides.remove(chord.degree)
                1 -> overrides[chord.degree] = major
                2 -> overrides[chord.degree] = minorQuality
            }
            onConfigChange(
                current.copy(
                    circleDegrees = current.circleDegrees + chord.degree,
                    circleOverrides = overrides
                )
            )
            render(dialogBinding)
            true
        }
        popup.show()
    }
}
