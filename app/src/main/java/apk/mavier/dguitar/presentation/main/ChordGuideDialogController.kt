package apk.mavier.dguitar.presentation.main

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import apk.mavier.dguitar.R
import apk.mavier.dguitar.databinding.DialogChordGuideBinding
import apk.mavier.dguitar.domain.model.ChordQuality
import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.Pitch

class ChordGuideDialogController(
    private val activity: AppCompatActivity,
    private val configProvider: () -> FretboardConfig
) {

    fun show() {
        val config = configProvider()
        show(config.rootPc, config.chord)
    }

    fun show(rootPc: Int, quality: ChordQuality) {
        val config = configProvider()
        val binding = DialogChordGuideBinding.inflate(activity.layoutInflater)

        binding.chordGuideChord.text = Pitch.name(rootPc, config.useFlats) + quality.symbol
        binding.chordGuideReading.text =
            ChordTheory.displayName(rootPc, quality, config.useFlats)
        binding.guideIntro.text = activity.getString(R.string.chord_guide_intro)
        binding.formationHeader.text = activity.getString(R.string.how_formed)
        binding.formationText.text = ChordTheory.formationText(rootPc, quality, config.useFlats)
        binding.notesHeader.text = activity.getString(R.string.chord_notes_section)
        binding.notesText.text = ChordTheory.notesText(rootPc, quality, config.useFlats)
        binding.namingHeader.text = activity.getString(R.string.how_named)
        binding.namingText.text = ChordTheory.namingText(rootPc, quality, config.useFlats)
        binding.guideHint.text = activity.getString(R.string.chord_guide_hint)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.chord_guide_title)
            .setView(binding.root)
            .setPositiveButton(R.string.close, null)
            .show()
    }
}
