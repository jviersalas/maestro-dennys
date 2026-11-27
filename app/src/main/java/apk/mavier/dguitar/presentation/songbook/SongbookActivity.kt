package apk.mavier.dguitar.presentation.songbook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import apk.mavier.dguitar.R
import apk.mavier.dguitar.data.format.SongbookFileCodec
import apk.mavier.dguitar.databinding.ActivitySongbookBinding
import apk.mavier.dguitar.databinding.ItemSongbookEmptyBinding
import apk.mavier.dguitar.databinding.ItemSongbookSongBinding
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.model.Song
import apk.mavier.dguitar.domain.model.Songbook
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SongbookRepository
import apk.mavier.dguitar.presentation.common.SongMetadataDialog
import apk.mavier.dguitar.presentation.common.instrumentLabel
import apk.mavier.dguitar.presentation.learning.LearningMenuActivity
import apk.mavier.dguitar.presentation.ui.applySystemBarInsets
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SongbookActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongbookBinding
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var songbookRepository: SongbookRepository
    private lateinit var metadataDialog: SongMetadataDialog
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>

    private val songs = mutableListOf<Song>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySongbookBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        val container = (application as DependencyProvider)
        settingsRepository = container.settingsRepository
        songbookRepository = container.songbookRepository
        metadataDialog = SongMetadataDialog(this)

        importLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) importSongs(uri)
        }

        binding.backButton.setOnClickListener { openLearningMenu() }
        binding.importButton.setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }
        binding.newSongButton.setOnClickListener { showCreateDialog() }
        registerBackToMenu()
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val config = settingsRepository.load()
        val instrument = instrumentLabel(config.tuning.strings.size)
        songs.clear()
        songs.addAll(songbookRepository.loadSongs())

        binding.songbookSubtitle.text = if (songs.size == 1) {
            getString(R.string.songbook_subtitle_one, instrument)
        } else {
            getString(R.string.songbook_subtitle, instrument, songs.size)
        }

        binding.songList.removeAllViews()
        if (songs.isEmpty()) {
            val empty = ItemSongbookEmptyBinding.inflate(
                layoutInflater,
                binding.songList,
                false
            )
            empty.emptyCreateButton.setOnClickListener { showCreateDialog() }
            binding.songList.addView(empty.root)
            return
        }

        songs.forEach { song ->
            val row = ItemSongbookSongBinding.inflate(
                layoutInflater,
                binding.songList,
                false
            )
            row.songTitle.text = song.title
            row.songMeta.text = buildString {
                if (song.artist.isNotBlank()) {
                    append(song.artist)
                    append(" · ")
                }
                append(Songbook.partSummary(song))
                append(" · ")
                append(song.bpm)
                append(" BPM")
            }
            row.songChords.text = if (Songbook.totalChords(song) == 0) {
                getString(R.string.song_no_chords)
            } else {
                Songbook.firstChordSummary(song, config.useFlats)
            }
            row.songRowRoot.contentDescription = buildString {
                append(song.title)
                if (song.artist.isNotBlank()) {
                    append(", ")
                    append(song.artist)
                }
                append(", ")
                append(Songbook.partSummary(song))
            }
            row.songRowRoot.setOnClickListener { openSong(song.id) }
            row.songRowRoot.setOnLongClickListener {
                showSongMenu(row.songRowRoot, song)
                true
            }
            binding.songList.addView(row.root)
        }
    }

    private fun showCreateDialog() {
        metadataDialog.show(
            getString(R.string.new_song_dialog_title),
            initial = null,
            positive = getString(R.string.create)
        ) { title, artist, bpm ->
            val song = Songbook.newSong(title, artist, bpm)
            songs.add(song)
            persist()
            render()
            openSong(song.id)
        }
    }

    private fun showSongMenu(anchor: android.view.View, song: Song) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.rename)
        popup.menu.add(0, 1, 1, R.string.delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> showRenameDialog(song)
                1 -> showDeleteDialog(song)
            }
            true
        }
        popup.show()
    }

    private fun showRenameDialog(song: Song) {
        metadataDialog.show(
            getString(R.string.song_rename_title),
            initial = song,
            positive = getString(R.string.save)
        ) { title, artist, bpm ->
            val index = songs.indexOfFirst { it.id == song.id }
            if (index < 0) return@show
            songs[index] = songs[index].copy(
                title = title,
                artist = artist,
                bpm = bpm
            )
            persist()
            render()
        }
    }

    private fun showDeleteDialog(song: Song) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.song_delete_title)
            .setMessage(getString(R.string.song_delete_message, song.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                songs.removeAll { it.id == song.id }
                persist()
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun importSongs(uri: Uri) {
        val raw = runCatching {
            contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
        }.getOrNull()
        if (raw.isNullOrBlank()) {
            Toast.makeText(this, R.string.songbook_import_read_error, Toast.LENGTH_SHORT).show()
            return
        }
        if (raw.length > MAX_IMPORT_CHARS) {
            Toast.makeText(this, R.string.songbook_import_error, Toast.LENGTH_SHORT).show()
            return
        }
        val imported = try {
            SongbookFileCodec.decode(raw)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.songbook_import_error, Toast.LENGTH_SHORT).show()
            return
        }
        if (imported.isEmpty()) {
            Toast.makeText(this, R.string.songbook_import_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val existing = songbookRepository.loadSongs()
        songbookRepository.saveSongs(imported + existing)
        render()
        val message = if (imported.size == 1) {
            getString(R.string.songbook_import_success_one)
        } else {
            getString(R.string.songbook_import_success_many, imported.size)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun persist() {
        songbookRepository.saveSongs(songs)
    }

    private fun openSong(songId: String) {
        startActivity(
            Intent(this, SongActivity::class.java).apply {
                putExtra(SongActivity.EXTRA_SONG_ID, songId)
            }
        )
    }

    private fun registerBackToMenu() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    openLearningMenu()
                }
            }
        )
    }

    private fun openLearningMenu() {
        startActivity(
            Intent(this, LearningMenuActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        finish()
    }

    private companion object {
        const val MAX_IMPORT_CHARS = 4_000_000
    }
}
