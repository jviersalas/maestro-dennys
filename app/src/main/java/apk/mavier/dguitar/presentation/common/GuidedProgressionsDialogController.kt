package apk.mavier.dguitar.presentation.common

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import apk.mavier.dguitar.R
import apk.mavier.dguitar.databinding.DialogGuidedProgressionsBinding
import apk.mavier.dguitar.databinding.ItemGuidedProgressionBinding
import apk.mavier.dguitar.domain.model.GuidedProgression
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.usecase.BuildGuidedProgressionUseCase
import apk.mavier.dguitar.presentation.theme.ChordColorProvider

class GuidedProgressionsDialogController(
    private val activity: AppCompatActivity,
    private val useCase: BuildGuidedProgressionUseCase,
    private val keyProvider: () -> Int,
    private val useFlatsProvider: () -> Boolean,
    private val onPractice: (GuidedProgression, Int) -> Unit
) {

    fun show() {
        val dialogBinding = DialogGuidedProgressionsBinding.inflate(activity.layoutInflater)
        var keyRootPc = normalize(keyProvider())
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.close, null)
            .create()

        val chips = ArrayList<TextView>(12)
        for (pitchClass in 0..11) {
            val chip = createKeyChip()
            chip.setOnClickListener {
                keyRootPc = pitchClass
                render(dialogBinding, keyRootPc, chips, dialog)
            }
            chips.add(chip)
            dialogBinding.guidedKeyRow.addView(chip)
        }

        render(dialogBinding, keyRootPc, chips, dialog)
        dialog.show()
    }

    private fun render(
        dialogBinding: DialogGuidedProgressionsBinding,
        keyRootPc: Int,
        chips: List<TextView>,
        dialog: AlertDialog
    ) {
        val useFlats = useFlatsProvider()
        val progressions = useCase.all()
        dialogBinding.guidedSubtitle.text = activity.getString(
            R.string.guided_subtitle_value,
            Pitch.name(keyRootPc, useFlats),
            progressions.size
        )

        chips.forEachIndexed { pitchClass, chip ->
            val label = Pitch.name(pitchClass, useFlats)
            chip.text = label
            chip.isSelected = pitchClass == keyRootPc
            chip.contentDescription = activity.getString(
                R.string.guided_key_description,
                label
            )
        }

        dialogBinding.guidedList.removeAllViews()
        for (progression in progressions) {
            val row = ItemGuidedProgressionBinding.inflate(
                activity.layoutInflater,
                dialogBinding.guidedList,
                false
            )
            row.guidedName.text = progression.name
            val barsLabel = activity.getString(R.string.bars_count, progression.totalBars)
            row.guidedDegrees.text = "${progression.degrees} · $barsLabel"
            row.guidedChords.text = chordsText(progression, keyRootPc, useFlats)
            row.guidedScale.text = activity.getString(
                R.string.suggested_scale,
                progression.scaleLabel(keyRootPc, useFlats)
            )
            row.practiceButton.contentDescription = activity.getString(
                R.string.guided_practice_description,
                progression.name,
                Pitch.name(keyRootPc, useFlats)
            )
            row.practiceButton.setOnClickListener {
                onPractice(progression, keyRootPc)
                dialog.dismiss()
            }
            dialogBinding.guidedList.addView(row.root)
        }
    }

    private fun chordsText(
        progression: GuidedProgression,
        keyRootPc: Int,
        useFlats: Boolean
    ): CharSequence {
        val builder = SpannableStringBuilder()
        progression.chords.forEachIndexed { index, chord ->
            if (index > 0) builder.append("  ")
            val rootPc = normalize(keyRootPc + chord.offset)
            val start = builder.length
            builder.append(Pitch.name(rootPc, useFlats))
            builder.append(chord.quality.symbol)
            if (chord.bars > 1) {
                builder.append("×")
                builder.append(chord.bars.toString())
            }
            builder.setSpan(
                ForegroundColorSpan(ChordColorProvider.labelColor(rootPc)),
                start,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return builder
    }

    private fun createKeyChip(): TextView = TextView(activity).apply {
        setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        minWidth = dp(48)
        minHeight = dp(48)
        background = AppCompatResources.getDrawable(activity, R.drawable.bg_chip)
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(6) }
    }

    private fun normalize(value: Int): Int = ((value % 12) + 12) % 12

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
