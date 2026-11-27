package apk.mavier.dguitar.presentation.common

import android.app.Activity
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import apk.mavier.dguitar.R
import apk.mavier.dguitar.databinding.DialogSongPartBinding
import apk.mavier.dguitar.domain.model.SongPartType
import apk.mavier.dguitar.domain.model.Songbook
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SongPartDialog(private val activity: Activity) {

    fun show(
        title: String,
        initialName: String,
        initialType: SongPartType,
        initialLyrics: String,
        initialRepeatCount: Int,
        positive: String,
        onSaved: (name: String, type: SongPartType, lyrics: String, repeatCount: Int) -> Unit
    ) {
        val binding = DialogSongPartBinding.inflate(activity.layoutInflater)
        val types = SongPartType.values()
        binding.partNameInput.setText(initialName)
        binding.partNameInput.setSelection(binding.partNameInput.text.length)
        binding.partLyricsInput.setText(initialLyrics)
        binding.partRepeatInput.setText(
            Songbook.normalizeRepeatCount(initialRepeatCount).toString()
        )
        binding.partTypeSpinner.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item,
            types.map { it.label }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.partTypeSpinner.setSelection(
            types.indexOf(initialType).coerceAtLeast(0)
        )

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(positive, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.partNameInput.text.toString().trim()
                if (name.isEmpty()) {
                    binding.partNameInput.error =
                        activity.getString(R.string.song_part_name_required)
                    return@setOnClickListener
                }
                val type = types.getOrElse(
                    binding.partTypeSpinner.selectedItemPosition
                ) { SongPartType.VERSO }
                val repeatCount = Songbook.normalizeRepeatCount(
                    binding.partRepeatInput.text.toString().toIntOrNull() ?: 1
                )
                dialog.dismiss()
                onSaved(
                    name,
                    type,
                    binding.partLyricsInput.text.toString().trim(),
                    repeatCount
                )
            }
        }
        dialog.show()
    }
}
