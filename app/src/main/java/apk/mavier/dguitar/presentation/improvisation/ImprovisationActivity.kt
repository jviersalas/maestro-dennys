package apk.mavier.dguitar.presentation.improvisation

import apk.mavier.dguitar.R
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.core.view.WindowCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.core.audio.GuitarSynth
import apk.mavier.dguitar.databinding.ActivityImprovisationBinding
import apk.mavier.dguitar.databinding.DialogImprovChordBinding
import apk.mavier.dguitar.databinding.DialogImprovisationProgressionBinding
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.ImprovBar
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.ImprovInstrument
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.ImprovStep
import apk.mavier.dguitar.domain.model.Improvisation
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.ScaleMode
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.model.SequencePositions
import apk.mavier.dguitar.domain.model.Tuning
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.usecase.GetImprovisationStepsUseCase
import apk.mavier.dguitar.presentation.theme.ChordColorProvider
import apk.mavier.dguitar.presentation.ui.FretboardView
import apk.mavier.dguitar.presentation.ui.applySystemBarInsets

class ImprovisationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImprovisationBinding
    private var synth: GuitarSynth? = null
    private lateinit var tuning: Tuning
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var improvisationRepository: ImprovisationRepository
    private lateinit var stepsUseCase: GetImprovisationStepsUseCase

    private var instrument = ImprovInstrument.BASS
    private var isGuitar = false
    private var useFlats = true
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
    private var barStartedAt = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var playbackRunnable: Runnable? = null
    private var progressRunnable: Runnable? = null
    private var metronomeRunnable: Runnable? = null
    private var beatIndex = 0
    private var toneGenerator: ToneGenerator? = null
    private val arpeggioHandler = Handler(Looper.getMainLooper())
    private var arpeggioRunnable: Runnable? = null
    private var progressionDialog: AlertDialog? = null
    private var progressionDialogBinding: DialogImprovisationProgressionBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImprovisationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        val container = (application as DependencyProvider)
        settingsRepository = container.settingsRepository
        improvisationRepository = container.improvisationRepository
        stepsUseCase = container.improvisationStepsUseCase

        val config = settingsRepository.load()
        tuning = config.tuning
        isGuitar = tuning.strings.size >= 6
        instrument = if (isGuitar) ImprovInstrument.GUITAR else ImprovInstrument.BASS
        useFlats = config.useFlats
        bpm = improvisationRepository.loadBpm(instrument)
        showDegrees = improvisationRepository.loadShowDegrees()
        showScale = improvisationRepository.loadShowScale()
        showChord = improvisationRepository.loadShowChord()
        showColors = improvisationRepository.loadShowColors()
        metronomeEnabled = improvisationRepository.loadMetronome()
        scale = Scales.all.getOrElse(improvisationRepository.loadScaleIndex()) {
            Scales.PENTATONIC_MINOR
        }
        sections = improvisationRepository.loadSections(instrument).toMutableList()
        if (sections.isEmpty()) sections.add(ImprovSection("Intro"))
        val storedRoot = improvisationRepository.loadScaleRoot()
        scaleRootAuto = storedRoot !in 0..11
        scaleRootPc = if (scaleRootAuto) firstChordRoot() else storedRoot
        selectedSection = 0
        setActiveStep(if (sections.first().chords.isNotEmpty()) 0 else -1)

        binding.barProgress.max = PROGRESS_MAX

        val guitarSynth = GuitarSynth().apply { bassMode = !isGuitar }
        synth = guitarSynth
        if (!isGuitar) {
            binding.improvFretboard.onNotePlayed = { stringIndex, midi ->
                guitarSynth.pluck(stringIndex, midi)
            }
        }
        toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, METRONOME_VOLUME)
        }.getOrNull()

        binding.backButton.setOnClickListener { finish() }
        binding.playButton.setOnClickListener { togglePlayback() }
        binding.tempoButton.setOnClickListener { showTempoDialog() }
        binding.progressionButton.setOnClickListener { showProgressionDialog() }
        binding.degreesButton.setOnClickListener {
            showDegrees = !showDegrees
            improvisationRepository.saveShowDegrees(showDegrees)
            renderFretboard()
            renderHeader()
        }
        binding.scaleRootButton.setOnClickListener { showScaleRootMenu(it) }
        binding.scaleButton.setOnClickListener { showScaleDialog() }
        binding.showScaleButton.setOnClickListener { toggleScaleLayer() }
        binding.showChordButton.setOnClickListener { toggleChordLayer() }
        binding.colorsButton.setOnClickListener { toggleColors() }
        binding.metronomeButton.setOnClickListener { toggleMetronome() }

        renderAll()
    }

    override fun onResume() {
        super.onResume()
        synth?.start()
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
        synth?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressionDialog?.dismiss()
        progressionDialog = null
        progressionDialogBinding = null
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
            append(instrumentLabel())
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
        binding.tempoButton.text = "Tempo $bpm"
        binding.tempoButton.contentDescription = "Cambiar tempo, $bpm BPM"
        binding.progressionButton.contentDescription =
            "Editar progresión, ${sections.size} secciones"
        binding.metronomeButton.isSelected = metronomeEnabled
        binding.metronomeButton.contentDescription =
            if (metronomeEnabled) "Desactivar metrónomo" else "Activar metrónomo"
        binding.playButton.text = if (playing) "Detener" else "Play"
        binding.playButton.isSelected = playing
        binding.playButton.contentDescription =
            if (playing) "Detener reproducción" else "Reproducir progresión"
        binding.barIndicator.text = when {
            bars.isEmpty() -> "Sin compases"
            bar != null -> "Compás ${bar.globalBar} / ${bars.size}"
            else -> "Sin compás activo"
        }
        binding.barProgress.progress = if (playing && bar != null) currentBarProgress() else 0
    }

    private fun renderGuitarControls() {
        binding.scaleRootButton.visibility = View.VISIBLE
        binding.scaleButton.visibility = View.VISIBLE
        binding.showScaleButton.visibility = View.VISIBLE
        binding.showChordButton.visibility = View.VISIBLE
        binding.colorsButton.visibility = View.VISIBLE
        binding.scaleRootButton.text = "Tónica ${Pitch.name(scaleRootPc, useFlats)}"
        binding.scaleRootButton.contentDescription =
            "Tónica de la escala ${Pitch.name(scaleRootPc, useFlats)}"
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
                text = "Sin acordes"
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
        binding.improvFretboard.contentDescription = buildString {
            append("Mástil de ")
            append(instrumentLabel().lowercase())
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
            background = getDrawable(R.drawable.bg_chip)
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
        val rootNames = (0..11).map { Pitch.name(it, useFlats) }
        dialogBinding.rootSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            rootNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        dialogBinding.rootSpinner.setSelection(initial.rootPc.coerceIn(0, 11))

        val qualityNames = Chords.all.map { it.name }
        dialogBinding.qualitySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            qualityNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val qualityIndex = Chords.all.indexOf(initial.quality).coerceAtLeast(0)
        dialogBinding.qualitySpinner.setSelection(qualityIndex)

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
                val root = dialogBinding.rootSpinner.selectedItemPosition.coerceIn(0, 11)
                val quality = Chords.all.getOrNull(
                    dialogBinding.qualitySpinner.selectedItemPosition
                ) ?: Chords.MAJOR
                val bars = barsOptions.getOrNull(
                    dialogBinding.barsSpinner.selectedItemPosition
                ) ?: ImprovChord.DEFAULT_BARS_PER_CHORD
                val hits = hitsOptions.getOrNull(
                    dialogBinding.hitsSpinner.selectedItemPosition
                ) ?: ImprovChord.DEFAULT_HITS_PER_BAR
                onPicked(
                    ImprovChord(
                        rootPc = root,
                        quality = quality,
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
            setText(bpm.toString())
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
                improvisationRepository.saveBpm(instrument, bpm)
                renderHeader()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showScaleRootMenu(anchor: View) {
        stopPlayback()
        val popup = PopupMenu(this, anchor)
        (0..11).forEach { pitchClass ->
            popup.menu.add(Menu.NONE, pitchClass, pitchClass, Pitch.name(pitchClass, useFlats))
        }
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId in 0..11) {
                scaleRootAuto = false
                scaleRootPc = item.itemId
                improvisationRepository.saveScaleRoot(scaleRootPc)
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
                improvisationRepository.saveScaleIndex(which)
                renderFretboard()
                renderGuitarControls()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toggleScaleLayer() {
        showScale = !showScale
        improvisationRepository.saveShowScale(showScale)
        renderFretboard()
        renderGuitarControls()
    }

    private fun toggleChordLayer() {
        showChord = !showChord
        improvisationRepository.saveShowChord(showChord)
        renderFretboard()
        renderGuitarControls()
    }

    private fun toggleColors() {
        showColors = !showColors
        improvisationRepository.saveShowColors(showColors)
        renderFretboard()
        renderGuitarControls()
    }

    private fun toggleMetronome() {
        metronomeEnabled = !metronomeEnabled
        improvisationRepository.saveMetronome(metronomeEnabled)
        if (metronomeEnabled) {
            if (playing) startMetronomeForBar()
        } else {
            stopMetronome()
        }
        renderHeader()
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
        if (activeBar !in bars.indices) {
            setActiveStep(0)
        } else {
            activeStep = bars[activeBar].stepIndex
            selectedSection = bars[activeBar].step.sectionIndex
        }
        barStartedAt = SystemClock.elapsedRealtime()
        renderAll()
        beginBar()
    }

    private fun beginBar() {
        barStartedAt = SystemClock.elapsedRealtime()
        startProgressTicker()
        startMetronomeForBar()
        val bar = bars().getOrNull(activeBar)
        if (bar != null) {
            if (isGuitar) {
                playGuideNote(bar.step.chord)
            } else if (bar.barInChord == 0) {
                playArpeggio()
            }
        }
        scheduleBarAdvance()
    }

    private fun scheduleBarAdvance() {
        playbackRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                if (!playing) return
                val bars = bars()
                val next = activeBar + 1
                if (next >= bars.size) {
                    stopPlayback()
                    returnToStart()
                    return
                }
                activeBar = next
                activeStep = bars[next].stepIndex
                selectedSection = bars[next].step.sectionIndex
                barStartedAt = SystemClock.elapsedRealtime()
                renderAll()
                beginBar()
            }
        }
        playbackRunnable = runnable
        handler.postDelayed(runnable, barDurationMs())
    }

    private fun startProgressTicker() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                if (!playing) return
                binding.barProgress.progress = currentBarProgress()
                handler.postDelayed(this, PROGRESS_TICK_MS)
            }
        }
        progressRunnable = runnable
        handler.post(runnable)
    }

    private fun currentBarProgress(): Int {
        if (!playing) return 0
        val elapsed = SystemClock.elapsedRealtime() - barStartedAt
        val fraction = elapsed.toFloat() / barDurationMs().toFloat()
        return (fraction.coerceIn(0f, 1f) * PROGRESS_MAX).toInt()
    }

    private fun stopPlayback() {
        playing = false
        playbackRunnable?.let { handler.removeCallbacks(it) }
        playbackRunnable = null
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
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
        val guitarSynth = synth ?: return
        arpeggioRunnable?.let { arpeggioHandler.removeCallbacks(it) }
        arpeggioRunnable = null
        val bar = bars().getOrNull(activeBar) ?: return
        val chord = bar.step.chord
        val hits = chord.normalizedHitsPerBar
        val allPositions = binding.improvFretboard.improvArpeggioPositions()
        val rootPosition = lowestRootPosition(chord.rootPc) ?: allPositions.firstOrNull()
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
                guitarSynth.pluck(stringIndex, string.openMidi + fret)
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
        val midis = Improvisation.ascendingChordMidi(
            rootMidi,
            chord.quality.intervals,
            limit
        )
        val result = ArrayList<Pair<Int, Int>>(midis.size)
        for (midi in midis) {
            val position = if (result.isEmpty()) {
                rootPosition
            } else {
                val sameStringFret = midi - rootString.openMidi
                if (sameStringFret in 0..FretboardView.FRET_COUNT) {
                    rootPosition.first to sameStringFret
                } else {
                    SequencePositions.freePosition(midi, tuning, FretboardView.FRET_COUNT)
                        ?.let { it.stringIndex to it.fret }
                }
            } ?: continue
            if (result.isEmpty() || position != result.last()) result.add(position)
        }
        return result
    }

    private fun playGuideNote(chord: ImprovChord) {
        if (!isGuitar) return
        val guitarSynth = synth ?: return
        val (stringIndex, fret) = lowestRootPosition(chord.rootPc) ?: return
        val string = tuning.strings.getOrNull(stringIndex) ?: return
        guitarSynth.pluck(stringIndex, string.openMidi + fret)
        binding.improvFretboard.highlightNote(stringIndex, fret, reveal = false)
    }

    private fun lowestRootPosition(rootPc: Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestMidi = Int.MAX_VALUE
        for (stringIndex in tuning.strings.indices) {
            val openMidi = tuning.strings[stringIndex].openMidi
            val fret = ((rootPc - openMidi) % 12 + 12) % 12
            if (fret > FretboardView.FRET_COUNT) continue
            val midi = openMidi + fret
            if (midi < bestMidi) {
                bestMidi = midi
                best = stringIndex to fret
            }
        }
        return best
    }

    private fun beatDurationMs(): Long = 60_000L / bpm

    private fun barDurationMs(): Long = beatDurationMs() * BEATS_PER_BAR

    private fun stepDurationMs(step: ImprovStep): Long =
        barDurationMs() * step.chord.normalizedBarsPerChord

    private fun bars(): List<ImprovBar> = stepsUseCase.bars(sections)

    private fun steps(): List<ImprovStep> = stepsUseCase(sections)

    private fun firstStepOfSection(index: Int): Int =
        sections.take(index).sumOf { it.chords.size }

    private fun firstBarOfStep(stepIndex: Int): Int {
        if (stepIndex < 0) return -1
        var offset = 0
        steps().forEachIndexed { index, step ->
            if (index == stepIndex) return offset
            offset += step.chord.normalizedBarsPerChord
        }
        return -1
    }

    private fun firstChordRoot(): Int =
        sections.firstOrNull()?.chords?.firstOrNull()?.rootPc ?: 0

    private fun syncScaleRootFromProgression() {
        if (scaleRootAuto) scaleRootPc = firstChordRoot()
    }

    private fun instrumentLabel(): String = when (tuning.strings.size) {
        4 -> "Bajo 4"
        5 -> "Bajo 5"
        else -> "Guitarra"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun saveSections() {
        syncScaleRootFromProgression()
        improvisationRepository.saveSections(instrument, sections)
    }

    private companion object {
        const val DEFAULT_BPM = 80
        const val MIN_BPM = 40
        const val MAX_BPM = 220
        const val BEATS_PER_BAR = 4L
        const val MIN_NOTE_GAP_MS = 30L
        const val PROGRESS_MAX = 1000
        const val PROGRESS_TICK_MS = 50L
        const val METRONOME_VOLUME = 70
        const val METRONOME_TICK_MS = 45
    }
}
