package apk.mavier.dguitar.presentation.common

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import apk.mavier.dguitar.R
import apk.mavier.dguitar.databinding.DialogSongMetadataBinding
import apk.mavier.dguitar.domain.model.Song
import apk.mavier.dguitar.domain.model.Songbook
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SongMetadataDialog(private val activity: Activity) {

    fun show(
        title: String,
        initial: Song?,
        positive: String,
        onSaved: (title: String, artist: String, bpm: Int) -> Unit
    ) {
        val binding = DialogSongMetadataBinding.inflate(activity.layoutInflater)
        binding.songTitleInput.setText(initial?.title.orEmpty())
        binding.songArtistInput.setText(initial?.artist.orEmpty())
        binding.songBpmInput.setText(
            (initial?.bpm ?: Songbook.DEFAULT_BPM).toString()
        )
        binding.songTitleInput.setSelection(binding.songTitleInput.text.length)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(positive, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val songTitle = binding.songTitleInput.text.toString().trim()
                if (songTitle.isEmpty()) {
                    binding.songTitleInput.error =
                        activity.getString(R.string.songbook_title_required)
                    return@setOnClickListener
                }
                val bpm = binding.songBpmInput.text.toString().toIntOrNull()
                    ?: Songbook.DEFAULT_BPM
                dialog.dismiss()
                onSaved(
                    songTitle,
                    binding.songArtistInput.text.toString().trim(),
                    Songbook.normalizeBpm(bpm)
                )
            }
        }
        dialog.show()
    }
}
