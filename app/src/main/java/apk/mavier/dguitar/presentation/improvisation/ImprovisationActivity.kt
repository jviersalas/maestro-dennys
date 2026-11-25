package apk.mavier.dguitar.presentation.improvisation

import apk.mavier.dguitar.R
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.core.audio.GuitarSynth
import apk.mavier.dguitar.databinding.ActivityImprovisationBinding
import apk.mavier.dguitar.databinding.DialogImprovChordBinding
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.ImprovChord
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.ImprovStep
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.Scales
import apk.mavier.dguitar.domain.model.Tuning
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.usecase.GetImprovisationStepsUseCase
import apk.mavier.dguitar.presentation.theme.ChordColorProvider

class ImprovisationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImprovisationBinding
    private lateinit var synth: GuitarSynth
    private lateinit var tuning: Tuning
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var improvisationRepository: ImprovisationRepository
    private lateinit var stepsUseCase: GetImprovisationStepsUseCase

    private var useFlats = true
    private var sections = mutableListOf<ImprovSection>()
    private var selectedSection = 0
    private var activeStep = -1
    private var bpm = DEFAULT_BPM
    private var playing = false
    private var showDegrees = true

    private val handler = Handler(Looper.getMainLooper())
    private var playbackRunnable: Runnable? = null
    private val arpeggioHandler = Handler(Looper.getMainLooper())
    private var arpeggioRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImprovisationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val container = (application as DependencyProvider)
        settingsRepository = container.settingsRepository
        improvisationRepository = container.improvisationRepository
        stepsUseCase = container.improvisationStepsUseCase

        val config = settingsRepository.load()
        tuning = if (config.tuning.strings.size in 4..5) {
            config.tuning
        } else {
            Tunings.BASS_4
        }
        useFlats = config.useFlats
        bpm = improvisationRepository.loadBpm()
        showDegrees = improvisationRepository.loadShowDegrees()
        sections = improvisationRepository.loadSections().toMutableList()
        if (sections.isEmpty()) sections.add(ImprovSection("Intro"))
        selectedSection = 0
        activeStep = if (sections.first().chords.isNotEmpty()) 0 else -1

        synth = GuitarSynth()
        binding.improvFretboard.onNotePlayed = { stringIndex, midi ->
            synth.pluck(stringIndex, midi)
        }

        binding.backButton.setOnClickListener { finish() }
        binding.playButton.setOnClickListener { togglePlayback() }
        binding.tempoButton.setOnClickListener { showTempoDialog() }
        binding.degreesButton.setOnClickListener {
            showDegrees = !showDegrees
            improvisationRepository.saveShowDegrees(showDegrees)
            renderFretboard()
            renderHeader()
        }

        renderAll()
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
        arpeggioHandler.removeCallbacksAndMessages(null)
    }

    private fun renderAll() {
        renderSectionChips()
        renderChordChips()
        renderFretboard()
        renderHeader()
    }

    private fun renderHeader() {
        val section = sections.getOrNull(selectedSection)
        val active = steps().getOrNull(activeStep)
        binding.improvSubtitle.text = buildString {
            append(instrumentLabel())
            append(" · ")
            append(section?.name ?: "Sin sección")
            if (active != null) {
                append(" · ")
                append(active.chord.label(useFlats))
            }
        }
        binding.degreesButton.isSelected = showDegrees
        binding.degreesButton.contentDescription =
            if (showDegrees) "Ocultar grados del acorde" else "Mostrar grados del acorde"
        binding.tempoButton.text = "Tempo $bpm"
        binding.tempoButton.contentDescription = "Cambiar tempo, $bpm BPM"
        binding.playButton.text = if (playing) "Detener" else "Play"
        binding.playButton.isSelected = playing
        binding.playButton.contentDescription =
            if (playing) "Detener reproducción" else "Reproducir progresión"
    }

    private fun renderSectionChips() {
        binding.sectionList.removeAllViews()
        sections.forEachIndexed { index, section ->
            val chip = createChip(section.name, index == selectedSection)
            chip.contentDescription = "Sección ${section.name}"
            chip.setOnClickListener { selectSection(index) }
            chip.setOnLongClickListener {
                showSectionMenu(chip, index)
                true
            }
            binding.sectionList.addView(chip)
        }
        val addChip = createAddChip("Añadir sección")
        addChip.setOnClickListener { addSection() }
        binding.sectionList.addView(addChip)
    }

    private fun renderChordChips() {
        binding.chordList.removeAllViews()
        val section = sections.getOrNull(selectedSection) ?: return
        val active = steps().getOrNull(activeStep)
        val activeChordIndex = if (active?.sectionIndex == selectedSection) {
            active.chordIndex
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
            binding.chordList.addView(hint)
        }

        section.chords.forEachIndexed { index, chord ->
            val chip = createChip(chordChipText(chord), index == activeChordIndex)
            chip.contentDescription = "Acorde ${chord.label(useFlats)}"
            chip.setOnClickListener {
                stopPlayback()
                selectedSection = sections.indexOf(section)
                activeStep = firstStepOfSection(selectedSection) + index
                renderAll()
            }
            chip.setOnLongClickListener {
                showChordMenu(chip, selectedSection, index)
                true
            }
            binding.chordList.addView(chip)
        }

        val addChip = createAddChip("Añadir acorde")
        addChip.setOnClickListener { addChord() }
        binding.chordList.addView(addChip)
    }

    private fun renderFretboard() {
        val allSteps = steps()
        val active = allSteps.getOrNull(activeStep)?.chord
        val next = allSteps.getOrNull(activeStep + 1)?.chord
        val config = FretboardConfig(
            tuning = tuning,
            mode = ViewMode.IMPROV,
            rootPc = active?.rootPc ?: 0,
            scale = Scales.default,
            chord = active?.quality ?: Chords.MAJOR,
            useFlats = useFlats,
            colorsEnabled = true,
            showDegrees = showDegrees,
            improvActive = active,
            improvNext = next
        )
        binding.improvFretboard.config = config
        binding.improvFretboard.contentDescription = buildString {
            append("Mástil de ")
            append(instrumentLabel().lowercase())
            if (active != null) {
                append(". Acorde actual ")
                append(active.label(useFlats))
            }
        }
        if (active != null) {
            binding.improvFretboard.post { binding.improvFretboard.focusImprovShape() }
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
        activeStep = if (sections[index].chords.isEmpty()) {
            -1
        } else {
            firstStepOfSection(index)
        }
        renderAll()
    }

    private fun addSection() {
        stopPlayback()
        showNameDialog("Nueva sección", "Sección ${sections.size + 1}", "Añadir") { name ->
            sections.add(ImprovSection(name))
            selectedSection = sections.lastIndex
            activeStep = -1
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
        activeStep = if (sections[selectedSection].chords.isEmpty()) {
            -1
        } else {
            firstStepOfSection(selectedSection)
        }
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
            ?: ImprovChord(0, Chords.MAJOR)
        showChordDialog("Añadir acorde", initial, "Añadir") { chord ->
            stopPlayback()
            val current = sections[selectedSection]
            val chords = current.chords + chord
            sections[selectedSection] = current.copy(chords = chords)
            activeStep = firstStepOfSection(selectedSection) + chords.lastIndex
            saveSections()
            renderAll()
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
            activeStep = firstStepOfSection(sectionIndex) + chordIndex
            saveSections()
            renderAll()
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
        activeStep = if (chords.isEmpty()) {
            -1
        } else {
            firstStepOfSection(sectionIndex) + chordIndex.coerceAtMost(chords.lastIndex)
        }
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

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(positive) { _, _ ->
                val root = dialogBinding.rootSpinner.selectedItemPosition.coerceIn(0, 11)
                val quality = Chords.all.getOrNull(
                    dialogBinding.qualitySpinner.selectedItemPosition
                ) ?: Chords.MAJOR
                val hits = hitsOptions.getOrNull(
                    dialogBinding.hitsSpinner.selectedItemPosition
                ) ?: ImprovChord.DEFAULT_HITS_PER_BAR
                onPicked(
                    ImprovChord(
                        rootPc = root,
                        quality = quality,
                        principalOnly = dialogBinding.principalSwitch.isChecked,
                        hitsPerBar = hits
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
                improvisationRepository.saveBpm(bpm)
                renderHeader()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
            Toast.makeText(this, "Añade acordes a la sección", Toast.LENGTH_SHORT).show()
            return
        }
        playing = true
        if (activeStep !in allSteps.indices) activeStep = 0
        renderAll()
        playArpeggio()
        scheduleNext()
    }

    private fun scheduleNext() {
        playbackRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                if (!playing) return
                val allSteps = steps()
                val next = activeStep + 1
                if (next >= allSteps.size) {
                    stopPlayback()
                    returnToStart()
                    return
                }
                activeStep = next
                selectedSection = allSteps[next].sectionIndex
                renderAll()
                playArpeggio()
                handler.postDelayed(this, chordDurationMs())
            }
        }
        playbackRunnable = runnable
        handler.postDelayed(runnable, chordDurationMs())
    }

    private fun stopPlayback() {
        playing = false
        playbackRunnable?.let { handler.removeCallbacks(it) }
        playbackRunnable = null
        arpeggioRunnable?.let { arpeggioHandler.removeCallbacks(it) }
        arpeggioRunnable = null
        if (::binding.isInitialized) renderHeader()
    }

    private fun returnToStart() {
        val allSteps = steps()
        if (allSteps.isEmpty()) {
            activeStep = -1
            selectedSection = 0
        } else {
            activeStep = 0
            selectedSection = allSteps[0].sectionIndex
        }
        renderAll()
    }

    private fun playArpeggio() {
        arpeggioRunnable?.let { arpeggioHandler.removeCallbacks(it) }
        arpeggioRunnable = null
        val chord = steps().getOrNull(activeStep)?.chord ?: return
        val allPositions = binding.improvFretboard.improvArpeggioPositions()
        if (allPositions.isEmpty()) return
        val positions = if (chord.principalOnly) {
            listOf(principalPosition(allPositions, chord.rootPc))
        } else {
            allPositions
        }
        val hits = chord.normalizedHitsPerBar
        val stepMs = (chordDurationMs() / hits).coerceAtLeast(MIN_NOTE_GAP_MS)
        var hit = 0
        val runnable = object : Runnable {
            override fun run() {
                if (!playing) return
                val (stringIndex, fret) = positions[hit % positions.size]
                val string = tuning.strings.getOrNull(stringIndex) ?: return
                synth.pluck(stringIndex, string.openMidi + fret)
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

    private fun principalPosition(
        positions: List<Pair<Int, Int>>,
        rootPc: Int
    ): Pair<Int, Int> {
        val roots = positions.filter { (stringIndex, fret) ->
            val string = tuning.strings.getOrNull(stringIndex) ?: return@filter false
            (string.openMidi + fret) % 12 == rootPc
        }
        return (roots.ifEmpty { positions }).minBy { (stringIndex, fret) ->
            tuning.strings[stringIndex].openMidi + fret
        }
    }

    private fun beatDurationMs(): Long = 60_000L / bpm

    private fun chordDurationMs(): Long = beatDurationMs() * BEATS_PER_CHORD

    private fun steps(): List<ImprovStep> = stepsUseCase(sections)

    private fun firstStepOfSection(index: Int): Int =
        sections.take(index).sumOf { it.chords.size }

    private fun instrumentLabel(): String = when (tuning.strings.size) {
        4 -> "Bajo 4"
        5 -> "Bajo 5"
        else -> "Bajo"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun saveSections() {
        improvisationRepository.saveSections(sections)
    }

    private companion object {
        const val DEFAULT_BPM = 80
        const val MIN_BPM = 40
        const val MAX_BPM = 220
        const val BEATS_PER_CHORD = 4L
        const val MIN_NOTE_GAP_MS = 30L
    }
}
