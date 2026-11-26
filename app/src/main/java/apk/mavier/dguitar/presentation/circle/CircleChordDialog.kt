package apk.mavier.dguitar.presentation.circle

import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import apk.mavier.dguitar.R
import apk.mavier.dguitar.databinding.DialogCircleChordBinding
import apk.mavier.dguitar.domain.model.ChordQuality
import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.Tuning
import apk.mavier.dguitar.presentation.theme.ChordColorProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CircleChordDialog(
    private val activity: AppCompatActivity,
    private val onPlay: (Int, ChordQuality) -> Unit
) {

    fun show(
        chordName: String,
        roman: String,
        rootPc: Int,
        quality: ChordQuality,
        tuning: Tuning,
        useFlats: Boolean
    ) {
        val binding = DialogCircleChordBinding.inflate(activity.layoutInflater)
        val qualityName = ChordTheory.qualityName(quality)
        binding.chordHeader.text = chordName
        binding.chordHeader.setTextColor(ChordColorProvider.baseColor(rootPc))
        binding.chordSubtitle.text = activity.getString(
            R.string.circle_chord_modal_subtitle,
            roman,
            qualityName
        )
        binding.chordDiagram.setData(
            ChordTheory.diagram(rootPc, quality, tuning, useFlats)
        )

        binding.chordNotes.removeAllViews()
        for (tone in ChordTheory.tones(quality)) {
            val solfege = ChordTheory.noteSolfege(rootPc, quality, useFlats, tone.semitone)
            val anglo = ChordTheory.noteName(rootPc, quality, useFlats, tone.semitone)
            val label = activity.getString(R.string.circle_chord_note_chip, solfege, anglo)
            val chip = activity.layoutInflater.inflate(
                R.layout.item_circle_note,
                binding.chordNotes,
                false
            ) as TextView
            chip.text = label
            chip.contentDescription = label
            binding.chordNotes.addView(chip)
        }

        binding.chordFormation.text = ChordTheory.formationText(rootPc, quality, useFlats)
        binding.playChordButton.setOnClickListener { onPlay(rootPc, quality) }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.show()
        val width = (activity.resources.displayMetrics.widthPixels * 0.9f).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }
}
