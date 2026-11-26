package apk.mavier.dguitar.presentation.circle

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import apk.mavier.dguitar.R
import apk.mavier.dguitar.core.audio.GuitarSynth
import apk.mavier.dguitar.databinding.ActivityCircleOfFifthsBinding
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.model.ChordQuality
import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.HarmonicChord
import apk.mavier.dguitar.domain.model.KeySignature
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.SequencePositions
import apk.mavier.dguitar.domain.model.SoundKind
import apk.mavier.dguitar.domain.model.Tuning
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SoundFontRepository
import apk.mavier.dguitar.domain.usecase.BuildCircleOfFifthsUseCase
import apk.mavier.dguitar.presentation.common.HandlerScheduler
import apk.mavier.dguitar.presentation.common.NoteSequencePlayer
import apk.mavier.dguitar.presentation.common.NoteStep
import apk.mavier.dguitar.presentation.common.SoundFontCatalog
import apk.mavier.dguitar.presentation.common.SoundFontFamily
import apk.mavier.dguitar.presentation.common.instrumentLabel
import apk.mavier.dguitar.presentation.ui.FretboardView
import apk.mavier.dguitar.presentation.ui.applySystemBarInsets
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CircleOfFifthsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCircleOfFifthsBinding
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var soundFontRepository: SoundFontRepository
    private lateinit var circleUseCase: BuildCircleOfFifthsUseCase
    private lateinit var synth: GuitarSynth
    private lateinit var sequencePlayer: NoteSequencePlayer
    private lateinit var chordDialog: CircleChordDialog

    private val scheduler = HandlerScheduler()
    private val handler = Handler(Looper.getMainLooper())

    private var tuning: Tuning = Tunings.STANDARD
    private var useFlats = false
    private var selectedIndex = 0
    private var minorMode = false
    private var selectedProgressionId = ""
    private var playMode: PlayMode? = null

    private enum class PlayMode { SCALE, PROGRESSION }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCircleOfFifthsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        val container = application as DependencyProvider
        settingsRepository = container.settingsRepository
        soundFontRepository = container.soundFontRepository
        circleUseCase = container.circleOfFifthsUseCase

        val config = settingsRepository.load()
        tuning = config.tuning
        useFlats = config.useFlats
        selectedIndex = circleUseCase.indexForMajorPc(config.rootPc).takeIf { it >= 0 } ?: 0

        synth = GuitarSynth()
        synth.bassMode = tuning.strings.size < 6
        chordDialog = CircleChordDialog(this) { rootPc, quality ->
            stopPlayback()
            playChord(rootPc, quality)
        }

        setupPlayers()
        setupListeners()
        loadSoundProfile()
        render()
    }

    override fun onResume() {
        super.onResume()
        synth.start()
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
        synth.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun setupPlayers() {
        sequencePlayer = NoteSequencePlayer(scheduler)
        sequencePlayer.listener = object : NoteSequencePlayer.Listener {
            override fun onNoteStep(step: NoteStep, index: Int) {
                synth.pluck(step.stringIndex, step.midi)
            }

            override fun onFinished() {
                if (playMode == PlayMode.SCALE) stopPlayback()
            }
        }
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener { finish() }
        binding.keyButton.setOnClickListener { showKeyDialog() }
        binding.accidentalButton.setOnClickListener {
            useFlats = !useFlats
            render()
        }
        binding.modeButton.setOnClickListener {
            stopPlayback()
            minorMode = !minorMode
            render()
        }
        binding.circleView.onKeySelected = { index -> selectKey(index) }
        binding.playScaleButton.setOnClickListener {
            if (playMode == PlayMode.SCALE) stopPlayback() else playScale()
        }
        binding.playProgressionButton.setOnClickListener {
            if (playMode == PlayMode.PROGRESSION) stopPlayback() else playProgression()
        }
    }

    private fun loadSoundProfile() {
        val family = if (tuning.strings.size < 6) {
            SoundFontFamily.BASS
        } else {
            SoundFontFamily.GUITAR
        }
        val profile = SoundFontCatalog.resolve(settingsRepository.loadSoundProfile(), family)
        if (profile.kind == SoundKind.SYNTHETIC) return
        soundFontRepository.load(profile) { font ->
            if (font != null && !isFinishing && !isDestroyed) synth.setSoundFont(font)
        }
    }

    private fun selectKey(index: Int) {
        val normalized = ((index % 12) + 12) % 12
        if (normalized == selectedIndex) return
        stopPlayback()
        selectedIndex = normalized
        render()
    }

    private fun showKeyDialog() {
        val options = circleUseCase.keys().map { key ->
            getString(
                R.string.circle_key_option,
                key.majorName(useFlats),
                key.minorName(useFlats).removeSuffix("m")
            )
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.circle_key_dialog_title)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                dialog.dismiss()
                selectKey(which)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun render() {
        val key = circleUseCase.key(selectedIndex)
        val signature = key.signature(useFlats)
        val signatureText = signatureText(signature)
        val majorName = key.majorName(useFlats)
        val minorName = key.minorName(useFlats)
        val displayRoot = if (minorMode) minorName.removeSuffix("m") else majorName

        binding.subtitle.text = getString(
            R.string.circle_subtitle,
            instrumentLabel(tuning.strings.size)
        )
        binding.keyButton.text = if (minorMode) {
            getString(R.string.circle_minor_short, displayRoot)
        } else {
            getString(R.string.circle_major_short, displayRoot)
        }
        binding.accidentalButton.text = getString(
            if (useFlats) R.string.circle_flat_symbol else R.string.circle_sharp_symbol
        )
        binding.accidentalButton.isSelected = useFlats
        binding.accidentalButton.contentDescription = getString(
            if (useFlats) R.string.circle_show_sharps else R.string.circle_show_flats
        )
        binding.modeButton.text = getString(
            if (minorMode) R.string.circle_mode_minor else R.string.circle_mode_major
        )
        binding.modeButton.isSelected = minorMode
        binding.modeButton.contentDescription = getString(
            if (minorMode) {
                R.string.circle_mode_major_description
            } else {
                R.string.circle_mode_minor_description
            }
        )

        val highlight = circleUseCase.highlight(key, minorMode)
        binding.circleView.chordMajorIndices = highlight.majorIndices
        binding.circleView.chordMinorIndices = highlight.minorIndices
        binding.circleView.selectedIndex = selectedIndex
        binding.circleView.minorMode = minorMode
        binding.circleView.useFlats = useFlats

        binding.keyTitle.text = if (minorMode) {
            getString(R.string.circle_minor_short, displayRoot)
        } else {
            getString(R.string.circle_major_short, displayRoot)
        }
        binding.keyDetail.text = getString(R.string.circle_key_relation, majorName, minorName)
        binding.keySignature.text = getString(R.string.circle_signature_value, signatureText)

        val notes = key.spelledScale(useFlats, minorMode)
        val chords = circleUseCase.diatonicChords(key, minorMode)

        binding.scaleNotes.removeAllViews()
        for ((index, note) in notes.withIndex()) {
            val chip = layoutInflater.inflate(
                R.layout.item_circle_note,
                binding.scaleNotes,
                false
            ) as TextView
            chip.text = note
            chip.contentDescription = note
            chip.setOnClickListener {
                val chord = chords.getOrNull(index) ?: return@setOnClickListener
                showChordModal(chord, index, note)
            }
            binding.scaleNotes.addView(chip)
        }

        binding.chordList.removeAllViews()
        for ((index, chord) in chords.withIndex()) {
            val row = layoutInflater.inflate(
                R.layout.item_circle_chord,
                binding.chordList,
                false
            ) as TextView
            val roman = circleUseCase.romanFor(index, chord.quality)
            val rootName = notes.getOrElse(index) {
                Pitch.name(chord.rootPc, useFlats)
            }
            val chordName = rootName + chord.quality.symbol
            val qualityName = ChordTheory.qualityName(chord.quality)
            row.text = getString(R.string.circle_chord_row, roman, chordName, qualityName)
            row.contentDescription = getString(
                R.string.circle_chord_description,
                chordName,
                qualityName
            )
            row.setOnClickListener {
                showChordModal(chord, index, rootName)
            }
            binding.chordList.addView(row)
        }

        renderProgressions()
        updatePlayButtons()
    }

    private fun showChordModal(chord: HarmonicChord, degreeIndex: Int, rootName: String) {
        stopPlayback()
        playChord(chord.rootPc, chord.quality)
        chordDialog.show(
            chordName = rootName + chord.quality.symbol,
            roman = circleUseCase.romanFor(degreeIndex, chord.quality),
            rootPc = chord.rootPc,
            quality = chord.quality,
            tuning = tuning,
            useFlats = useFlats
        )
    }

    private fun renderProgressions() {
        val progressions = circleUseCase.progressions(minorMode)
        if (progressions.none { it.id == selectedProgressionId }) {
            selectedProgressionId = progressions.firstOrNull()?.id.orEmpty()
        }
        binding.progressionRow.removeAllViews()
        for (progression in progressions) {
            val chip = layoutInflater.inflate(
                R.layout.item_circle_note,
                binding.progressionRow,
                false
            ) as TextView
            chip.text = progression.name
            chip.isSelected = progression.id == selectedProgressionId
            chip.contentDescription = progression.degrees
            chip.setOnClickListener {
                if (progression.id == selectedProgressionId) return@setOnClickListener
                stopPlayback()
                selectedProgressionId = progression.id
                renderProgressions()
            }
            binding.progressionRow.addView(chip)
        }
    }

    private fun signatureText(signature: KeySignature): String = when {
        signature.isNatural -> getString(R.string.circle_signature_none)
        signature.sharps && signature.count == 1 -> getString(
            R.string.circle_signature_sharp_one,
            signature.accidentals.joinToString(", ")
        )
        signature.sharps -> getString(
            R.string.circle_signature_sharp_many,
            signature.count,
            signature.accidentals.joinToString(", ")
        )
        signature.count == 1 -> getString(
            R.string.circle_signature_flat_one,
            signature.accidentals.joinToString(", ")
        )
        else -> getString(
            R.string.circle_signature_flat_many,
            signature.count,
            signature.accidentals.joinToString(", ")
        )
    }

    private fun updatePlayButtons() {
        binding.playScaleButton.text = getString(
            if (playMode == PlayMode.SCALE) R.string.circle_stop else R.string.circle_play_scale
        )
        binding.playScaleButton.isSelected = playMode == PlayMode.SCALE
        binding.playProgressionButton.text = getString(
            if (playMode == PlayMode.PROGRESSION) {
                R.string.circle_stop
            } else {
                R.string.circle_play_progression
            }
        )
        binding.playProgressionButton.isSelected = playMode == PlayMode.PROGRESSION
    }

    private fun playScale() {
        stopPlayback()
        val key = circleUseCase.key(selectedIndex)
        val rootPc = key.rootPc(minorMode)
        val scale = key.modeScale(minorMode)
        val midis = scale.octaveSequence(rootPc, tuning, FretboardView.FRET_COUNT)
        val steps = midis.mapNotNull { midi ->
            SequencePositions.freePosition(midi, tuning, FretboardView.FRET_COUNT)?.let { pos ->
                NoteStep(pos.stringIndex, pos.fret, midi)
            }
        }
        if (steps.isEmpty()) return
        playMode = PlayMode.SCALE
        updatePlayButtons()
        sequencePlayer.play(steps, SCALE_STEP_MS)
    }

    private fun playProgression() {
        stopPlayback()
        val progression = circleUseCase.progressions(minorMode)
            .firstOrNull { it.id == selectedProgressionId } ?: return
        val key = circleUseCase.key(selectedIndex)
        val chords = progression.improvChords(key.rootPc(minorMode))
        if (chords.isEmpty()) return

        playMode = PlayMode.PROGRESSION
        updatePlayButtons()
        val barMs = 4L * (60_000L / CIRCLE_BPM)
        var index = 0
        val next = object : Runnable {
            override fun run() {
                if (playMode != PlayMode.PROGRESSION) return
                if (index >= chords.size) {
                    stopPlayback()
                    return
                }
                val chord = chords[index]
                playChord(chord.rootPc, chord.quality)
                index++
                handler.postDelayed(this, barMs)
            }
        }
        handler.post(next)
    }

    private fun playChord(rootPc: Int, quality: ChordQuality, delayMs: Long = 0L) {
        val diagram = ChordTheory.diagram(rootPc, quality, tuning, useFlats)
        val lowToHigh = diagram.strings.asReversed()
        lowToHigh.forEachIndexed { stringIndex, string ->
            val fret = string.fret ?: return@forEachIndexed
            val midi = tuning.strings[stringIndex].openMidi + fret
            handler.postDelayed(
                { if (!isFinishing) synth.pluck(stringIndex, midi) },
                delayMs + stringIndex * STRUM_MS
            )
        }
    }

    private fun stopPlayback() {
        if (::sequencePlayer.isInitialized) sequencePlayer.stop()
        handler.removeCallbacksAndMessages(null)
        playMode = null
        if (::binding.isInitialized) updatePlayButtons()
    }

    private companion object {
        const val SCALE_STEP_MS = 190L
        const val STRUM_MS = 70L
        const val CIRCLE_BPM = 90L
    }
}
