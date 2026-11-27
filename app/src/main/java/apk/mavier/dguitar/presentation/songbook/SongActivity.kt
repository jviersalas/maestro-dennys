package apk.mavier.dguitar.presentation.songbook

import android.graphics.Typeface
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import apk.mavier.dguitar.R
import apk.mavier.dguitar.core.audio.GuitarSynth
import apk.mavier.dguitar.data.format.SongbookFileCodec
import apk.mavier.dguitar.databinding.ActivitySongBinding
import apk.mavier.dguitar.databinding.ItemSongGridRowBinding
import apk.mavier.dguitar.databinding.ItemSongPartBinding
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.Song
import apk.mavier.dguitar.domain.model.SongBar
import apk.mavier.dguitar.domain.model.SongChord
import apk.mavier.dguitar.domain.model.SongPart
import apk.mavier.dguitar.domain.model.SongPartType
import apk.mavier.dguitar.domain.model.SongStep
import apk.mavier.dguitar.domain.model.Songbook
import apk.mavier.dguitar.domain.model.SoundProfile
import apk.mavier.dguitar.domain.model.Tuning
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SongbookRepository
import apk.mavier.dguitar.domain.repository.SoundFontRepository
import apk.mavier.dguitar.presentation.common.AudioSession
import apk.mavier.dguitar.presentation.common.ChordAudition
import apk.mavier.dguitar.presentation.common.HandlerScheduler
import apk.mavier.dguitar.presentation.common.NoteSequencePlayer
import apk.mavier.dguitar.presentation.common.NoteStep
import apk.mavier.dguitar.presentation.common.SongChordPickerDialog
import apk.mavier.dguitar.presentation.common.SongMetadataDialog
import apk.mavier.dguitar.presentation.common.SongPartDialog
import apk.mavier.dguitar.presentation.common.SoundFontFamily
import apk.mavier.dguitar.presentation.common.SoundProfileDialogController
import apk.mavier.dguitar.presentation.common.instrumentLabel
import apk.mavier.dguitar.presentation.common.soundProfileLabel
import apk.mavier.dguitar.presentation.theme.ChordColorProvider
import apk.mavier.dguitar.presentation.ui.applySystemBarInsets
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class SongActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongBinding
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var songbookRepository: SongbookRepository
    private lateinit var soundFontRepository: SoundFontRepository
    private lateinit var metadataDialog: SongMetadataDialog
    private lateinit var partDialog: SongPartDialog
    private lateinit var chordPicker: SongChordPickerDialog
    private lateinit var soundProfileDialog: SoundProfileDialogController
    private lateinit var importSoundFontLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var tuning: Tuning
    private var useFlats = true
    private var isGuitar = true
    private var bpm = Songbook.DEFAULT_BPM

    private lateinit var songs: MutableList<Song>
    private var song: Song? = null
    private var stepsCache: List<SongStep>? = null
    private val barViews = mutableMapOf<String, View>()
    private val chordViews = mutableMapOf<String, TextView>()
    private val partCardViews = mutableMapOf<Int, View>()
    private var selectedPartIndex = 0
    private var activeStepIndex = -1
    private var playing = false

    private lateinit var audioSession: AudioSession
    private lateinit var strumPlayer: NoteSequencePlayer
    private lateinit var chordAudition: ChordAudition
    private lateinit var playbackController: SongPlaybackController
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySongBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        val container = (application as DependencyProvider)
        settingsRepository = container.settingsRepository
        songbookRepository = container.songbookRepository
        soundFontRepository = container.soundFontRepository
        metadataDialog = SongMetadataDialog(this)
        partDialog = SongPartDialog(this)
        chordPicker = SongChordPickerDialog(
            activity = this,
            useFlatsProvider = { useFlats },
            onPreview = { chord -> strum(chord) }
        )

        val config = settingsRepository.load()
        tuning = config.tuning
        isGuitar = tuning.strings.size >= 6
        useFlats = config.useFlats

        importSoundFontLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) importSoundFont(uri)
        }
        soundProfileDialog = SoundProfileDialogController(
            activity = this,
            familyProvider = {
                if (isGuitar) SoundFontFamily.GUITAR else SoundFontFamily.BASS
            },
            importedNames = { soundFontRepository.importedNames() },
            onSelect = { audioSession.select(it) },
            onImport = { importSoundFontLauncher.launch(arrayOf("*/*")) }
        )

        songs = songbookRepository.loadSongs().toMutableList()
        val loaded = intent.getStringExtra(EXTRA_SONG_ID)
            ?.let { id -> songs.firstOrNull { it.id == id } }
        if (loaded == null) {
            finish()
            return
        }
        song = loaded
        stepsCache = null
        bpm = loaded.bpm
        selectedPartIndex = 0
        activeStepIndex = firstStepOfPart(0).takeIf {
            loaded.parts.firstOrNull()?.chords?.isNotEmpty() == true
        } ?: -1

        binding.songProgress.max = PROGRESS_MAX
        binding.backButton.setOnClickListener { finish() }
        binding.playButton.setOnClickListener { togglePlayback() }
        binding.tempoButton.setOnClickListener { showTempoDialog() }
        binding.optionsButton.setOnClickListener { showOptionsMenu(it) }
        binding.currentChord.setOnClickListener { previewActiveChord() }
        binding.listenChordButton.setOnClickListener { previewActiveChord() }

        audioSession = AudioSession(
            output = GuitarSynth(),
            settings = settingsRepository,
            soundFonts = soundFontRepository,
            guitar = { isGuitar },
            isActive = { !isFinishing && !isDestroyed },
            onLoadFailed = {
                Toast.makeText(
                    this,
                    R.string.sound_load_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        ).apply {
            bassMode = !isGuitar
        }
        audioSession.refresh()
        strumPlayer = NoteSequencePlayer(HandlerScheduler(handler))
        strumPlayer.listener = object : NoteSequencePlayer.Listener {
            override fun onNoteStep(step: NoteStep, index: Int) {
                audioSession.pluck(step.stringIndex, step.midi)
            }

            override fun onFinished() = Unit
        }
        chordAudition = ChordAudition(
            silenceNotes = { audioSession.silence() },
            player = strumPlayer,
            tuning = { tuning }
        )
        playbackController = SongPlaybackController(
            scheduler = HandlerScheduler(handler),
            tickMs = PROGRESS_TICK_MS
        )
        playbackController.listener = object : SongPlaybackController.Listener {
            override fun onChordChanged(stepIndex: Int) {
                val step = steps().getOrNull(stepIndex) ?: return
                val previousStep = activeStepIndex
                selectedPartIndex = step.partIndex
                activeStepIndex = stepIndex
                updateActiveSelection(previousStep)
                strum(step.chord)
                scrollToActiveChord()
            }

            override fun onProgress(progress: Int) {
                binding.songProgress.progress = progress
            }

            override fun onFinished() {
                playing = false
                returnToStart()
            }
        }

        registerBackToSongbook()
        renderAll()
    }

    override fun onResume() {
        super.onResume()
        audioSession.start()
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
        audioSession.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::playbackController.isInitialized) playbackController.stop()
        if (::strumPlayer.isInitialized) strumPlayer.stop()
        handler.removeCallbacksAndMessages(null)
    }

    private fun registerBackToSongbook() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )
    }

    private fun currentSong(): Song? = song

    private fun steps(): List<SongStep> =
        stepsCache ?: song?.let { Songbook.steps(it) }
            ?.also { stepsCache = it }
            .orEmpty()

    private fun activeStep(): SongStep? = steps().getOrNull(activeStepIndex)

    private fun firstStepOfPart(partIndex: Int): Int =
        song?.let { Songbook.firstStepOfPart(it, partIndex) } ?: 0

    private fun renderAll() {
        renderHeader()
        renderPartTabs()
        renderParts()
        renderChordGuide()
    }

    private fun renderHeader() {
        val current = currentSong() ?: return
        binding.songTitle.text = current.title
        val instrument = instrumentLabel(tuning.strings.size)
        val headerContext = if (current.artist.isBlank()) {
            instrument
        } else {
            "${current.artist} · $instrument"
        }
        binding.songSubtitle.text = if (current.parts.size == 1) {
            getString(R.string.song_screen_subtitle_one, headerContext, bpm)
        } else {
            getString(
                R.string.song_screen_subtitle,
                headerContext,
                current.parts.size,
                bpm
            )
        }

        renderCurrentChord()

        binding.tempoButton.text = bpm.toString()
        binding.tempoButton.contentDescription =
            getString(R.string.song_tempo_description, bpm)
        binding.playButton.isSelected = playing
        binding.playButton.contentDescription =
            getString(if (playing) R.string.song_stop else R.string.song_play)
        binding.optionsButton.contentDescription = getString(R.string.song_options)
        binding.songProgress.progress =
            if (playing && ::playbackController.isInitialized) {
                playbackController.progress
            } else {
                0
            }
    }

    private fun renderCurrentChord() {
        val step = activeStep()
        binding.currentChord.text = step?.chord?.label(useFlats)
            ?: getString(R.string.song_no_current_chord)
        binding.currentChord.setTextColor(
            step?.let { ChordColorProvider.labelColor(it.chord.rootPc) }
                ?: getColor(R.color.text_muted)
        )
        binding.currentChord.isSelected = step != null && playing
        binding.currentChord.contentDescription = step?.let {
            getString(R.string.song_current_chord, it.chord.label(useFlats))
        } ?: getString(R.string.song_no_current_chord)
    }

    private fun updateActiveSelection(previousStepIndex: Int) {
        val previous = steps().getOrNull(previousStepIndex)
        if (previous != null) {
            chordViews[chordTag(previous.partIndex, previous.barIndex, previous.indexInBar)]
                ?.isSelected = false
        }
        activeStep()?.let { step ->
            chordViews[chordTag(step.partIndex, step.barIndex, step.indexInBar)]
                ?.isSelected = true
        }
        for (index in 0 until binding.partTabs.childCount) {
            binding.partTabs.getChildAt(index).isSelected = index == selectedPartIndex
        }
        partCardViews.forEach { (index, card) ->
            card.isSelected = index == selectedPartIndex
        }
        renderCurrentChord()
    }

    private fun renderPartTabs() {
        val current = currentSong() ?: return
        binding.partTabs.removeAllViews()
        current.parts.forEachIndexed { index, part ->
            val chip = createChip(part.name, index == selectedPartIndex)
            chip.contentDescription = if (part.chords.size == 1) {
                getString(R.string.song_part_meta_one, part.type.label)
            } else {
                getString(R.string.song_part_meta, part.type.label, part.chords.size)
            }
            if (part.normalizedRepeatCount > 1) {
                chip.contentDescription = buildString {
                    append(chip.contentDescription)
                    append(" · ")
                    append(
                        getString(
                            R.string.song_part_repeat_badge,
                            part.normalizedRepeatCount
                        )
                    )
                }
            }
            chip.setOnClickListener { selectPart(index) }
            chip.setOnLongClickListener {
                showPartMenu(chip, index)
                true
            }
            binding.partTabs.addView(chip)
        }
        val addChip = createChip(getString(R.string.song_add_part), false)
        addChip.setTextColor(getColor(R.color.accent))
        addChip.setOnClickListener { addPart() }
        binding.partTabs.addView(addChip)
    }

    private fun renderParts() {
        val current = currentSong() ?: return
        binding.partsContainer.removeAllViews()
        barViews.clear()
        chordViews.clear()
        partCardViews.clear()
        val active = activeStep()
        var rowNumber = 1
        current.parts.forEachIndexed { partIndex, part ->
            val row = ItemSongPartBinding.inflate(
                layoutInflater,
                binding.partsContainer,
                false
            )
            row.partTitle.text = part.name
            row.partMeta.text = partMeta(part)
            row.partCard.tag = partCardTag(partIndex)
            partCardViews[partIndex] = row.partCard
            row.partCard.isSelected = partIndex == selectedPartIndex
            row.partCard.contentDescription = partMeta(part)
            row.partCard.setOnClickListener { selectPart(partIndex) }
            row.partCard.setOnLongClickListener {
                showPartMenu(row.partCard, partIndex)
                true
            }
            row.addBarButton.setOnClickListener { addBar(partIndex) }
            row.addChordButton.setOnClickListener { addChordToLastBar(partIndex) }

            row.partEmptyHint.visibility =
                if (part.chords.isEmpty()) View.VISIBLE else View.GONE

            if (part.lyrics.isBlank()) {
                row.partLyrics.visibility = View.GONE
            } else {
                row.partLyrics.text = part.lyrics
                row.partLyrics.visibility = View.VISIBLE
            }

            row.chordGrid.removeAllViews()
            var chordOffset = 0
            part.normalizedBars.forEachIndexed { barIndex, bar ->
                val gridRow = ItemSongGridRowBinding.inflate(
                    layoutInflater,
                    row.chordGrid,
                    false
                )
                gridRow.rowNumber.text = rowNumber.toString()
                gridRow.rowNumber.contentDescription =
                    getString(R.string.song_grid_row_description, rowNumber)
                gridRow.root.tag = barTag(partIndex, barIndex)
                barViews[barTag(partIndex, barIndex)] = gridRow.root
                val cells = listOf(
                    gridRow.cell1,
                    gridRow.cell2,
                    gridRow.cell3,
                    gridRow.cell4
                )
                val barChords = bar.normalizedChords
                val slotWeight = if (barChords.isEmpty()) {
                    1f
                } else {
                    SongBar.MAX_CHORDS.toFloat() / barChords.size
                }
                cells.forEachIndexed { column, cell ->
                    val chord = barChords.getOrNull(column)
                    if (chord == null) {
                        if (barChords.isEmpty()) {
                            cell.visibility = View.VISIBLE
                            setGridCellWeight(cell, 1f)
                            cell.text = ""
                            cell.setBackgroundResource(R.drawable.bg_grid_cell_empty)
                            cell.isClickable = true
                            cell.isFocusable = true
                            cell.isSelected = false
                            cell.contentDescription = getString(
                                R.string.song_grid_cell_add,
                                rowNumber
                            )
                            cell.setOnClickListener {
                                promptAddChord(partIndex, barIndex)
                            }
                            cell.setOnLongClickListener(null)
                        } else {
                            cell.visibility = View.GONE
                            cell.text = ""
                            cell.setOnClickListener(null)
                            cell.setOnLongClickListener(null)
                            cell.contentDescription = null
                            cell.isSelected = false
                        }
                    } else {
                        cell.visibility = View.VISIBLE
                        setGridCellWeight(cell, slotWeight)
                        val stepIndex = firstStepOfPart(partIndex) +
                            chordOffset + column
                        cell.tag = chordTag(partIndex, barIndex, column)
                        chordViews[chordTag(partIndex, barIndex, column)] = cell
                        cell.text = chord.label(useFlats)
                        cell.setTextColor(
                            ChordColorProvider.labelColor(chord.rootPc)
                        )
                        cell.setBackgroundResource(R.drawable.bg_grid_cell)
                        cell.isClickable = true
                        cell.isFocusable = true
                        cell.isSelected = active?.partIndex == partIndex &&
                            active.barIndex == barIndex &&
                            active.indexInBar == column
                        cell.contentDescription = getString(
                            R.string.song_grid_cell_description,
                            chord.label(useFlats),
                            rowNumber
                        )
                        cell.setOnClickListener { selectChord(stepIndex) }
                        cell.setOnLongClickListener {
                            showChordMenu(cell, stepIndex)
                            true
                        }
                    }
                }
                gridRow.addCell.visibility =
                    if (barChords.size < SongBar.MAX_CHORDS) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                gridRow.addCell.contentDescription = getString(
                    R.string.song_grid_cell_add,
                    rowNumber
                )
                gridRow.addCell.setOnClickListener {
                    promptAddChord(partIndex, barIndex)
                }
                chordOffset += barChords.size
                row.chordGrid.addView(gridRow.root)
                rowNumber++
            }
            binding.partsContainer.addView(row.root)
        }
    }

    private fun barTag(partIndex: Int, barIndex: Int): String =
        "song-bar:$partIndex:$barIndex"

    private fun chordTag(partIndex: Int, barIndex: Int, column: Int): String =
        "song-chord:$partIndex:$barIndex:$column"

    private fun partCardTag(partIndex: Int): String = "song-part-card:$partIndex"

    private fun scrollToActiveChord() {
        val step = activeStep() ?: return
        val activeRow = barViews[barTag(step.partIndex, step.barIndex)] ?: return
        binding.partsScroll.post {
            if (isFinishing || isDestroyed) return@post
            val rowLocation = IntArray(2)
            val scrollLocation = IntArray(2)
            activeRow.getLocationOnScreen(rowLocation)
            binding.partsScroll.getLocationOnScreen(scrollLocation)
            val relativeTop = rowLocation[1] - scrollLocation[1]
            val rowHeight = activeRow.height
            val viewportHeight = binding.partsScroll.height
            if (viewportHeight <= 0 || rowHeight <= 0) return@post
            val margin = dp(12)
            val needsScroll = relativeTop < margin ||
                relativeTop + rowHeight * 2 > viewportHeight - margin
            if (!needsScroll) return@post
            val target = (binding.partsScroll.scrollY + relativeTop - margin)
                .coerceAtLeast(0)
            binding.partsScroll.smoothScrollTo(0, target)
        }
    }

    private fun setGridCellWeight(cell: TextView, weight: Float) {
        val params = cell.layoutParams as LinearLayout.LayoutParams
        params.width = 0
        params.weight = weight
        cell.layoutParams = params
    }

    private fun partMeta(part: SongPart): String = buildString {
        append(
            if (part.chords.size == 1) {
                getString(R.string.song_part_meta_one, part.type.label)
            } else {
                getString(R.string.song_part_meta, part.type.label, part.chords.size)
            }
        )
        if (part.normalizedRepeatCount > 1) {
            append(" · ")
            append(
                getString(
                    R.string.song_part_repeat_badge,
                    part.normalizedRepeatCount
                )
            )
        }
    }

    private fun renderChordGuide() {
        val step = activeStep()
        if (step == null) {
            binding.guideChordDiagram.setData(null)
            binding.guideChordDiagram.contentDescription =
                getString(R.string.song_guide_hint)
            binding.guideChordName.text = getString(R.string.song_no_current_chord)
            binding.guideChordName.setTextColor(getColor(R.color.text_muted))
            binding.guideChordMeta.text = getString(R.string.song_capo_zero)
            binding.guideChordNotes.text = ""
            binding.guideChordHint.visibility = View.VISIBLE
            binding.guideChordHint.text = getString(R.string.song_guide_hint)
            binding.listenChordButton.visibility = View.GONE
            binding.listenChordButton.isEnabled = false
            return
        }

        binding.listenChordButton.visibility = View.VISIBLE
        binding.listenChordButton.isEnabled = true
        binding.listenChordButton.alpha = 1f
        val chord = step.chord
        binding.guideChordDiagram.setData(
            ChordTheory.diagram(chord.rootPc, chord.quality, tuning, useFlats)
        )
        binding.guideChordName.text = chord.label(useFlats)
        binding.guideChordName.setTextColor(
            ChordColorProvider.labelColor(chord.rootPc)
        )
        binding.guideChordMeta.text = buildString {
            append(getString(R.string.song_capo_value, 0))
            chord.bassLabel(useFlats)?.let { bass ->
                append(" · ")
                append(getString(R.string.song_bass_value, bass))
            }
        }
        binding.guideChordNotes.text = chord.notesText(useFlats)
        binding.guideChordHint.visibility = View.GONE
        binding.guideChordDiagram.contentDescription = getString(
            R.string.song_chord_preview_description,
            chord.label(useFlats),
            chord.notesText(useFlats)
        )
    }

    private fun createChip(text: CharSequence, selected: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            minHeight = dp(48)
            background = AppCompatResources.getDrawable(
                this@SongActivity,
                R.drawable.bg_chip
            )
            isClickable = true
            isFocusable = true
            isSelected = selected
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        }

    private fun selectPart(index: Int) {
        val current = currentSong() ?: return
        if (index !in current.parts.indices) return
        stopPlayback()
        val previousStep = activeStepIndex
        selectedPartIndex = index
        activeStepIndex = if (current.parts[index].chords.isEmpty()) {
            -1
        } else {
            firstStepOfPart(index)
        }
        updateActiveSelection(previousStep)
        scrollToActiveChord()
    }

    private fun selectChord(stepIndex: Int) {
        val step = steps().getOrNull(stepIndex) ?: return
        stopPlayback()
        val previousStep = activeStepIndex
        selectedPartIndex = step.partIndex
        activeStepIndex = stepIndex
        updateActiveSelection(previousStep)
        strum(step.chord)
    }

    private fun previewActiveChord() {
        activeStep()?.let { strum(it.chord) }
    }

    private fun togglePlayback() {
        if (playing) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val allSteps = steps()
        if (allSteps.isEmpty()) {
            Toast.makeText(this, R.string.song_add_chords_first, Toast.LENGTH_SHORT).show()
            return
        }
        val startIndex = startIndexForSelection()
        if (startIndex < 0) {
            Toast.makeText(
                this,
                R.string.song_part_no_chords,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        playing = true
        playbackController.start(allSteps, startIndex, bpm)
        renderHeader()
    }

    private fun startIndexForSelection(): Int {
        val allSteps = steps()
        if (allSteps.isEmpty()) return -1
        if (activeStepIndex in allSteps.indices &&
            allSteps[activeStepIndex].partIndex == selectedPartIndex
        ) {
            return activeStepIndex
        }
        return allSteps.indexOfFirst { it.partIndex == selectedPartIndex }
    }

    private fun stopPlayback() {
        playing = false
        if (::playbackController.isInitialized) playbackController.stop()
        if (::strumPlayer.isInitialized) strumPlayer.stop()
        audioSession.silence()
        if (::binding.isInitialized) renderHeader()
    }

    private fun returnToStart() {
        selectedPartIndex = 0
        activeStepIndex = if (steps().isEmpty()) -1 else 0
        renderAll()
        scrollToActiveChord()
    }

    private fun strum(chord: SongChord) {
        chordAudition.play(chord)
    }

    private fun addPart() {
        val current = currentSong() ?: return
        val verseCount = current.parts.count { it.type == SongPartType.VERSO } + 1
        partDialog.show(
            getString(R.string.song_part_dialog_new),
            "Verso $verseCount",
            SongPartType.VERSO,
            "",
            1,
            getString(R.string.song_part_add)
        ) { name, type, lyrics, repeatCount ->
            val updatedParts = current.parts + SongPart(
                name = name,
                type = type,
                bars = listOf(SongBar()),
                lyrics = lyrics,
                repeatCount = repeatCount
            )
            val updated = current.copy(parts = updatedParts)
            updateSong(updated)
            selectedPartIndex = updatedParts.lastIndex
            activeStepIndex = -1
            renderAll()
        }
    }

    private fun editPart(index: Int) {
        val current = currentSong() ?: return
        val part = current.parts.getOrNull(index) ?: return
        partDialog.show(
            getString(R.string.song_part_dialog_edit),
            part.name,
            part.type,
            part.lyrics,
            part.repeatCount,
            getString(R.string.save)
        ) { name, type, lyrics, repeatCount ->
            if (index !in current.parts.indices) return@show
            val parts = current.parts.toMutableList()
            parts[index] = parts[index].copy(
                name = name,
                type = type,
                lyrics = lyrics,
                repeatCount = repeatCount
            )
            updateSong(current.copy(parts = parts))
            renderAll()
        }
    }

    private fun deletePart(index: Int) {
        val current = currentSong() ?: return
        if (current.parts.size <= 1) {
            Toast.makeText(this, R.string.song_part_minimum, Toast.LENGTH_SHORT).show()
            return
        }
        val part = current.parts.getOrNull(index) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.song_part_delete_title)
            .setMessage(getString(R.string.song_part_delete_message, part.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                stopPlayback()
                val parts = current.parts.toMutableList()
                if (index !in parts.indices) return@setPositiveButton
                parts.removeAt(index)
                val updated = current.copy(parts = parts)
                updateSong(updated)
                selectedPartIndex = index.coerceAtMost(parts.lastIndex)
                activeStepIndex = if (parts[selectedPartIndex].chords.isEmpty()) {
                    -1
                } else {
                    Songbook.firstStepOfPart(updated, selectedPartIndex)
                }
                renderAll()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun duplicatePart(index: Int) {
        val current = currentSong() ?: return
        val part = current.parts.getOrNull(index) ?: return
        stopPlayback()
        val copy = part.copy(name = uniquePartName(current, part.name))
        val parts = current.parts.toMutableList()
        parts.add(index + 1, copy)
        val updated = current.copy(parts = parts)
        updateSong(updated)
        selectedPartIndex = index + 1
        activeStepIndex = if (copy.chords.isEmpty()) {
            -1
        } else {
            Songbook.firstStepOfPart(updated, index + 1)
        }
        renderAll()
    }

    private fun uniquePartName(song: Song, base: String): String {
        var number = 2
        var candidate = "$base ($number)"
        while (song.parts.any { it.name == candidate }) {
            number++
            candidate = "$base ($number)"
        }
        return candidate
    }

    private fun movePart(index: Int, delta: Int) {
        val current = currentSong() ?: return
        val target = index + delta
        if (index !in current.parts.indices || target !in current.parts.indices) return
        stopPlayback()
        val parts = current.parts.toMutableList()
        val value = parts.removeAt(index)
        parts.add(target, value)
        updateSong(current.copy(parts = parts))
        if (selectedPartIndex == index) {
            selectedPartIndex = target
        } else if (selectedPartIndex == target) {
            selectedPartIndex = index
        }
        activeStepIndex = if (parts[selectedPartIndex].chords.isEmpty()) {
            -1
        } else {
            Songbook.firstStepOfPart(current.copy(parts = parts), selectedPartIndex)
        }
        renderAll()
    }

    private fun addBar(partIndex: Int) {
        val current = currentSong() ?: return
        val part = current.parts.getOrNull(partIndex) ?: return
        stopPlayback()
        val parts = current.parts.toMutableList()
        val bars = part.bars.toMutableList()
        bars.add(SongBar())
        parts[partIndex] = part.copy(bars = bars)
        val updated = current.copy(parts = parts)
        updateSong(updated)
        selectedPartIndex = partIndex
        activeStepIndex = if (part.chords.isEmpty()) {
            -1
        } else {
            Songbook.firstStepOfPart(updated, partIndex)
        }
        renderAll()
        binding.partsScroll.post {
            binding.partsScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun addChordToLastBar(partIndex: Int) {
        val current = currentSong() ?: return
        val part = current.parts.getOrNull(partIndex) ?: return
        val barIndex = when {
            part.bars.isEmpty() -> 0
            part.bars.last().normalizedChords.size >= SongBar.MAX_CHORDS ->
                part.bars.size
            else -> part.bars.lastIndex
        }
        promptAddChord(partIndex, barIndex)
    }

    private fun promptAddChord(partIndex: Int, barIndex: Int) {
        val current = currentSong() ?: return
        val part = current.parts.getOrNull(partIndex) ?: return
        val existingBar = part.bars.getOrNull(barIndex)
        if (existingBar != null &&
            existingBar.normalizedChords.size >= SongBar.MAX_CHORDS
        ) {
            return
        }
        stopPlayback()
        val initial = existingBar?.normalizedChords?.lastOrNull()
            ?: part.chords.lastOrNull()
            ?: SongChord(0, Chords.MAJOR)
        chordPicker.show(
            getString(R.string.song_chord_add_title),
            initial.rootPc,
            initial.quality,
            initial.bassPc,
            getString(R.string.add)
        ) { chord ->
            val latest = currentSong() ?: return@show
            val latestPart = latest.parts.getOrNull(partIndex) ?: return@show
            val bars = latestPart.bars.toMutableList()
            if (barIndex > bars.size) return@show
            if (barIndex == bars.size) bars.add(SongBar())
            val targetBar = bars[barIndex]
            if (targetBar.normalizedChords.size >= SongBar.MAX_CHORDS) {
                return@show
            }
            val newChords = targetBar.normalizedChords + chord
            bars[barIndex] = targetBar.copy(chords = newChords)
            val parts = latest.parts.toMutableList()
            parts[partIndex] = latestPart.copy(bars = bars)
            val updated = latest.copy(parts = parts)
            updateSong(updated)
            selectedPartIndex = partIndex
            activeStepIndex = stepIndexFor(
                updated,
                partIndex,
                barIndex,
                newChords.lastIndex
            )
            renderAll()
            strum(chord)
            scrollToActiveChord()
        }
    }

    private fun stepIndexFor(
        song: Song,
        partIndex: Int,
        barIndex: Int,
        indexInBar: Int
    ): Int {
        val part = song.parts.getOrNull(partIndex) ?: return 0
        val beforeBar = part.bars
            .take(barIndex)
            .sumOf { it.normalizedChords.size }
        return Songbook.firstStepOfPart(song, partIndex) +
            beforeBar + indexInBar
    }

    private fun editChord(stepIndex: Int) {
        val step = steps().getOrNull(stepIndex) ?: return
        stopPlayback()
        chordPicker.show(
            getString(R.string.song_chord_edit_title),
            step.chord.rootPc,
            step.chord.quality,
            step.chord.bassPc,
            getString(R.string.save)
        ) { chord ->
            val latest = currentSong() ?: return@show
            val parts = latest.parts.toMutableList()
            val part = parts.getOrNull(step.partIndex) ?: return@show
            val bars = part.bars.toMutableList()
            val bar = bars.getOrNull(step.barIndex) ?: return@show
            val chords = bar.normalizedChords.toMutableList()
            if (step.indexInBar !in chords.indices) return@show
            chords[step.indexInBar] = chord
            bars[step.barIndex] = bar.copy(chords = chords)
            parts[step.partIndex] = part.copy(bars = bars)
            val updated = latest.copy(parts = parts)
            updateSong(updated)
            selectedPartIndex = step.partIndex
            activeStepIndex = stepIndexFor(
                updated,
                step.partIndex,
                step.barIndex,
                step.indexInBar
            )
            renderAll()
            strum(chord)
        }
    }

    private fun removeChord(stepIndex: Int) {
        val step = steps().getOrNull(stepIndex) ?: return
        val current = currentSong() ?: return
        val part = current.parts.getOrNull(step.partIndex) ?: return
        val bar = part.bars.getOrNull(step.barIndex) ?: return
        val chord = bar.normalizedChords.getOrNull(step.indexInBar) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.song_chord_remove_title)
            .setMessage(
                getString(
                    R.string.song_chord_remove_message,
                    chord.label(useFlats),
                    part.name
                )
            )
            .setPositiveButton(R.string.remove) { _, _ ->
                stopPlayback()
                val latest = currentSong() ?: return@setPositiveButton
                val parts = latest.parts.toMutableList()
                val latestPart = parts.getOrNull(step.partIndex)
                    ?: return@setPositiveButton
                val bars = latestPart.bars.toMutableList()
                val latestBar = bars.getOrNull(step.barIndex)
                    ?: return@setPositiveButton
                val chords = latestBar.normalizedChords.toMutableList()
                if (step.indexInBar !in chords.indices) {
                    return@setPositiveButton
                }
                chords.removeAt(step.indexInBar)
                bars[step.barIndex] = latestBar.copy(chords = chords)
                parts[step.partIndex] = latestPart.copy(bars = bars)
                val updated = latest.copy(parts = parts)
                updateSong(updated)
                selectedPartIndex = step.partIndex
                activeStepIndex = if (updated.parts[step.partIndex].chords.isEmpty()) {
                    -1
                } else {
                    Songbook.firstStepOfPart(updated, step.partIndex)
                }
                renderAll()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun duplicateChord(stepIndex: Int) {
        val step = steps().getOrNull(stepIndex) ?: return
        val current = currentSong() ?: return
        val part = current.parts.getOrNull(step.partIndex) ?: return
        val bar = part.bars.getOrNull(step.barIndex) ?: return
        stopPlayback()
        val parts = current.parts.toMutableList()
        val bars = part.bars.toMutableList()
        val chords = bar.normalizedChords.toMutableList()
        val targetBar: Int
        val targetIndex: Int
        if (chords.size < SongBar.MAX_CHORDS) {
            chords.add(step.indexInBar + 1, step.chord)
            bars[step.barIndex] = bar.copy(chords = chords)
            targetBar = step.barIndex
            targetIndex = step.indexInBar + 1
        } else {
            bars.add(step.barIndex + 1, SongBar(listOf(step.chord)))
            targetBar = step.barIndex + 1
            targetIndex = 0
        }
        parts[step.partIndex] = part.copy(bars = bars)
        val updated = current.copy(parts = parts)
        updateSong(updated)
        selectedPartIndex = step.partIndex
        activeStepIndex = stepIndexFor(
            updated,
            step.partIndex,
            targetBar,
            targetIndex
        )
        renderAll()
        strum(step.chord)
    }

    private fun moveChord(stepIndex: Int, delta: Int) {
        val step = steps().getOrNull(stepIndex) ?: return
        val current = currentSong() ?: return
        val part = current.parts.getOrNull(step.partIndex) ?: return
        val bar = part.bars.getOrNull(step.barIndex) ?: return
        val chords = bar.normalizedChords.toMutableList()
        val target = step.indexInBar + delta
        if (target !in chords.indices) return
        stopPlayback()
        val value = chords.removeAt(step.indexInBar)
        chords.add(target, value)
        val parts = current.parts.toMutableList()
        val bars = part.bars.toMutableList()
        bars[step.barIndex] = bar.copy(chords = chords)
        parts[step.partIndex] = part.copy(bars = bars)
        val updated = current.copy(parts = parts)
        updateSong(updated)
        selectedPartIndex = step.partIndex
        activeStepIndex = stepIndexFor(
            updated,
            step.partIndex,
            step.barIndex,
            target
        )
        renderAll()
    }

    private fun showOptionsMenu(anchor: View) {
        stopPlayback()
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.song_edit_title)
        popup.menu.add(0, 1, 1, R.string.song_part_add)
        popup.menu.add(
            0,
            2,
            2,
            if (useFlats) R.string.circle_show_sharps else R.string.circle_show_flats
        )
        popup.menu.add(
            0,
            3,
            3,
            getString(R.string.sound_profile_value, soundProfileLabel(this, audioSession.profile))
        )
        popup.menu.add(0, 5, 5, R.string.song_share)
        popup.menu.add(0, 6, 6, R.string.song_delete_title)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> showEditSongDialog()
                1 -> addPart()
                2 -> toggleAccidentals()
                3 -> soundProfileDialog.show(audioSession.profile)
                5 -> shareCurrentSong()
                6 -> showDeleteSongDialog()
            }
            true
        }
        popup.show()
    }

    private fun toggleAccidentals() {
        val config = settingsRepository.load()
        useFlats = !useFlats
        settingsRepository.save(config.copy(useFlats = useFlats))
        renderAll()
    }

    private fun showPartMenu(anchor: View, index: Int) {
        val current = currentSong() ?: return
        stopPlayback()
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.rename)
        popup.menu.add(0, 1, 1, R.string.move_left).isEnabled = index > 0
        popup.menu.add(0, 2, 2, R.string.move_right).isEnabled =
            index < current.parts.lastIndex
        popup.menu.add(0, 3, 3, R.string.song_part_repeat_action)
        popup.menu.add(0, 4, 4, R.string.duplicate)
        popup.menu.add(0, 5, 5, R.string.delete).isEnabled = current.parts.size > 1
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> movePart(index, -1)
                2 -> movePart(index, 1)
                3 -> showRepeatDialog(index)
                4 -> duplicatePart(index)
                5 -> deletePart(index)
                else -> editPart(index)
            }
            true
        }
        popup.show()
    }

    private fun showChordMenu(anchor: View, stepIndex: Int) {
        val step = steps().getOrNull(stepIndex) ?: return
        stopPlayback()
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.edit)
        popup.menu.add(0, 1, 1, R.string.move_left).isEnabled = step.indexInBar > 0
        popup.menu.add(0, 2, 2, R.string.move_right).isEnabled =
            step.indexInBar < (currentSong()?.parts?.getOrNull(step.partIndex)
                ?.bars?.getOrNull(step.barIndex)
                ?.normalizedChords?.lastIndex ?: 0)
        popup.menu.add(0, 3, 3, R.string.duplicate)
        popup.menu.add(0, 4, 4, R.string.remove)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> moveChord(stepIndex, -1)
                2 -> moveChord(stepIndex, 1)
                3 -> duplicateChord(stepIndex)
                4 -> removeChord(stepIndex)
                else -> editChord(stepIndex)
            }
            true
        }
        popup.show()
    }

    private fun showEditSongDialog() {
        val current = currentSong() ?: return
        metadataDialog.show(
            getString(R.string.song_edit_title),
            initial = current,
            positive = getString(R.string.save)
        ) { title, artist, newBpm ->
            stopPlayback()
            bpm = newBpm
            updateSong(current.copy(title = title, artist = artist, bpm = newBpm))
            renderAll()
        }
    }

    private fun showDeleteSongDialog() {
        val current = currentSong() ?: return
        stopPlayback()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.song_delete_title)
            .setMessage(getString(R.string.song_delete_message, current.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                songs.removeAll { it.id == current.id }
                songbookRepository.saveSongs(songs)
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTempoDialog() {
        stopPlayback()
        val input = EditText(this).apply {
            setText(bpm.toString())
            setSelection(0, text.length)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(getColor(R.color.text_primary))
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.song_bpm_label)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val current = currentSong() ?: return@setPositiveButton
                val newBpm = Songbook.normalizeBpm(
                    input.text.toString().toIntOrNull() ?: Songbook.DEFAULT_BPM
                )
                bpm = newBpm
                updateSong(current.copy(bpm = newBpm))
                renderHeader()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRepeatDialog(index: Int) {
        val current = currentSong() ?: return
        val part = current.parts.getOrNull(index) ?: return
        stopPlayback()
        val input = EditText(this).apply {
            setText(part.normalizedRepeatCount.toString())
            setSelection(0, text.length)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(getColor(R.color.text_primary))
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.song_part_repeat_title)
            .setMessage(R.string.song_part_repeat_label)
            .setView(input)
            .setPositiveButton(R.string.apply) { _, _ ->
                val repeatCount = Songbook.normalizeRepeatCount(
                    input.text.toString().toIntOrNull() ?: 1
                )
                val parts = current.parts.toMutableList()
                if (index !in parts.indices) return@setPositiveButton
                parts[index] = parts[index].copy(repeatCount = repeatCount)
                val updated = current.copy(parts = parts)
                updateSong(updated)
                selectedPartIndex = index
                activeStepIndex = if (parts[index].chords.isEmpty()) {
                    -1
                } else {
                    Songbook.firstStepOfPart(updated, index)
                }
                renderAll()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateSong(updated: Song) {
        song = updated
        stepsCache = null
        val index = songs.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            songs[index] = updated
        }
        songbookRepository.saveSongs(songs)
    }

    private fun shareCurrentSong() {
        val current = currentSong() ?: return
        try {
            val dir = File(cacheDir, "songbook").apply { mkdirs() }
            val file = File(
                dir,
                safeFileName(current.title) + "." + SongbookFileCodec.EXTENSION
            )
            file.writeText(SongbookFileCodec.encode(listOf(current)))
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val share = Intent(Intent.ACTION_SEND).apply {
                type = SongbookFileCodec.MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, current.title)
                clipData = ClipData.newRawUri(current.title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(
                Intent.createChooser(
                    share,
                    getString(R.string.song_share_chooser)
                )
            )
        } catch (_: Exception) {
            Toast.makeText(this, R.string.song_share_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(60)
            .ifBlank { "cancion" }

    private fun importSoundFont(uri: Uri) {
        val input = runCatching { contentResolver.openInputStream(uri) }.getOrNull()
        if (input == null) {
            Toast.makeText(this, R.string.sound_import_failed, Toast.LENGTH_SHORT).show()
            return
        }
        soundFontRepository.importFont(displayName(uri), input) { result ->
            if (isFinishing || isDestroyed) return@importFont
            val fileName = result.getOrNull()
            if (fileName != null) {
                audioSession.select(SoundProfile.imported(fileName))
                Toast.makeText(
                    this,
                    getString(R.string.sound_import_success, fileName),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val message = result.exceptionOrNull()?.message
                    ?: getString(R.string.sound_import_failed)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return name ?: uri.lastPathSegment ?: "sonido.sf2"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SONG_ID = "extra_song_id"

        private const val PROGRESS_MAX = 1000
        private const val PROGRESS_TICK_MS = 100L
    }
}
