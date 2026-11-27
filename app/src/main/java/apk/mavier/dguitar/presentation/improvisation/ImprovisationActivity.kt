package apk.mavier.dguitar.presentation.improvisation

import apk.mavier.dguitar.R
import android.content.Intent
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowCompat
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.core.audio.GuitarSynth
import apk.mavier.dguitar.databinding.ActivityImprovisationBinding
import apk.mavier.dguitar.databinding.DialogImprovChordBinding
import apk.mavier.dguitar.databinding.DialogImprovLibraryBinding
import apk.mavier.dguitar.databinding.DialogImprovisationProgressionBinding
import apk.mavier.dguitar.databinding.ItemImprovProgressionBinding
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.GuidedProgression
import apk.mavier.dguitar.domain.model.ImprovBar
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.ImprovInstrument
import apk.mavier.dguitar.domain.model.ImprovProgression
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.ImprovStep
import apk.mavier.dguitar.domain.model.ImprovTimeline
import apk.mavier.dguitar.domain.model.Improvisation
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.ScaleMode
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.model.SequencePositions
import apk.mavier.dguitar.domain.model.SongChord
import apk.mavier.dguitar.domain.model.Tuning
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SoundFontRepository
import apk.mavier.dguitar.domain.usecase.BuildGuidedProgressionUseCase
import apk.mavier.dguitar.domain.usecase.GetImprovisationStepsUseCase
import apk.mavier.dguitar.presentation.common.AudioSession
import apk.mavier.dguitar.presentation.common.ChordPickerPanel
import apk.mavier.dguitar.presentation.common.GuidedProgressionsDialogController
import apk.mavier.dguitar.presentation.common.HandlerScheduler
import apk.mavier.dguitar.presentation.common.instrumentLabel
import apk.mavier.dguitar.presentation.learning.LearningMenuActivity
import apk.mavier.dguitar.presentation.theme.ChordColorProvider
import apk.mavier.dguitar.presentation.ui.FretboardView
import apk.mavier.dguitar.presentation.ui.applySystemBarInsets
import java.util.UUID

class ImprovisationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImprovisationBinding
    private lateinit var audioSession: AudioSession
    private lateinit var tuning: Tuning
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var improvisationRepository: ImprovisationRepository
    private lateinit var soundFontRepository: SoundFontRepository
    private lateinit var stepsUseCase: GetImprovisationStepsUseCase
    private lateinit var guidedProgressionUseCase: BuildGuidedProgressionUseCase
    private lateinit var viewModel: ImprovisationViewModel

    private var instrument = ImprovInstrument.BASS
    private var isGuitar = false
    private var useFlats = true
    private var progressions = mutableListOf<ImprovProgression>()
    private var currentProgressionId = ""
    private var sections = mutableListOf<ImprovSection>()
    private var selectedSection = 0
    private var activeStep = -1
    private var activeBar = -1
    private var bpm = DEFAULT_BPM
    private var playing = false
    private var showDegrees = true
    private var showScale = true
    private var showChord = true
    private var showColors = true
    private var metronomeEnabled = true
    private var scaleRootPc = 0
    private var scaleRootAuto = true
    private var scale: ScaleMode = Scales.PENTATONIC_MINOR
    private var timelineCache: ImprovTimeline? = null
    private lateinit var playbackController: ImprovPlaybackController

    private val handler = Handler(Looper.getMainLooper())
    private var metronomeRunnable: Runnable? = null
    private var beatIndex = 0
    private var toneGenerator: ToneGenerator? = null
    private val arpeggioHandler = Handler(Looper.getMainLooper())
    private var arpeggioRunnable: Runnable? = null
    private var progressionDialog: AlertDialog? = null
    private var progressionDialogBinding: DialogImprovisationProgressionBinding? = null
    private var libraryDialog: AlertDialog? = null
    private var libraryDialogBinding: DialogImprovLibraryBinding? = null
    private lateinit var guidedController: GuidedProgressionsDialogController
    private var pendingGuidedAutoPlay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImprovisationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        val container = (application as DependencyProvider)
        settingsRepository = container.settingsRepository
        improvisationRepository = container.improvisationRepository
        soundFontRepository = container.soundFontRepository
        stepsUseCase = container.improvisationStepsUseCase
        guidedProgressionUseCase = container.guidedProgressionUseCase

        val config = settingsRepository.load()
        tuning = config.tuning
        isGuitar = tuning.strings.size >= 6
        instrument = if (isGuitar) ImprovInstrument.GUITAR else ImprovInstrument.BASS
        useFlats = config.useFlats

        viewModel = ViewModelProvider(
            this,
            ImprovisationViewModel.factory(container, instrument)
        )[ImprovisationViewModel::class.java]
        val saved = viewModel.settings.value
        bpm = saved.bpm
        showDegrees = saved.showDegrees
        showScale = saved.showScale
        showChord = saved.showChord
        showColors = saved.showColors
        metronomeEnabled = saved.metronome
        scale = Scales.all.getOrElse(saved.scaleIndex) {
            Scales.PENTATONIC_MINOR
        }
        progressions = improvisationRepository.loadProgressions(instrument).toMutableList()
        if (progressions.isEmpty()) {
            progressions.add(
                ImprovProgression(
                    id = UUID.randomUUID().toString(),
                    name = "Improvisación 1",
                    sections = listOf(ImprovSection("Intro"))
                )
            )
        }
        val storedProgressionId = improvisationRepository.loadSelectedProgressionId(instrument)
        val currentProgression = progressions.firstOrNull { it.id == storedProgressionId }
            ?: progressions.first()
        currentProgressionId = currentProgression.id
        sections = currentProgression.sections.toMutableList()
        if (sections.isEmpty()) sections.add(ImprovSection("Intro"))
        updateCurrentProgression()
        persistProgressions()
        val storedRoot = saved.scaleRootPc
        scaleRootAuto = storedRoot !in 0..11
        scaleRootPc = if (scaleRootAuto) firstChordRoot() else storedRoot
        selectedSection = 0
        setActiveStep(if (sections.first().chords.isNotEmpty()) 0 else -1)

        binding.barProgress.max = PROGRESS_MAX

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
        binding.improvFretboard.onNotePlayed = { stringIndex, midi ->
            audioSession.pluck(stringIndex, midi)
        }
        toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, METRONOME_VOLUME)
        }.getOrNull()

        playbackController = ImprovPlaybackController(HandlerScheduler(handler))
        playbackController.listener = object : ImprovPlaybackController.Listener {
            override fun onBarChanged(barIndex: Int) {
                val bar = bars().getOrNull(barIndex) ?: return
                activeBar = barIndex
                activeStep = bar.stepIndex
                selectedSection = bar.step.sectionIndex
                renderAll()
                beginBar()
            }

            override fun onProgress(progress: Int) {
                binding.barProgress.progress = progress
            }

            override fun onFinished() {
                playing = false
                returnToStart()
            }
        }

        binding.backButton.setOnClickListener { openLearningMenu() }
        binding.backButton.contentDescription = getString(R.string.back_to_menu)
        binding.playButton.setOnClickListener { togglePlayback() }
        binding.tempoButton.setOnClickListener { showTempoDialog() }
        binding.libraryButton.setOnClickListener { showLibraryDialog() }
        binding.progressionButton.setOnClickListener { showProgressionDialog() }
        binding.guidedButton.contentDescription =
            getString(R.string.guided_progressions_description)
        binding.guidedButton.setOnClickListener { guidedController.show() }
        binding.degreesButton.setOnClickListener {
            showDegrees = !showDegrees
            viewModel.setShowDegrees(showDegrees)
            renderFretboard()
            renderHeader()
        }
        binding.scaleRootButton.setOnClickListener { showScaleRootMenu(it) }
        binding.scaleButton.setOnClickListener { showScaleDialog() }
        binding.showScaleButton.setOnClickListener { toggleScaleLayer() }
        binding.showChordButton.setOnClickListener { toggleChordLayer() }
        binding.colorsButton.setOnClickListener { toggleColors() }
        binding.metronomeButton.setOnClickListener { toggleMetronome() }
        binding.optionsButton.setOnClickListener { toggleOptions() }
        binding.optionsButton.isSelected = false
        binding.optionsButton.contentDescription = "Mostrar opciones"

        guidedController = GuidedProgressionsDialogController(
            activity = this,
            useCase = guidedProgressionUseCase,
            keyProvider = { scaleRootPc },
            useFlatsProvider = { useFlats },
            onPractice = { guided, keyRootPc ->
                applyGuidedProgression(guided, keyRootPc, autoPlay = true)
            }
        )

        val guidedId = intent.getStringExtra(EXTRA_GUIDED_ID)
        val guidedKey = intent.getIntExtra(EXTRA_GUIDED_KEY, -1)
        val guided = guidedId?.let { guidedProgressionUseCase.byId(it) }
        if (guided != null && guidedKey in 0..11) {
            applyGuidedProgression(
                guided,
                guidedKey,
                autoPlay = intent.getBooleanExtra(EXTRA_GUIDED_PLAY, true)
            )
        } else {
            renderAll()
        }
        registerBackToMenu()
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
        stopPlayback()
        startActivity(
            Intent(this, LearningMenuActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        finish()
    }

    override fun onResume() {
        super.onResume()
        audioSession.start()
        if (pendingGuidedAutoPlay) {
            pendingGuidedAutoPlay = false
            binding.root.post { if (!isFinishing) startPlayback() }
        }
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
        audioSession.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::playbackController.isInitialized) playbackController.stop()
        progressionDialog?.dismiss()
        progressionDialog = null
        progressionDialogBinding = null
        libraryDialog?.dismiss()
        libraryDialog = null
        libraryDialogBinding = null
        stopMetronome()
        toneGenerator?.release()
        toneGenerator = null
        handler.removeCallbacksAndMessages(null)
        arpeggioHandler.removeCallbacksAndMessages(null)
    }

    private fun renderAll() {
        refreshProgressionDialog()
        renderFretboard()
        renderHeader()
        renderGuitarControls()
    }

    private fun renderHeader() {
        val section = sections.getOrNull(selectedSection)
        val bars = bars()
        val bar = bars.getOrNull(activeBar)
        val active = bar?.step?.chord ?: steps().getOrNull(activeStep)?.chord
        binding.improvSubtitle.text = buildString {
            append(instrumentLabel(tuning.strings.size))
            append(" · ")
            append(currentProgression()?.name ?: "Improvisación")
            append(" · ")
            append(section?.name ?: "Sin sección")
            if (active != null) {
                append(" · ")
                append(active.label(useFlats))
            }
        }
        binding.degreesButton.isSelected = showDegrees
        binding.degreesButton.contentDescription =
            if (showDegrees) "Ocultar grados del acorde" else "Mostrar grados del acorde"
        binding.tempoButton.text = getString(R.string.tempo_value, bpm)
        binding.tempoButton.contentDescription =
            getString(R.string.tempo_description_value, bpm)
        binding.progressionButton.contentDescription =
            "Editar ${currentProgression()?.name ?: "progresión"}, ${sections.size} secciones"
        binding.libraryButton.contentDescription =
            "Ver improvisaciones guardadas, ${progressions.size}"
        binding.metronomeButton.isSelected = metronomeEnabled
        binding.metronomeButton.contentDescription =
            if (metronomeEnabled) "Desactivar metrónomo" else "Activar metrónomo"
        binding.playButton.isSelected = playing
        binding.playButton.contentDescription =
            if (playing) "Detener reproducción" else "Reproducir progresión"
        binding.barIndicator.text = when {
            bars.isEmpty() -> getString(R.string.no_bars)
            bar != null -> getString(R.string.bar_indicator, bar.globalBar, bars.size)
            else -> getString(R.string.no_active_bar)
        }
        binding.barProgress.progress =
            if (playing && bar != null && ::playbackController.isInitialized) {
                playbackController.progress
            } else {
                0
            }
    }

    private fun renderGuitarControls() {
        binding.scaleRootButton.visibility = View.VISIBLE
        binding.scaleButton.visibility = View.VISIBLE
        binding.showScaleButton.visibility = View.VISIBLE
        binding.showChordButton.visibility = View.VISIBLE
        binding.colorsButton.visibility = View.VISIBLE
        binding.scaleRootButton.text =
            getString(R.string.tonic_value, Pitch.name(scaleRootPc, useFlats))
        binding.scaleRootButton.contentDescription =
            "Tónica de la escala ${Pitch.name(scaleRootPc, useFlats)} " +
                "(${Pitch.solfegeName(scaleRootPc, useFlats)})"
        binding.scaleButton.text = scale.name
        binding.scaleButton.contentDescription = "Escala ${scale.name}"
        binding.showScaleButton.isSelected = showScale
        binding.showScaleButton.contentDescription =
            if (showScale) "Ocultar notas de la escala" else "Mostrar notas de la escala"
        binding.showChordButton.isSelected = showChord
        binding.showChordButton.contentDescription =
            if (showChord) "Ocultar notas del acorde" else "Mostrar notas del acorde"
        binding.colorsButton.isSelected = showColors
        binding.colorsButton.contentDescription =
            if (showColors) "Quitar colores de la escala" else "Mostrar colores de la escala"
    }

    private fun showProgressionDialog() {
        stopPlayback()
        if (progressionDialog?.isShowing == true) return
        val dialogBinding = DialogImprovisationProgressionBinding.inflate(layoutInflater)
        progressionDialogBinding = dialogBinding
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Progresión")
            .setView(dialogBinding.root)
            .setNegativeButton("Cerrar", null)
            .setOnDismissListener {
                progressionDialogBinding = null
                progressionDialog = null
            }
            .create()
        progressionDialog = dialog
        dialog.show()
        refreshProgressionDialog()
    }

    private fun refreshProgressionDialog() {
        val dialogBinding = progressionDialogBinding ?: return
        renderSectionChips(dialogBinding.dialogSectionList)
        renderChordChips(dialogBinding.dialogChordList)
    }

    private fun currentProgression(): ImprovProgression? =
        progressions.firstOrNull { it.id == currentProgressionId }

    private fun updateCurrentProgression() {
        val index = progressions.indexOfFirst { it.id == currentProgressionId }
        if (index >= 0) {
            progressions[index] = progressions[index].copy(sections = sections.toList())
        }
    }

    private fun persistProgressions() {
        improvisationRepository.saveProgressions(instrument, progressions)
        improvisationRepository.saveSelectedProgressionId(instrument, currentProgressionId)
    }

    private fun showLibraryDialog() {
        stopPlayback()
        if (libraryDialog?.isShowing == true) return
        val dialogBinding = DialogImprovLibraryBinding.inflate(layoutInflater)
        libraryDialogBinding = dialogBinding
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Improvisaciones")
            .setView(dialogBinding.root)
            .setNegativeButton("Cerrar", null)
            .setOnDismissListener {
                libraryDialogBinding = null
                libraryDialog = null
            }
            .create()
        libraryDialog = dialog
        dialogBinding.newProgressionButton.setOnClickListener {
            showNameDialog(
                "Nueva improvisación",
                "Improvisación ${progressions.size + 1}",
                "Crear"
            ) { name -> createProgression(name) }
        }
        dialog.show()
        refreshLibraryDialog()
    }

    private fun refreshLibraryDialog() {
        val dialogBinding = libraryDialogBinding ?: return
        val count = progressions.size
        dialogBinding.librarySubtitle.text = buildString {
            append(instrumentLabel(tuning.strings.size))
            append(" · ")
            append(count)
            append(if (count == 1) " guardada" else " guardadas")
        }

        val list = dialogBinding.libraryList
        list.removeAllViews()
        for (progression in progressions) {
            val row = ItemImprovProgressionBinding.inflate(
                layoutInflater,
                list,
                false
            )
            val summary = progressionSummary(progression)
            row.progressionName.text = progression.name
            row.progressionSummary.text = summary
            row.root.isSelected = progression.id == currentProgressionId
            row.root.contentDescription = buildString {
                append("Improvisación ")
                append(progression.name)
                append(", ")
                append(summary)
                if (progression.id == currentProgressionId) append(", seleccionada")
            }
            row.root.setOnClickListener { selectProgression(progression.id) }
            row.root.setOnLongClickListener {
                showLibraryItemMenu(row.root, progression)
                true
            }
            list.addView(row.root)
        }
    }

    private fun progressionSummary(progression: ImprovProgression): String {
        val sectionCount = progression.sections.size
        val chordCount = progression.sections.sumOf { it.chords.size }
        val sectionsLabel = if (sectionCount == 1) "1 sección" else "$sectionCount secciones"
        val chordsLabel = if (chordCount == 1) "1 acorde" else "$chordCount acordes"
        return "$sectionsLabel · $chordsLabel"
    }

    private fun showLibraryItemMenu(anchor: View, progression: ImprovProgression) {
        stopPlayback()
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, "Renombrar")
        popup.menu.add(0, 1, 1, "Eliminar")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> showNameDialog(
                    "Renombrar improvisación",
                    progression.name,
                    "Guardar"
                ) { name -> renameProgression(progression.id, name) }
                1 -> confirmDeleteProgression(progression)
            }
            true
        }
        popup.show()
    }

    private fun selectProgression(id: String) {
        if (id == currentProgressionId) {
            libraryDialog?.dismiss()
            return
        }
        stopPlayback()
        saveSections()
        val next = progressions.firstOrNull { it.id == id } ?: return
        currentProgressionId = next.id
        sections = next.sections.toMutableList()
        if (sections.isEmpty()) sections.add(ImprovSection("Intro"))
        invalidateTimeline()
        selectedSection = 0
        setActiveStep(if (sections.first().chords.isNotEmpty()) 0 else -1)
        saveSections()
        libraryDialog?.dismiss()
        renderAll()
    }

    private fun applyGuidedProgression(
        guided: GuidedProgression,
        keyRootPc: Int,
        autoPlay: Boolean
    ) {
        stopPlayback()
        saveSections()
        val built = guidedProgressionUseCase.buildProgression(guided, keyRootPc, useFlats)
        val existing = progressions.indexOfFirst { it.id == built.id }
        if (existing >= 0) {
            progressions[existing] = built
        } else {
            progressions.add(built)
        }
        currentProgressionId = built.id
        sections = built.sections.toMutableList()
        invalidateTimeline()
        selectedSection = 0
        setActiveStep(if (sections.first().chords.isNotEmpty()) 0 else -1)

        scale = guided.suggestedScale
        viewModel.setScaleIndex(Scales.all.indexOf(guided.suggestedScale))
        scaleRootAuto = false
        scaleRootPc = guided.scaleRoot(keyRootPc)
        viewModel.setScaleRoot(scaleRootPc)
        showScale = true
        viewModel.setShowScale(true)
        metronomeEnabled = true
        viewModel.setMetronome(true)

        saveSections()
        renderAll()

        if (autoPlay) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                binding.root.post { if (!isFinishing) startPlayback() }
            } else {
                pendingGuidedAutoPlay = true
            }
        }
    }

    private fun createProgression(name: String) {
        stopPlayback()
        saveSections()
        val progression = ImprovProgression(
            id = UUID.randomUUID().toString(),
            name = name,
            sections = listOf(ImprovSection("Intro"))
        )
        progressions.add(progression)
        currentProgressionId = progression.id
        sections = progression.sections.toMutableList()
        invalidateTimeline()
        selectedSection = 0
        setActiveStep(-1)
        saveSections()
        renderAll()
    }

    private fun renameProgression(id: String, name: String) {
        val index = progressions.indexOfFirst { it.id == id }
        if (index < 0) return
        progressions[index] = progressions[index].copy(name = name)
        persistProgressions()
        refreshLibraryDialog()
        renderHeader()
    }

    private fun confirmDeleteProgression(progression: ImprovProgression) {
        if (progressions.size <= 1) {
            Toast.makeText(
                this,
                "Debe quedar al menos una improvisación",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar improvisación")
            .setMessage("¿Eliminar “${progression.name}”?")
            .setPositiveButton("Eliminar") { _, _ -> deleteProgression(progression.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteProgression(id: String) {
        if (progressions.size <= 1) {
            Toast.makeText(
                this,
                "Debe quedar al menos una improvisación",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val index = progressions.indexOfFirst { it.id == id }
        if (index < 0) return
        stopPlayback()
        val wasCurrent = id == currentProgressionId
        progressions.removeAt(index)
        if (wasCurrent) {
            val next = progressions.first()
            currentProgressionId = next.id
            sections = next.sections.toMutableList()
            if (sections.isEmpty()) sections.add(ImprovSection("Intro"))
            invalidateTimeline()
            selectedSection = 0
            setActiveStep(if (sections.first().chords.isNotEmpty()) 0 else -1)
            saveSections()
        } else {
            persistProgressions()
        }
        refreshLibraryDialog()
        renderAll()
    }

    private fun renderSectionChips(container: LinearLayout) {
        container.removeAllViews()
        sections.forEachIndexed { index, section ->
            val chip = createChip(section.name, index == selectedSection)
            chip.contentDescription = "Sección ${section.name}"
            chip.setOnClickListener { selectSection(index) }
            chip.setOnLongClickListener {
                showSectionMenu(chip, index)
                true
            }
            container.addView(chip)
        }
        val addChip = createAddChip("Añadir sección")
        addChip.setOnClickListener { addSection() }
        container.addView(addChip)
    }

    private fun renderChordChips(container: LinearLayout) {
        container.removeAllViews()
        val section = sections.getOrNull(selectedSection) ?: return
        val active = bars().getOrNull(activeBar)
        val activeChordIndex = if (active?.step?.sectionIndex == selectedSection) {
            active.step.chordIndex
        } else {
            -1
        }

        if (section.chords.isEmpty()) {
            val hint = TextView(this).apply {
                text = getString(R.string.no_chords)
                setTextColor(getColor(R.color.text_muted))
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), 0, dp(12), 0)
                minHeight = dp(48)
            }
            container.addView(hint)
        }

        section.chords.forEachIndexed { index, chord ->
            val chip = createChip(chordChipText(chord), index == activeChordIndex)
            chip.contentDescription = "Acorde ${chord.label(useFlats)}"
            chip.setOnClickListener {
                selectStep(firstStepOfSection(selectedSection) + index)
            }
            chip.setOnLongClickListener {
                showChordMenu(chip, selectedSection, index)
                true
            }
            container.addView(chip)
        }

        val addChip = createAddChip("Añadir acorde")
        addChip.setOnClickListener { addChord() }
        container.addView(addChip)
    }

    private fun renderFretboard() {
        val bars = bars()
        val bar = bars.getOrNull(activeBar)
        val active = bar?.step?.chord ?: steps().getOrNull(activeStep)?.chord
        val next = bars.getOrNull(activeBar + 1)?.step?.chord
        val config = FretboardConfig(
            tuning = tuning,
            mode = ViewMode.IMPROV,
            rootPc = scaleRootPc,
            scale = scale,
            chord = active?.quality ?: Chords.MAJOR,
            useFlats = useFlats,
            colorsEnabled = showColors,
            showDegrees = showDegrees,
            improvActive = active,
            improvNext = next,
            improvOverlayScale = true,
            improvShowScale = showScale,
            improvShowChord = showChord
        )
        binding.improvFretboard.config = config
        if (active != null) {
            val cardTuning = if (isGuitar) tuning else Tunings.STANDARD
            binding.improvChordDiagram.visibility = View.VISIBLE
            binding.improvChordDiagram.setData(
                ChordTheory.diagram(active.rootPc, active.quality, cardTuning, useFlats)
            )
        } else {
            binding.improvChordDiagram.visibility = View.GONE
        }
        binding.improvFretboard.contentDescription = buildString {
            append("Mástil de ")
            append(instrumentLabel(tuning.strings.size).lowercase())
            append(". Escala ")
            append(scale.name)
            append(" en ")
            append(Pitch.name(scaleRootPc, useFlats))
            if (active != null) {
                append(". Acorde actual ")
                append(active.label(useFlats))
            }
        }
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
            background = AppCompatResources.getDrawable(this@ImprovisationActivity, R.drawable.bg_chip)
            isClickable = true
            isFocusable = true
            isSelected = selected
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        }

    private fun createAddChip(description: String): TextView =
        createChip("＋", false).apply {
            contentDescription = description
            setTextColor(getColor(R.color.accent))
            textSize = 20f
        }

    private fun chordChipText(chord: ImprovChord): CharSequence {
        val builder = SpannableStringBuilder()
        val start = builder.length
        builder.append(chord.label(useFlats))
        if (chord.normalizedBarsPerChord != ImprovChord.DEFAULT_BARS_PER_CHORD) {
            builder.append(" · ")
            builder.append(chord.normalizedBarsPerChord.toString())
            builder.append("c")
        }
        builder.setSpan(
            ForegroundColorSpan(ChordColorProvider.labelColor(chord.rootPc)),
            start,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    private fun selectSection(index: Int) {
        if (index !in sections.indices) return
        stopPlayback()
        selectedSection = index
        setActiveStep(if (sections[index].chords.isEmpty()) -1 else firstStepOfSection(index))
        renderAll()
        steps().getOrNull(activeStep)?.chord?.let { playGuideNote(it) }
    }

    private fun selectStep(stepIndex: Int) {
        stopPlayback()
        val step = steps().getOrNull(stepIndex) ?: return
        selectedSection = step.sectionIndex
        setActiveStep(stepIndex)
        renderAll()
        playGuideNote(step.chord)
    }

    private fun setActiveStep(stepIndex: Int) {
        activeStep = stepIndex
        activeBar = firstBarOfStep(stepIndex)
    }

    private fun addSection() {
        stopPlayback()
        showNameDialog("Nueva sección", "Sección ${sections.size + 1}", "Añadir") { name ->
            sections.add(ImprovSection(name))
            invalidateTimeline()
            selectedSection = sections.lastIndex
            setActiveStep(-1)
            saveSections()
            renderAll()
        }
    }

    private fun renameSection(index: Int) {
        val section = sections.getOrNull(index) ?: return
        stopPlayback()
        showNameDialog("Renombrar sección", section.name, "Guardar") { name ->
            if (index !in sections.indices) return@showNameDialog
            sections[index] = sections[index].copy(name = name)
            invalidateTimeline()
            saveSections()
            renderAll()
        }
    }

    private fun deleteSection(index: Int) {
        if (sections.size <= 1) {
            Toast.makeText(this, "Debe quedar al menos una sección", Toast.LENGTH_SHORT).show()
            return
        }
        stopPlayback()
        sections.removeAt(index)
        invalidateTimeline()
        selectedSection = selectedSection.coerceIn(0, sections.lastIndex)
        setActiveStep(
            if (sections[selectedSection].chords.isEmpty()) {
                -1
            } else {
                firstStepOfSection(selectedSection)
            }
        )
        saveSections()
        renderAll()
    }

    private fun showSectionMenu(anchor: View, index: Int) {
        stopPlayback()
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, "Renombrar")
        popup.menu.add(0, 1, 1, "Eliminar")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> renameSection(index)
                1 -> deleteSection(index)
            }
            true
        }
        popup.show()
    }

    private fun addChord() {
        stopPlayback()
        val section = sections.getOrNull(selectedSection) ?: return
        val initial = steps().getOrNull(activeStep)?.chord
            ?: section.chords.lastOrNull()
            ?: ImprovChord(scaleRootPc, Chords.MAJOR)
        showChordDialog("Añadir acorde", initial, "Añadir") { chord ->
            stopPlayback()
            val current = sections[selectedSection]
            val chords = current.chords + chord
            sections[selectedSection] = current.copy(chords = chords)
            invalidateTimeline()
            setActiveStep(firstStepOfSection(selectedSection) + chords.lastIndex)
            saveSections()
            renderAll()
            playGuideNote(chord)
        }
    }

    private fun editChord(sectionIndex: Int, chordIndex: Int) {
        stopPlayback()
        val section = sections.getOrNull(sectionIndex) ?: return
        val chord = section.chords.getOrNull(chordIndex) ?: return
        showChordDialog("Editar acorde", chord, "Guardar") { updated ->
            stopPlayback()
            if (sectionIndex !in sections.indices) return@showChordDialog
            val current = sections[sectionIndex]
            if (chordIndex !in current.chords.indices) return@showChordDialog
            val chords = current.chords.toMutableList()
            chords[chordIndex] = updated
            sections[sectionIndex] = current.copy(chords = chords)
            invalidateTimeline()
            selectedSection = sectionIndex
            setActiveStep(firstStepOfSection(sectionIndex) + chordIndex)
            saveSections()
            renderAll()
            playGuideNote(updated)
        }
    }

    private fun removeChord(sectionIndex: Int, chordIndex: Int) {
        val section = sections.getOrNull(sectionIndex) ?: return
        if (chordIndex !in section.chords.indices) return
        stopPlayback()
        val chords = section.chords.toMutableList()
        chords.removeAt(chordIndex)
        sections[sectionIndex] = section.copy(chords = chords)
        invalidateTimeline()
        selectedSection = sectionIndex
        setActiveStep(
            if (chords.isEmpty()) {
                -1
            } else {
                firstStepOfSection(sectionIndex) + chordIndex.coerceAtMost(chords.lastIndex)
            }
        )
        saveSections()
        renderAll()
    }

    private fun showChordMenu(anchor: View, sectionIndex: Int, chordIndex: Int) {
        stopPlayback()
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, "Editar")
        popup.menu.add(0, 1, 1, "Quitar")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> editChord(sectionIndex, chordIndex)
                1 -> removeChord(sectionIndex, chordIndex)
            }
            true
        }
        popup.show()
    }

    private fun showNameDialog(
        title: String,
        initial: String,
        positive: String,
        onName: (String) -> Unit
    ) {
        val input = EditText(this).apply {
            setText(initial)
            setSelection(0, text.length)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(getColor(R.color.text_primary))
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(positive) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) onName(name)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showChordDialog(
        title: String,
        initial: ImprovChord,
        positive: String,
        onPicked: (ImprovChord) -> Unit
    ) {
        val dialogBinding = DialogImprovChordBinding.inflate(layoutInflater)
        val chordPanel = ChordPickerPanel(
            activity = this,
            binding = dialogBinding.chordPicker,
            useFlats = useFlats,
            allowBass = false,
            initial = SongChord(initial.rootPc, initial.quality),
            onPreview = { chord ->
                playGuideNote(ImprovChord(chord.rootPc, chord.quality))
            }
        )

        dialogBinding.principalSwitch.isChecked = initial.principalOnly

        val barsOptions = ImprovChord.BARS_OPTIONS
        dialogBinding.barsSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            barsOptions.map { it.toString() }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        dialogBinding.barsSpinner.setSelection(
            barsOptions.indexOf(initial.normalizedBarsPerChord).coerceAtLeast(0)
        )

        val hitsOptions = ImprovChord.HITS_OPTIONS
        dialogBinding.hitsSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            hitsOptions.map { it.toString() }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        dialogBinding.hitsSpinner.setSelection(
            hitsOptions.indexOf(initial.normalizedHitsPerBar).coerceAtLeast(0)
        )
        val hitsVisibility = if (isGuitar) View.GONE else View.VISIBLE
        dialogBinding.hitsLabel.visibility = hitsVisibility
        dialogBinding.hitsSpinner.visibility = hitsVisibility

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(positive) { _, _ ->
                val bars = barsOptions.getOrNull(
                    dialogBinding.barsSpinner.selectedItemPosition
                ) ?: ImprovChord.DEFAULT_BARS_PER_CHORD
                val hits = hitsOptions.getOrNull(
                    dialogBinding.hitsSpinner.selectedItemPosition
                ) ?: ImprovChord.DEFAULT_HITS_PER_BAR
                onPicked(
                    ImprovChord(
                        rootPc = chordPanel.selection.rootPc,
                        quality = chordPanel.selection.quality,
                        principalOnly = dialogBinding.principalSwitch.isChecked,
                        hitsPerBar = hits,
                        barsPerChord = bars
                    )
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showTempoDialog() {
        stopPlayback()
        val input = EditText(this).apply {
            setText(getString(R.string.tempo_value, bpm))
            setSelection(0, text.length)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(getColor(R.color.text_primary))
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Tempo (BPM)")
            .setView(input)
            .setPositiveButton("Aplicar") { _, _ ->
                stopPlayback()
                bpm = (input.text.toString().toIntOrNull() ?: DEFAULT_BPM)
                    .coerceIn(MIN_BPM, MAX_BPM)
                viewModel.setBpm(bpm)
                renderHeader()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showScaleRootMenu(anchor: View) {
        stopPlayback()
        val popup = PopupMenu(this, anchor)
        (0..11).forEach { pitchClass ->
            popup.menu.add(
                Menu.NONE,
                pitchClass,
                pitchClass,
                "${Pitch.name(pitchClass, useFlats)} · " +
                    Pitch.solfegeName(pitchClass, useFlats)
            )
        }
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId in 0..11) {
                scaleRootAuto = false
                scaleRootPc = item.itemId
                viewModel.setScaleRoot(scaleRootPc)
                renderFretboard()
                renderGuitarControls()
            }
            true
        }
        popup.show()
    }

    private fun showScaleDialog() {
        stopPlayback()
        val labels = Scales.all.map { it.name }.toTypedArray()
        val checked = Scales.all.indexOf(scale).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Escala de improvisación")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                scale = Scales.all[which]
                viewModel.setScaleIndex(which)
                renderFretboard()
                renderGuitarControls()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toggleScaleLayer() {
        showScale = !showScale
        viewModel.setShowScale(showScale)
        renderFretboard()
        renderGuitarControls()
    }

    private fun toggleChordLayer() {
        showChord = !showChord
        viewModel.setShowChord(showChord)
        renderFretboard()
        renderGuitarControls()
    }

    private fun toggleColors() {
        showColors = !showColors
        viewModel.setShowColors(showColors)
        renderFretboard()
        renderGuitarControls()
    }

    private fun toggleMetronome() {
        metronomeEnabled = !metronomeEnabled
        viewModel.setMetronome(metronomeEnabled)
        if (metronomeEnabled) {
            if (playing) startMetronomeForBar()
        } else {
            stopMetronome()
        }
        renderHeader()
    }

    private fun toggleOptions() {
        val show = binding.optionsBar.visibility != View.VISIBLE
        binding.optionsBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.optionsButton.isSelected = show
        binding.optionsButton.contentDescription =
            if (show) "Ocultar opciones" else "Mostrar opciones"
    }

    private fun togglePlayback() {
        if (playing) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        val bars = bars()
        if (bars.isEmpty()) {
            Toast.makeText(this, "Añade acordes a la sección", Toast.LENGTH_SHORT).show()
            return
        }
        playing = true
        val startBar = if (activeBar in bars.indices) activeBar else 0
        playbackController.start(bars, startBar, bpm)
    }

    private fun beginBar() {
        startMetronomeForBar()
        val bar = bars().getOrNull(activeBar)
        if (bar != null) {
            if (isGuitar) {
                playGuideNote(bar.step.chord)
            } else if (bar.barInChord == 0) {
                playArpeggio()
            }
        }
    }

    private fun stopPlayback() {
        playing = false
        playbackController.stop()
        stopMetronome()
        arpeggioRunnable?.let { arpeggioHandler.removeCallbacks(it) }
        arpeggioRunnable = null
        if (::binding.isInitialized) renderHeader()
    }

    private fun startMetronomeForBar() {
        stopMetronome()
        if (!metronomeEnabled) return
        beatIndex = 0
        playMetronomeBeat()
        val runnable = object : Runnable {
            override fun run() {
                if (!playing || !metronomeEnabled) return
                beatIndex = (beatIndex + 1) % BEATS_PER_BAR.toInt()
                playMetronomeBeat()
                handler.postDelayed(this, beatDurationMs())
            }
        }
        metronomeRunnable = runnable
        handler.postDelayed(runnable, beatDurationMs())
    }

    private fun playMetronomeBeat() {
        val tone = if (beatIndex == 0) {
            ToneGenerator.TONE_PROP_BEEP
        } else {
            ToneGenerator.TONE_PROP_BEEP2
        }
        toneGenerator?.startTone(tone, METRONOME_TICK_MS)
    }

    private fun stopMetronome() {
        metronomeRunnable?.let { handler.removeCallbacks(it) }
        metronomeRunnable = null
        toneGenerator?.stopTone()
    }

    private fun returnToStart() {
        val bars = bars()
        if (bars.isEmpty()) {
            selectedSection = 0
            setActiveStep(-1)
        } else {
            selectedSection = bars[0].step.sectionIndex
            setActiveStep(0)
        }
        renderAll()
    }

    private fun playArpeggio() {
        if (isGuitar) return
        arpeggioRunnable?.let { arpeggioHandler.removeCallbacks(it) }
        arpeggioRunnable = null
        val bar = bars().getOrNull(activeBar) ?: return
        val chord = bar.step.chord
        val hits = chord.normalizedHitsPerBar
        val allPositions = binding.improvFretboard.improvArpeggioPositions()
        val rootPosition = SequencePositions
            .lowestRootPosition(chord.rootPc, tuning, FretboardView.FRET_COUNT)
            ?.let { it.stringIndex to it.fret }
            ?: allPositions.firstOrNull()
        val positions = if (chord.principalOnly) {
            listOfNotNull(rootPosition)
        } else {
            val ascending = if (rootPosition != null) {
                ascendingArpeggioPositions(chord, rootPosition, hits)
            } else {
                emptyList()
            }
            ascending.ifEmpty { allPositions }
        }
        if (positions.isEmpty()) return
        val stepMs = (stepDurationMs(bar.step) / hits).coerceAtLeast(MIN_NOTE_GAP_MS)
        var hit = 0
        val runnable = object : Runnable {
            override fun run() {
                if (!playing) return
                val (stringIndex, fret) = positions[hit % positions.size]
                val string = tuning.strings.getOrNull(stringIndex) ?: return
                audioSession.pluck(stringIndex, string.openMidi + fret)
                binding.improvFretboard.highlightNote(stringIndex, fret, reveal = false)
                hit++
                if (hit < hits) {
                    arpeggioHandler.postDelayed(this, stepMs)
                }
            }
        }
        arpeggioRunnable = runnable
        arpeggioHandler.post(runnable)
    }

    private fun ascendingArpeggioPositions(
        chord: ImprovChord,
        rootPosition: Pair<Int, Int>,
        hits: Int
    ): List<Pair<Int, Int>> {
        val rootString = tuning.strings.getOrNull(rootPosition.first) ?: return emptyList()
        val rootMidi = rootString.openMidi + rootPosition.second
        val limit = maxOf(hits, 8).coerceAtMost(16)
        return Improvisation.ascendingChordPositions(
            rootMidi,
            chord.quality.intervals,
            limit,
            tuning,
            FretboardView.FRET_COUNT
        ).map { it.stringIndex to it.fret }
    }

    private fun playGuideNote(chord: ImprovChord) {
        if (!isGuitar) return
        val position = SequencePositions.lowestRootPosition(
            chord.rootPc,
            tuning,
            FretboardView.FRET_COUNT
        ) ?: return
        val (stringIndex, fret) = position.stringIndex to position.fret
        val string = tuning.strings.getOrNull(stringIndex) ?: return
        audioSession.pluck(stringIndex, string.openMidi + fret)
        binding.improvFretboard.highlightNote(stringIndex, fret, reveal = false)
    }

    private fun beatDurationMs(): Long = 60_000L / bpm

    private fun barDurationMs(): Long = beatDurationMs() * BEATS_PER_BAR

    private fun stepDurationMs(step: ImprovStep): Long =
        barDurationMs() * step.chord.normalizedBarsPerChord

    private fun bars(): List<ImprovBar> = timeline().bars

    private fun steps(): List<ImprovStep> = timeline().steps

    private fun timeline(): ImprovTimeline {
        val cached = timelineCache
        if (cached != null) return cached
        val built = stepsUseCase.timeline(sections)
        timelineCache = built
        return built
    }

    private fun invalidateTimeline() {
        timelineCache = null
    }

    private fun firstStepOfSection(index: Int): Int =
        timeline().firstStepOfSection(index)

    private fun firstBarOfStep(stepIndex: Int): Int =
        timeline().firstBarOfStep(stepIndex)

    private fun firstChordRoot(): Int =
        sections.firstOrNull()?.chords?.firstOrNull()?.rootPc ?: 0

    private fun syncScaleRootFromProgression() {
        if (scaleRootAuto) scaleRootPc = firstChordRoot()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun saveSections() {
        syncScaleRootFromProgression()
        updateCurrentProgression()
        persistProgressions()
        refreshLibraryDialog()
    }

    companion object {
        const val EXTRA_GUIDED_ID = "extra_guided_id"
        const val EXTRA_GUIDED_KEY = "extra_guided_key"
        const val EXTRA_GUIDED_PLAY = "extra_guided_play"

        private const val DEFAULT_BPM = 80
        private const val MIN_BPM = 40
        private const val MAX_BPM = 220
        private const val BEATS_PER_BAR = 4L
        private const val MIN_NOTE_GAP_MS = 30L
        private const val PROGRESS_MAX = 1000
        private const val METRONOME_VOLUME = 70
        private const val METRONOME_TICK_MS = 45
    }
}
