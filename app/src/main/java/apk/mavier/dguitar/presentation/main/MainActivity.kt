package apk.mavier.dguitar.presentation.main

import apk.mavier.dguitar.R

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import apk.mavier.dguitar.core.audio.GuitarSynth
import apk.mavier.dguitar.databinding.ActivityMainBinding
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.HarmonicChord
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.model.SequencePositions
import apk.mavier.dguitar.domain.model.SoundKind
import apk.mavier.dguitar.domain.model.SoundProfile
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SoundFontRepository
import apk.mavier.dguitar.domain.usecase.BuildGuidedProgressionUseCase
import apk.mavier.dguitar.domain.usecase.BuildHarmonicCircleUseCase
import apk.mavier.dguitar.presentation.common.GuidedProgressionsDialogController
import apk.mavier.dguitar.presentation.common.HandlerScheduler
import apk.mavier.dguitar.presentation.common.NoteSequencePlayer
import apk.mavier.dguitar.presentation.common.NoteStep
import apk.mavier.dguitar.presentation.common.SoundFontCatalog
import apk.mavier.dguitar.presentation.common.SoundFontFamily
import apk.mavier.dguitar.presentation.common.SoundProfileDialogController
import apk.mavier.dguitar.presentation.common.instrumentLabel
import apk.mavier.dguitar.presentation.common.showLabeledMenu
import apk.mavier.dguitar.presentation.improvisation.ImprovisationActivity
import apk.mavier.dguitar.presentation.learning.LearningMenuActivity
import apk.mavier.dguitar.presentation.theme.ChordColorProvider
import apk.mavier.dguitar.presentation.tuner.TunerActivity
import apk.mavier.dguitar.presentation.ui.FretboardView
import apk.mavier.dguitar.presentation.ui.applySystemBarInsets

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var synth: GuitarSynth
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var soundFontRepository: SoundFontRepository
    private lateinit var harmonicCircleUseCase: BuildHarmonicCircleUseCase
    private lateinit var guidedProgressionUseCase: BuildGuidedProgressionUseCase
    private lateinit var harmonicCircleController: HarmonicCircleDialogController
    private lateinit var chordGuideController: ChordGuideDialogController
    private lateinit var guidedController: GuidedProgressionsDialogController
    private lateinit var soundProfileDialog: SoundProfileDialogController
    private lateinit var importSoundFontLauncher: ActivityResultLauncher<Array<String>>
    private var soundProfile = SoundProfile.bundledGuitar
    private var loadedSoundProfile: SoundProfile? = null
    private var loadingSoundProfile: SoundProfile? = null
    private var failedSoundProfile: SoundProfile? = null
    private var config = FretboardConfig()

    private lateinit var sequencePlayer: NoteSequencePlayer
    private lateinit var strumPlayer: NoteSequencePlayer
    private var sequencePlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        val container = (application as DependencyProvider)
        settingsRepository = container.settingsRepository
        soundFontRepository = container.soundFontRepository
        harmonicCircleUseCase = container.harmonicCircleUseCase
        guidedProgressionUseCase = container.guidedProgressionUseCase
        harmonicCircleController = HarmonicCircleDialogController(
            activity = this,
            harmonicCircleUseCase = harmonicCircleUseCase,
            configProvider = { config },
            onConfigChange = { updateConfig(it) }
        )
        config = settingsRepository.load().let { loaded ->
            if (loaded.chordPattern != null) {
                loaded.copy(chordPattern = null, chordPatternStartFret = null)
            } else {
                loaded
            }
        }
        val requestedMode = intent.getStringExtra(EXTRA_MODE)?.let { value ->
            ViewMode.values().firstOrNull { it.name == value }
        }
        if (requestedMode != null) {
            val safeMode = if (config.tuning.strings.size < 6 &&
                requestedMode == ViewMode.CHORD
            ) {
                ViewMode.SCALE
            } else {
                requestedMode
            }
            config = config.copy(mode = safeMode)
        }
        soundProfile = settingsRepository.loadSoundProfile()

        chordGuideController = ChordGuideDialogController(
            activity = this,
            configProvider = { config }
        )
        guidedController = GuidedProgressionsDialogController(
            activity = this,
            useCase = guidedProgressionUseCase,
            keyProvider = { config.rootPc },
            useFlatsProvider = { config.useFlats },
            onPractice = { progression, keyRootPc ->
                startActivity(
                    Intent(this, ImprovisationActivity::class.java).apply {
                        putExtra(ImprovisationActivity.EXTRA_GUIDED_ID, progression.id)
                        putExtra(ImprovisationActivity.EXTRA_GUIDED_KEY, keyRootPc)
                    }
                )
            }
        )

        synth = GuitarSynth()
        importSoundFontLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) importSoundFont(uri)
        }
        soundProfileDialog = SoundProfileDialogController(
            activity = this,
            familyProvider = {
                if (isBass()) SoundFontFamily.BASS else SoundFontFamily.GUITAR
            },
            importedNames = { soundFontRepository.importedNames() },
            onSelect = { applySoundProfile(it) },
            onImport = { importSoundFontLauncher.launch(arrayOf("*/*")) }
        )

        val scheduler = HandlerScheduler()
        sequencePlayer = NoteSequencePlayer(scheduler)
        sequencePlayer.listener = object : NoteSequencePlayer.Listener {
            override fun onNoteStep(step: NoteStep, index: Int) {
                synth.pluck(step.stringIndex, step.midi)
                binding.fretboard.highlightNote(step.stringIndex, step.fret, reveal = true)
                binding.sequenceButton.text = getString(
                    R.string.sequence_playing_note,
                    Pitch.name(Pitch.pitchClass(step.midi), config.useFlats)
                )
            }

            override fun onFinished() {
                stopSequence()
            }
        }
        strumPlayer = NoteSequencePlayer(scheduler)
        strumPlayer.listener = object : NoteSequencePlayer.Listener {
            override fun onNoteStep(step: NoteStep, index: Int) {
                synth.pluck(step.stringIndex, step.midi)
                binding.fretboard.highlightNote(step.stringIndex, step.fret, reveal = false)
            }

            override fun onFinished() = Unit
        }

        binding.fretboard.onNotePlayed = { stringIndex, midi ->
            synth.pluck(stringIndex, midi)
        }
        binding.fretboard.onPatternSelected = { occurrence ->
            if (config.mode == ViewMode.CHORD) {
                updateConfig(
                    config.copy(
                        chordPattern = occurrence?.number,
                        chordPatternStartFret = occurrence?.startFret
                    )
                )
            } else {
                updateConfig(
                    config.copy(
                        selectedPattern = occurrence?.number,
                        selectedPatternStartFret = occurrence?.startFret
                    )
                )
            }
        }

        binding.rootButton.setOnClickListener { showRootMenu(it) }
        binding.menuButton.setOnClickListener { openLearningMenu() }
        binding.settingsButton.setOnClickListener { showSettingsMenu(it) }
        binding.scaleButton.setOnClickListener { showScaleOrChordMenu(it) }
        binding.sequenceButton.setOnClickListener { toggleSequence() }
        binding.strumButton.setOnClickListener { strumChord() }
        binding.guideButton.setOnClickListener { chordGuideController.show() }
        binding.colorsButton.setOnClickListener { toggleColors() }
        binding.tunerButton.setOnClickListener {
            val intent = Intent(this, TunerActivity::class.java).apply {
                putExtra(TunerActivity.EXTRA_TUNING, Tunings.all.indexOf(config.tuning))
                putExtra(TunerActivity.EXTRA_FLATS, config.useFlats)
            }
            startActivity(intent)
        }
        binding.chordsButton.setOnClickListener { openHarmonicCircle() }
        binding.guidedButton.setOnClickListener { guidedController.show() }
        binding.degreesButton.setOnClickListener {
            updateConfig(config.copy(showDegrees = !config.showDegrees))
        }

        renderConfig()
        registerBackToMenu()
        if (intent.getBooleanExtra(EXTRA_OPEN_GUIDED, false) &&
            config.mode == ViewMode.SCALE
        ) {
            binding.root.post { if (!isFinishing) guidedController.show() }
        }
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
        stopSequence()
        stopStrum()
        startActivity(
            Intent(this, LearningMenuActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        finish()
    }

    override fun onResume() {
        super.onResume()
        synth.start()
    }

    override fun onPause() {
        super.onPause()
        stopSequence()
        stopStrum()
        synth.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::sequencePlayer.isInitialized) sequencePlayer.stop()
        if (::strumPlayer.isInitialized) strumPlayer.stop()
    }

    private fun updateConfig(newConfig: FretboardConfig) {
        stopSequence()
        stopStrum()
        config = newConfig
        renderConfig()
        settingsRepository.save(config)
    }

    private fun renderConfig() {
        synth.bassMode = isBass()
        binding.fretboard.config = config

        binding.rootButton.text = Pitch.name(config.rootPc, config.useFlats)
        binding.rootButton.contentDescription =
            "Nota raíz ${Pitch.name(config.rootPc, config.useFlats)}"

        binding.titleText.text = when (config.mode) {
            ViewMode.SCALE -> config.scale.name
            ViewMode.CHORD -> config.chord.name
            ViewMode.FREE -> "Modo libre"
            ViewMode.IMPROV -> "Improvisación"
        }
        binding.subtitleText.text = when (config.mode) {
            ViewMode.SCALE -> "${instrumentLabel(config.tuning.strings.size)} · ${config.scale.formula}"
            ViewMode.CHORD -> "${instrumentLabel(config.tuning.strings.size)} · ${chordNotesText()}"
            ViewMode.FREE -> "${instrumentLabel(config.tuning.strings.size)} · Explora el mástil"
            ViewMode.IMPROV -> "${instrumentLabel(config.tuning.strings.size)} · Progresión"
        }
        val instrumentText = if (isBass()) "bajo" else "guitarra"
        binding.fretboard.contentDescription = when (config.mode) {
            ViewMode.SCALE ->
                "Mástil de $instrumentText. Escala ${config.scale.name} en " +
                    Pitch.name(config.rootPc, config.useFlats)
            ViewMode.CHORD ->
                "Mástil de $instrumentText. Acorde ${config.chord.name} en " +
                    Pitch.name(config.rootPc, config.useFlats)
            ViewMode.FREE -> "Mástil de $instrumentText. Modo libre"
            ViewMode.IMPROV -> "Mástil de $instrumentText. Improvisación"
        }
        binding.settingsButton.contentDescription = "Ajustes"
        binding.menuButton.contentDescription = getString(R.string.back_to_menu)
        binding.scaleButton.text = when (config.mode) {
            ViewMode.SCALE -> config.scale.name
            ViewMode.CHORD -> config.chord.name
            ViewMode.FREE -> "Sin colores"
            ViewMode.IMPROV -> "Progresión"
        }
        binding.scaleButton.isEnabled = config.mode != ViewMode.FREE
        binding.scaleButton.alpha = if (config.mode == ViewMode.FREE) 0.5f else 1f
        binding.colorsButton.text = getString(R.string.color)
        binding.colorsButton.isSelected = config.colorsEnabled
        binding.colorsButton.contentDescription =
            if (config.colorsEnabled) "Colores activados" else "Colores desactivados"
        binding.scaleButton.contentDescription =
            if (config.mode == ViewMode.CHORD) "Elegir acorde" else "Elegir escala"

        val scaleMode = config.mode == ViewMode.SCALE
        binding.strumButton.visibility =
            if (config.mode == ViewMode.CHORD) View.VISIBLE else View.GONE
        binding.strumButton.contentDescription = "Púa: tocar todas las notas del acorde"
        binding.guideButton.visibility =
            if (config.mode == ViewMode.CHORD) View.VISIBLE else View.GONE
        binding.guideButton.contentDescription = "Abrir la guía del acorde"
        binding.sequenceButton.visibility = if (scaleMode) View.VISIBLE else View.GONE
        binding.chordsButton.visibility = if (scaleMode) View.VISIBLE else View.GONE
        binding.guidedButton.visibility = if (scaleMode) View.VISIBLE else View.GONE
        binding.guidedButton.contentDescription =
            getString(R.string.guided_progressions_description)
        binding.degreesButton.visibility = if (scaleMode) View.VISIBLE else View.GONE
        binding.degreesButton.isSelected = config.showDegrees
        binding.degreesButton.contentDescription =
            if (config.showDegrees) "Ocultar grados de la escala" else "Mostrar grados de la escala"
        if (!sequencePlaying) {
            binding.sequenceButton.text = getString(R.string.sequence)
            binding.sequenceButton.isSelected = false
            binding.sequenceButton.contentDescription = "Reproducir secuencia"
        }
        val circleChords = harmonicCircleUseCase.effective(config)
        binding.chordsButton.text = if (circleChords.isEmpty()) {
            "Acordes"
        } else {
            buildChordsLabel(circleChords)
        }
        binding.chordsButton.isSelected = circleChords.isNotEmpty()
        binding.chordsButton.contentDescription = if (circleChords.isEmpty()) {
            "Añadir círculo armónico de la escala"
        } else {
            "Círculo armónico: " + circleChords.joinToString(" ") { chord ->
                "${Pitch.name(chord.rootPc, config.useFlats)} ${chord.quality.name}"
            }
        }
        refreshSoundProfile()
    }

    private fun buildChordsLabel(chords: List<HarmonicChord>): CharSequence {
        val builder = SpannableStringBuilder("Acordes: ")
        chords.forEachIndexed { index, chord ->
            if (index > 0) builder.append(" ")
            val start = builder.length
            builder.append(Pitch.name(chord.rootPc, config.useFlats))
            builder.append(chord.quality.symbol)
            builder.setSpan(
                ForegroundColorSpan(ChordColorProvider.labelColor(chord.rootPc)),
                start,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return builder
    }

    private fun chordNotesText(): String {
        val pitchClasses = config.chord.pitchClasses(config.rootPc)
            .sortedBy { ((it - config.rootPc) % 12 + 12) % 12 }
        return pitchClasses.joinToString(" · ") {
            ChordTheory.noteNameForPitchClass(
                config.rootPc,
                config.chord,
                config.useFlats,
                it,
                solfege = false
            )
        }
    }

    private fun showRootMenu(anchor: View) {
        showLabeledMenu(anchor, (0..11).toList(), { pitchClass -> Pitch.name(pitchClass, config.useFlats) }) { pitchClass ->
            updateConfig(
                config.copy(
                    rootPc = pitchClass,
                    chordPattern = null,
                    chordPatternStartFret = null
                )
            )
        }
    }

    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(Menu.NONE, 0, 0, "Instrumento: ${instrumentLabel(config.tuning.strings.size)}")
        popup.menu.add(Menu.NONE, 1, 1, "Afinación: ${config.tuning.name}")
        popup.menu.add(
            Menu.NONE, 2, 2,
            if (config.useFlats) "Notas: ♭ bemoles" else "Notas: ♯ sostenidos"
        )
        popup.menu.add(
            Menu.NONE,
            3,
            3,
            getString(R.string.sound_profile_value, soundProfileLabel())
        )
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> showInstrumentDialog()
                1 -> anchor.post { showTuningMenu(anchor) }
                2 -> toggleAccidental()
                3 -> soundProfileDialog.show(soundProfile)
            }
            true
        }
        popup.show()
    }

    private fun soundProfileLabel(profile: SoundProfile = soundProfile): String =
        when (profile.kind) {
            SoundKind.SYNTHETIC -> getString(R.string.sound_synthetic)
            SoundKind.BUNDLED -> SoundFontCatalog.find(profile.id)?.let {
                getString(it.labelRes)
            } ?: profile.id
            SoundKind.IMPORTED -> getString(
                R.string.sound_imported,
                profile.id.removeSuffix(".sf2")
            )
        }

    private fun applySoundProfile(profile: SoundProfile) {
        soundProfile = profile
        settingsRepository.saveSoundProfile(profile)
        loadedSoundProfile = null
        loadingSoundProfile = null
        failedSoundProfile = null
        synth.setSoundFont(null)
        refreshSoundProfile()
    }

    private fun refreshSoundProfile() {
        val family = if (isBass()) SoundFontFamily.BASS else SoundFontFamily.GUITAR
        val resolved = SoundFontCatalog.resolve(soundProfile, family)
        if (resolved != soundProfile) {
            soundProfile = resolved
            settingsRepository.saveSoundProfile(resolved)
        }
        if (soundProfile.kind == SoundKind.SYNTHETIC) {
            if (loadedSoundProfile != null || loadingSoundProfile != null) {
                synth.setSoundFont(null)
                loadedSoundProfile = null
                loadingSoundProfile = null
            }
            return
        }
        if (loadedSoundProfile == soundProfile ||
            loadingSoundProfile == soundProfile ||
            failedSoundProfile == soundProfile
        ) {
            return
        }
        val target = soundProfile
        synth.setSoundFont(null)
        loadingSoundProfile = target
        soundFontRepository.load(target) { font ->
            if (loadingSoundProfile == target) loadingSoundProfile = null
            if (isFinishing || isDestroyed) return@load
            if (soundProfile != target) return@load
            if (font != null) {
                synth.setSoundFont(font)
                loadedSoundProfile = target
                failedSoundProfile = null
            } else {
                loadedSoundProfile = null
                failedSoundProfile = target
                Toast.makeText(this, R.string.sound_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                applySoundProfile(SoundProfile.imported(fileName))
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

    private fun showInstrumentDialog() {
        val options = arrayOf("Guitarra (6 cuerdas)", "Bajo 4 cuerdas", "Bajo 5 cuerdas")
        MaterialAlertDialogBuilder(this)
            .setTitle("Instrumento")
            .setItems(options) { _, which ->
                val tuning = when (which) {
                    0 -> if (config.tuning.strings.size == 6) config.tuning else Tunings.STANDARD
                    1 -> Tunings.BASS_4
                    else -> Tunings.BASS_5
                }
                val mode = if (tuning.strings.size < 6 && config.mode == ViewMode.CHORD) {
                    ViewMode.SCALE
                } else {
                    config.mode
                }
                updateConfig(config.copy(tuning = tuning, mode = mode))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun isBass(): Boolean = config.tuning.strings.size < 6

    private fun showScaleOrChordMenu(anchor: View) {
        when (config.mode) {
            ViewMode.SCALE -> showScaleDialog()

            ViewMode.CHORD -> showLabeledMenu(anchor, Chords.all, { it.name }) { chord ->
                updateConfig(
                    config.copy(
                        chord = chord,
                        chordPattern = null,
                        chordPatternStartFret = null
                    )
                )
                binding.root.post { chordGuideController.show() }
            }

            ViewMode.FREE, ViewMode.IMPROV -> Unit
        }
    }

    private fun showScaleDialog() {
        val labels = Scales.all.map { it.name }.toTypedArray()
        val checked = Scales.all.indexOf(config.scale).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Escala")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                updateConfig(config.copy(scale = Scales.all[which]))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showTuningMenu(anchor: View) {
        val tunings = if (isBass()) Tunings.bassAll else Tunings.guitarAll
        showLabeledMenu(anchor, tunings, { it.name }) { tuning ->
            val mode = if (tuning.strings.size < 6 && config.mode == ViewMode.CHORD) {
                ViewMode.SCALE
            } else {
                config.mode
            }
            updateConfig(config.copy(tuning = tuning, mode = mode))
        }
    }

    private fun openHarmonicCircle() {
        if (!harmonicCircleUseCase.supports(config.scale)) {
            Toast.makeText(
                this,
                "El círculo armónico necesita una escala de 7 notas",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (config.circleDegrees.isEmpty()) {
            updateConfig(config.copy(circleDegrees = (1..7).toSet()))
        }
        harmonicCircleController.show()
    }

    private fun toggleSequence() {
        if (sequencePlaying) stopSequence() else startSequence()
    }

    private fun startSequence() {
        stopSequence()

        val occurrence = if (config.selectedPattern != null) {
            binding.fretboard.currentSequenceOccurrence()
        } else {
            null
        }
        val positions = if (occurrence != null) {
            config.scale.segmentSequence(
                config.rootPc,
                config.tuning,
                occurrence,
                FretboardView.FRET_COUNT
            )
        } else {
            config.scale.octaveSequence(
                config.rootPc,
                config.tuning,
                FretboardView.FRET_COUNT
            ).mapNotNull {
                SequencePositions.freePosition(it, config.tuning, FretboardView.FRET_COUNT)
            }
        }
        if (positions.isEmpty()) return

        sequencePlaying = true
        binding.sequenceButton.text = getString(R.string.stop)
        binding.sequenceButton.isSelected = true
        binding.sequenceButton.contentDescription = "Detener secuencia"

        val steps = positions.map { position ->
            NoteStep(
                stringIndex = position.stringIndex,
                fret = position.fret,
                midi = position.midi(config.tuning)
            )
        }
        sequencePlayer.play(steps, SEQUENCE_STEP_MS)
    }

    private fun stopSequence() {
        sequencePlaying = false
        sequencePlayer.stop()
        if (::binding.isInitialized) {
            binding.sequenceButton.text = getString(R.string.sequence)
            binding.sequenceButton.isSelected = false
            binding.sequenceButton.contentDescription = "Reproducir secuencia"
        }
    }

    private fun strumChord() {
        stopStrum()

        val positions = binding.fretboard.currentChordFormationPositions()
        if (positions.isEmpty()) return

        val steps = positions.map { (stringIndex, fret) ->
            NoteStep(
                stringIndex = stringIndex,
                fret = fret,
                midi = config.tuning.strings[stringIndex].openMidi + fret
            )
        }
        strumPlayer.play(steps, STRUM_STEP_MS)
    }

    private fun stopStrum() {
        strumPlayer.stop()
    }

    private fun toggleAccidental() {
        updateConfig(config.copy(useFlats = !config.useFlats))
    }

    private fun toggleColors() {
        updateConfig(config.copy(colorsEnabled = !config.colorsEnabled))
    }

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_OPEN_GUIDED = "extra_open_guided"

        private const val SEQUENCE_STEP_MS = 230L
        private const val STRUM_STEP_MS = 70L
    }
}





