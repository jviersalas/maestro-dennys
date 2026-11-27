package apk.mavier.dguitar.presentation.common

import android.app.Activity
import apk.mavier.dguitar.R
import apk.mavier.dguitar.databinding.DialogChordPickerBinding
import apk.mavier.dguitar.domain.model.ChordQuality
import apk.mavier.dguitar.domain.model.SongChord
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SongChordPickerDialog(
    private val activity: Activity,
    private val useFlatsProvider: () -> Boolean,
    private val onPreview: ((SongChord) -> Unit)? = null,
    private val allowBass: Boolean = true
) {

    fun show(
        title: String,
        initialRoot: Int,
        initialQuality: ChordQuality,
        initialBassPc: Int?,
        positive: String,
        onPicked: (SongChord) -> Unit
    ) {
        val binding = DialogChordPickerBinding.inflate(activity.layoutInflater)
        val panel = ChordPickerPanel(
            activity = activity,
            binding = binding.chordPicker,
            useFlats = useFlatsProvider(),
            allowBass = allowBass,
            initial = SongChord(initialRoot, initialQuality, initialBassPc),
            onPreview = onPreview
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(positive) { _, _ ->
                onPicked(panel.selection)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
