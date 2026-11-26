package apk.mavier.dguitar.presentation.modulation

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import apk.mavier.dguitar.R
import apk.mavier.dguitar.core.audio.GuitarSynth
import apk.mavier.dguitar.databinding.ActivityModulationBinding
import apk.mavier.dguitar.databinding.ItemModulationStoneBinding
import apk.mavier.dguitar.databinding.ScreenModulationBridgeBinding
import apk.mavier.dguitar.databinding.ScreenModulationPracticeBinding
import apk.mavier.dguitar.databinding.ScreenModulationRouteBinding
import apk.mavier.dguitar.databinding.ScreenModulationWheelBinding
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.model.Chords
import apk.mavier.dguitar.domain.model.ChordTheory
import apk.mavier.dguitar.domain.model.ChromaticVariant
import apk.mavier.dguitar.domain.model.CircleOfFifths
import apk.mavier.dguitar.domain.model.ModulationAvailability
import apk.mavier.dguitar.domain.model.ModulationBridge
import apk.mavier.dguitar.domain.model.ModulationChord
import apk.mavier.dguitar.domain.model.ModulationSignal
import apk.mavier.dguitar.domain.model.ModulationStep
import apk.mavier.dguitar.domain.model.ModulationType
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.SoundKind
import apk.mavier.dguitar.domain.model.Tuning
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.domain.repository.SoundFontRepository
import apk.mavier.dguitar.domain.usecase.BuildModulationUseCase
import apk.mavier.dguitar.presentation.common.HandlerScheduler
import apk.mavier.dguitar.presentation.common.SoundFontCatalog
import apk.mavier.dguitar.presentation.common.SoundFontFamily
import apk.mavier.dguitar.presentation.common.instrumentLabel
import apk.mavier.dguitar.presentation.modulation.ModulationAudioPlayer.PlayMode
import apk.mavier.dguitar.presentation.ui.applySystemBarInsets
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.roundToInt

class ModulationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModulationBinding
    private lateinit var wheelScreen: ScreenModulationWheelBinding
    private lateinit var routeScreen: ScreenModulationRouteBinding
    private lateinit var bridgeScreen: ScreenModulationBridgeBinding
    private lateinit var practiceScreen: ScreenModulationPracticeBinding

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var soundFontRepository: SoundFontRepository
    private lateinit var modulationUseCase: BuildModulationUseCase
    private lateinit var synth: GuitarSynth
    private lateinit var audioPlayer: ModulationAudioPlayer
    private lateinit var playbackController: ModulationPlaybackController

    private val handler = Handler(Looper.getMainLooper())
    private val scheduler = HandlerScheduler(handler)
    private val density by lazy { resources.displayMetrics.density }

    private var tuning: Tuning = Tunings.STANDARD
    private var useFlats = false
    private var originPc = 0
    private var destinationPc = 7
    private var currentScreen = 0
    private var activeSlot = Slot.ORIGIN
    private var routeType = ModulationType.PIVOT
    private var chromaticVariant = ChromaticVariant.ORIGIN_IMPULSE
    private var bpm = 90
    private var bridge: ModulationBridge? = null
    private var selectedStepIndex: Int? = null
    private var challengeSolved = false
    private var challengeFeedback = ""
    private var playbackPurpose: PlaybackPurpose? = null
    private var playbackChords: List<ModulationChord> = emptyList()
    private var toneGenerator: ToneGenerator? = null

    private enum class Slot { ORIGIN, DESTINATION }

    private enum class PlaybackPurpose { BRIDGE, ORIGIN, DESTINATION, FULL }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModulationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        val container = application as DependencyProvider
        settingsRepository = container.settingsRepository
        soundFontRepository = container.soundFontRepository
        modulationUseCase = container.modulationUseCase

        val config = settingsRepository.load()
        tuning = config.tuning
        useFlats = config.useFlats
        originPc = normalize(config.rootPc)
        destinationPc = normalize(originPc + 7)

        savedInstanceState?.let { saved ->
            originPc = normalize(saved.getInt(STATE_ORIGIN, originPc))
            destinationPc = normalize(saved.getInt(STATE_DESTINATION, destinationPc))
            currentScreen = saved.getInt(STATE_SCREEN, 0).coerceIn(0, 3)
            bpm = saved.getInt(STATE_BPM, 90)
            challengeSolved = saved.getBoolean(STATE_CHALLENGE, false)
            saved.getString(STATE_TYPE)?.let { name ->
                runCatching { ModulationType.valueOf(name) }.getOrNull()?.let { routeType = it }
            }
            saved.getString(STATE_VARIANT)?.let { name ->
                runCatching { ChromaticVariant.valueOf(name) }.getOrNull()?.let {
                    chromaticVariant = it
                }
            }
        }

        synth = GuitarSynth()
        synth.bassMode = tuning.strings.size < 6
        audioPlayer = ModulationAudioPlayer(synth, scheduler, tuning, useFlats)
        playbackController = ModulationPlaybackController(scheduler)
        playbackController.listener = playbackListener()

        toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, METRONOME_VOLUME)
        }.getOrNull()

        setupScreens()
        setupListeners()
        loadSoundProfile()
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
        audioPlayer.stop()
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_ORIGIN, originPc)
        outState.putInt(STATE_DESTINATION, destinationPc)
        outState.putInt(STATE_SCREEN, currentScreen)
        outState.putInt(STATE_BPM, bpm)
        outState.putBoolean(STATE_CHALLENGE, challengeSolved)
        outState.putString(STATE_TYPE, routeType.name)
        outState.putString(STATE_VARIANT, chromaticVariant.name)
    }

    private fun setupScreens() {
        wheelScreen = ScreenModulationWheelBinding.inflate(
            layoutInflater,
            binding.screenFlipper,
            true
        )
        routeScreen = ScreenModulationRouteBinding.inflate(
            layoutInflater,
            binding.screenFlipper,
            true
        )
        bridgeScreen = ScreenModulationBridgeBinding.inflate(
            layoutInflater,
            binding.screenFlipper,
            true
        )
        practiceScreen = ScreenModulationPracticeBinding.inflate(
            layoutInflater,
            binding.screenFlipper,
            true
        )
        binding.screenFlipper.displayedChild = currentScreen
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener { finish() }
        binding.keyButton.setOnClickListener { showKeyDialog() }
        binding.accidentalButton.setOnClickListener { toggleAccidentals() }
        binding.stepWheel.setOnClickListener { goToStep(0) }
        binding.stepRoute.setOnClickListener { goToStep(1) }
        binding.stepBridge.setOnClickListener { goToStep(2) }
        binding.stepPractice.setOnClickListener { goToStep(3) }
        binding.previousButton.setOnClickListener {
            if (currentScreen > 0) goToStep(currentScreen - 1)
        }
        binding.nextButton.setOnClickListener {
            if (currentScreen == 3) {
                restartModule()
            } else {
                goToStep(currentScreen + 1)
            }
        }

        wheelScreen.wheelView.onKeySelected = { index ->
            selectOrigin(circleKeyPc(index), play = true)
        }
        wheelScreen.wheelView.onKeyLongPressed = { index ->
            selectOrigin(circleKeyPc(index), play = false)
            animateWheelNotes()
            audioPlayer.stop()
            audioPlayer.playChord(circleKeyPc(index), Chords.MAJOR, PlayMode.ARPEGGIO)
        }
        wheelScreen.chooseKeyButton.setOnClickListener { showKeyDialog() }

        routeScreen.routeWheel.compact = true
        routeScreen.routeWheel.onKeySelected = { index ->
            assignKey(circleKeyPc(index), play = true)
        }
        routeScreen.routeWheel.onKeyLongPressed = { index ->
            routeScreen.routeWheel.stopPulseAnimation()
            startKeyDrag(circleKeyPc(index))
        }
        routeScreen.originCard.setOnClickListener { setActiveSlot(Slot.ORIGIN) }
        routeScreen.destinationCard.setOnClickListener { setActiveSlot(Slot.DESTINATION) }
        routeScreen.swapButton.setOnClickListener { swapKeys() }
        setupDropTarget(routeScreen.originCard, Slot.ORIGIN)
        setupDropTarget(routeScreen.destinationCard, Slot.DESTINATION)

        bridgeScreen.playBridgeButton.setOnClickListener { toggleBridgePlayback() }

        practiceScreen.playOriginButton.setOnClickListener {
            togglePlayback(
                PlaybackPurpose.ORIGIN,
                modulationUseCase.cadence(originPc)
            )
        }
        practiceScreen.playDestinationButton.setOnClickListener {
            togglePlayback(
                PlaybackPurpose.DESTINATION,
                modulationUseCase.cadence(destinationPc)
            )
        }
        practiceScreen.playFullButton.setOnClickListener {
            bridge?.let { togglePlayback(PlaybackPurpose.FULL, it.steps.map { step -> step.chord }) }
        }
        practiceScreen.tempoSlow.setOnClickListener { setTempo(60) }
        practiceScreen.tempoMedium.setOnClickListener { setTempo(90) }
        practiceScreen.tempoFast.setOnClickListener { setTempo(120) }
        practiceScreen.newChallengeButton.setOnClickListener { newChallenge() }
    }

    private fun playbackListener(): ModulationPlaybackController.Listener =
        object : ModulationPlaybackController.Listener {
            override fun onChordStarted(index: Int) {
                val chord = playbackChords.getOrNull(index) ?: return
                val purpose = playbackPurpose
                val accent = when (purpose) {
                    PlaybackPurpose.BRIDGE, PlaybackPurpose.FULL ->
                        bridge?.changeIndex == index
                    else -> false
                }
                audioPlayer.playChord(chord, PlayMode.STRUM, accent = accent)
                when (purpose) {
                    PlaybackPurpose.BRIDGE -> {
                        highlightBridgeStone(index)
                        updatePlayhead(index)
                    }

                    PlaybackPurpose.ORIGIN,
                    PlaybackPurpose.DESTINATION,
                    PlaybackPurpose.FULL,
                    null -> practiceScreen.pulseView.pulse(index)
                }
            }

            override fun onBeat(chordIndex: Int, beatInChord: Int, accent: Boolean) {
                runCatching {
                    toneGenerator?.startTone(
                        if (accent) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2,
                        METRONOME_TICK_MS
                    )
                }
            }

            override fun onFinished() {
                stopPlayback()
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

    private fun renderAll() {
        updateHeader()
        updateStepButtons()
        renderWheel()
        renderRoute()
        renderBridge()
        renderPractice()
        updatePlayButtons()
    }

    private fun updateHeader() {
        binding.subtitle.text = getString(
            R.string.modulation_subtitle,
            instrumentLabel(tuning.strings.size)
        )
        binding.keyButton.text = keyName(originPc)
        binding.accidentalButton.text = getString(
            if (useFlats) R.string.circle_flat_symbol else R.string.circle_sharp_symbol
        )
        binding.accidentalButton.isSelected = useFlats
        binding.accidentalButton.contentDescription = getString(
            if (useFlats) R.string.circle_show_sharps else R.string.circle_show_flats
        )
    }

    private fun updateStepButtons() {
        val steps = listOf(
            binding.stepWheel to R.string.modulation_step_wheel,
            binding.stepRoute to R.string.modulation_step_route,
            binding.stepBridge to R.string.modulation_step_bridge,
            binding.stepPractice to R.string.modulation_step_practice
        )
        steps.forEachIndexed { index, (view, labelRes) ->
            view.isSelected = index == currentScreen
            view.contentDescription = getString(
                R.string.modulation_screen_description,
                index + 1,
                getString(labelRes)
            )
        }
        binding.previousButton.isEnabled = currentScreen > 0
        binding.previousButton.alpha = if (currentScreen > 0) 1f else 0.4f
        binding.nextButton.text = getString(
            if (currentScreen == 3) R.string.modulation_restart else R.string.modulation_next
        )
        val sameKey = originPc == destinationPc
        binding.nextButton.isEnabled = !(currentScreen == 1 && sameKey)
        binding.nextButton.alpha = if (binding.nextButton.isEnabled) 1f else 0.4f
    }

    private fun renderWheel() {
        val index = circleIndex(originPc)
        wheelScreen.wheelView.selectedIndex = index
        wheelScreen.wheelView.useFlats = useFlats
        val key = CircleOfFifths.byIndex(index)
        wheelScreen.selectedKeyTitle.text = getString(
            R.string.circle_major_short,
            key.majorName(useFlats)
        )
        wheelScreen.selectedKeyDetail.text = getString(
            R.string.modulation_key_relation,
            key.majorName(useFlats),
            key.minorName(useFlats)
        )
        wheelScreen.wheelNotes.removeAllViews()
        val semitones = ChordTheory.tones(Chords.MAJOR).map { it.semitone } + 12
        for (semitone in semitones) {
            val note = ChordTheory.noteName(originPc, Chords.MAJOR, useFlats, semitone)
            val chip = layoutInflater.inflate(
                R.layout.item_circle_note,
                wheelScreen.wheelNotes,
                false
            ) as TextView
            chip.text = note
            chip.contentDescription = note
            wheelScreen.wheelNotes.addView(chip)
        }
    }

    private fun renderRoute() {
        routeScreen.routeWheel.selectedIndex = circleIndex(
            if (activeSlot == Slot.ORIGIN) originPc else destinationPc
        )
        routeScreen.routeWheel.useFlats = useFlats
        routeScreen.originCard.isSelected = activeSlot == Slot.ORIGIN
        routeScreen.destinationCard.isSelected = activeSlot == Slot.DESTINATION

        routeScreen.originValue.text = getString(
            R.string.modulation_route_key_value,
            keyName(originPc)
        )
        routeScreen.originDetail.text = getString(
            R.string.modulation_key_relation,
            keyName(originPc),
            minorName(originPc)
        )
        routeScreen.destinationValue.text = getString(
            R.string.modulation_route_key_value,
            keyName(destinationPc)
        )
        routeScreen.destinationDetail.text = getString(
            R.string.modulation_key_relation,
            keyName(destinationPc),
            minorName(destinationPc)
        )

        val sameKey = originPc == destinationPc
        val distance = modulationUseCase.distance(originPc, destinationPc)
        val signal = signalFor(distance)
        val signalColor = signalColor(signal)
        if (sameKey) {
            routeScreen.distanceText.text = getString(R.string.modulation_distance_same)
            routeScreen.distanceText.setTextColor(getColor(R.color.danger))
        } else {
            routeScreen.distanceText.text = distanceLabel(distance)
            routeScreen.distanceText.setTextColor(signalColor)
        }
        renderConnector(signalColor)
        routeScreen.originCard.contentDescription = getString(
            R.string.modulation_origin
        ) + ": " + getString(R.string.modulation_route_key_value, keyName(originPc))
        routeScreen.destinationCard.contentDescription = getString(
            R.string.modulation_destination
        ) + ": " + getString(R.string.modulation_route_key_value, keyName(destinationPc))
        routeScreen.routeWheel.contentDescription = getString(
            R.string.modulation_route_description,
            keyName(originPc),
            keyName(destinationPc),
            if (sameKey) getString(R.string.modulation_distance_same) else distanceLabel(distance)
        )
    }

    private fun renderBridge() {
        val availability = modulationUseCase.availability(originPc, destinationPc)
        if (routeType == ModulationType.PIVOT && !availability.pivotAvailable) {
            routeType = ModulationType.DIRECT
        }
        val current = modulationUseCase.bridge(
            originPc,
            destinationPc,
            routeType,
            if (routeType == ModulationType.CHROMATIC) chromaticVariant else null
        )
        bridge = current
        if (selectedStepIndex != null && selectedStepIndex !in current.steps.indices) {
            selectedStepIndex = null
        }

        bridgeScreen.bridgeTitle.text = getString(R.string.modulation_bridge_title)
        bridgeScreen.bridgeSubtitle.text = getString(
            R.string.modulation_bridge_subtitle,
            keyName(originPc),
            keyName(destinationPc)
        )

        renderBridgeTypes(availability)
        renderChromaticVariants(current)

        bridgeScreen.bridgeRow.removeAllViews()
        bridgeScreen.bridgeRow.contentDescription =
            getString(R.string.modulation_bridge_hint)
        for ((index, step) in current.steps.withIndex()) {
            val stone = ItemModulationStoneBinding.inflate(
                layoutInflater,
                bridgeScreen.bridgeRow,
                false
            )
            val params = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginEnd = dp(8)
            }
            stone.root.layoutParams = params
            stone.stoneChord.text = step.label(useFlats)
            stone.stoneRole.text = roleLabel(step, current)
            stone.root.background = stoneBackground(step, current, quiz = false)
            stone.stoneChord.setTextColor(getColor(R.color.modulation_stone_text))
            stone.stoneRole.setTextColor(Color.WHITE)
            stone.root.alpha = if (step.isPivot) 1f else 0.96f
            stone.root.contentDescription = getString(
                R.string.modulation_info_chord,
                step.label(useFlats),
                roleLabel(step, current)
            )
            stone.root.setOnClickListener {
                stopPlayback()
                audioPlayer.stop()
                audioPlayer.playChord(step.chord, PlayMode.STRUM)
                selectedStepIndex = index
                showBridgeInfo(step, current)
                animateStone(stone.root)
            }
            bridgeScreen.bridgeRow.addView(stone.root)
        }

        val infoStep = selectedStepIndex
            ?.let { current.steps.getOrNull(it) }
            ?: current.steps.firstOrNull { it.isPivot }
            ?: current.changeStep
        if (infoStep != null) {
            showBridgeInfo(infoStep, current, animate = false)
        } else {
            bridgeScreen.infoCard.visibility = View.GONE
        }
        bridgeScreen.playheadDot.alpha = 0f
    }

    private fun renderBridgeTypes(availability: ModulationAvailability) {
        bridgeScreen.typeRow.removeAllViews()
        val types = listOf(
            ModulationType.DIRECT to R.string.modulation_type_direct,
            ModulationType.PIVOT to R.string.modulation_type_pivot,
            ModulationType.CHROMATIC to R.string.modulation_type_chromatic
        )
        for ((type, labelRes) in types) {
            val enabled = type != ModulationType.PIVOT || availability.pivotAvailable
            val chip = createChip(
                text = getString(labelRes),
                selected = routeType == type,
                enabled = enabled
            )
            if (!enabled) {
                chip.contentDescription = getString(R.string.modulation_pivot_unavailable)
            } else {
                chip.setOnClickListener {
                    routeType = type
                    selectedStepIndex = null
                    challengeSolved = false
                    challengeFeedback = ""
                    stopPlayback()
                    renderAll()
                }
            }
            bridgeScreen.typeRow.addView(chip)
        }
    }

    private fun renderChromaticVariants(current: ModulationBridge) {
        bridgeScreen.chromaticScroll.visibility = View.GONE
        if (current.type != ModulationType.CHROMATIC) return

        val originV7 = keyName(normalize(originPc + 7)) + "7"
        val destinationV7 = keyName(normalize(destinationPc + 7)) + "7"
        val label = when (chromaticVariant) {
            ChromaticVariant.ORIGIN_IMPULSE -> getString(
                R.string.modulation_chromatic_origin_impulse,
                keyName(originPc),
                originV7
            )

            ChromaticVariant.DESTINATION_DOMINANT -> getString(
                R.string.modulation_chromatic_destination_dominant,
                keyName(destinationPc),
                destinationV7
            )
        }
        val chip = createChip(
            text = getString(R.string.modulation_variant_label, label),
            selected = true,
            enabled = true
        )
        chip.setOnClickListener { showChromaticVariantDialog() }
        bridgeScreen.typeRow.addView(chip)
    }

    private fun showChromaticVariantDialog() {
        val originV7 = keyName(normalize(originPc + 7)) + "7"
        val destinationV7 = keyName(normalize(destinationPc + 7)) + "7"
        val options = arrayOf(
            getString(
                R.string.modulation_chromatic_origin_impulse,
                keyName(originPc),
                originV7
            ),
            getString(
                R.string.modulation_chromatic_destination_dominant,
                keyName(destinationPc),
                destinationV7
            )
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.modulation_variant_dialog_title)
            .setSingleChoiceItems(options, chromaticVariant.ordinal) { dialog, which ->
                dialog.dismiss()
                chromaticVariant = if (which == 0) {
                    ChromaticVariant.ORIGIN_IMPULSE
                } else {
                    ChromaticVariant.DESTINATION_DOMINANT
                }
                selectedStepIndex = null
                challengeSolved = false
                challengeFeedback = ""
                stopPlayback()
                renderAll()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun renderPractice() {
        val current = bridge ?: return
        practiceScreen.practiceSubtitle.text = getString(
            R.string.modulation_practice_subtitle,
            keyName(originPc),
            keyName(destinationPc)
        )
        practiceScreen.challengeTitle.contentDescription =
            getString(R.string.modulation_challenge_hint)
        practiceScreen.playFullButton.contentDescription =
            getString(R.string.modulation_practice_hint)
        practiceScreen.challengeFeedback.text = challengeFeedback
        practiceScreen.challengeFeedback.setTextColor(
            if (challengeSolved) getColor(R.color.success) else getColor(R.color.text_secondary)
        )
        val colors = current.steps.map { stepColor(it, current) }
        practiceScreen.pulseView.setBars(colors, current.changeIndex)
        renderChallenge(current)
        updatePlayButtons()
    }

    private fun renderChallenge(current: ModulationBridge) {
        practiceScreen.quizRow.removeAllViews()
        for ((index, step) in current.steps.withIndex()) {
            val stone = ItemModulationStoneBinding.inflate(
                layoutInflater,
                practiceScreen.quizRow,
                false
            )
            val params = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginEnd = dp(6)
            }
            stone.root.layoutParams = params
            stone.stoneChord.text = step.label(useFlats)
            stone.stoneRole.text = if (challengeSolved) roleLabel(step, current) else ""
            stone.root.background = if (challengeSolved) {
                stoneBackground(step, current, quiz = false)
            } else {
                stoneBackground(step, current, quiz = true)
            }
            stone.stoneChord.setTextColor(
                if (challengeSolved) getColor(R.color.modulation_stone_text) else getColor(
                    R.color.text_primary
                )
            )
            stone.stoneRole.setTextColor(
                if (challengeSolved) Color.WHITE else getColor(R.color.text_muted)
            )
            stone.root.contentDescription = getString(
                R.string.modulation_info_chord,
                step.label(useFlats),
                if (challengeSolved) roleLabel(step, current) else getString(
                    R.string.modulation_challenge_title
                )
            )
            stone.root.setOnClickListener { checkChallenge(index, step, current) }
            practiceScreen.quizRow.addView(stone.root)
        }
    }

    private fun checkChallenge(index: Int, step: ModulationStep, current: ModulationBridge) {
        if (challengeSolved) return
        if (index == current.changeIndex) {
            challengeSolved = true
            challengeFeedback = getString(
                R.string.modulation_challenge_correct,
                step.label(useFlats)
            )
            practiceScreen.challengeFeedback.text = challengeFeedback
            practiceScreen.challengeFeedback.setTextColor(getColor(R.color.success))
            practiceScreen.pulseView.pulse(index)
            audioPlayer.stop()
            audioPlayer.playChord(step.chord, PlayMode.STRUM, accent = true)
            renderChallenge(current)
        } else {
            challengeFeedback = getString(
                R.string.modulation_challenge_wrong,
                keyName(originPc)
            )
            practiceScreen.challengeFeedback.text = challengeFeedback
            practiceScreen.challengeFeedback.setTextColor(getColor(R.color.danger))
            practiceScreen.quizRow.getChildAt(index)?.let { shake(it) }
        }
    }

    private fun newChallenge() {
        val options = (0..11).filter { it != originPc }
        destinationPc = options.random()
        if (routeType == ModulationType.PIVOT &&
            !modulationUseCase.availability(originPc, destinationPc).pivotAvailable
        ) {
            routeType = ModulationType.DIRECT
        }
        challengeSolved = false
        challengeFeedback = ""
        selectedStepIndex = null
        stopPlayback()
        renderAll()
    }

    private fun selectOrigin(pc: Int, play: Boolean) {
        val normalized = normalize(pc)
        originPc = normalized
        if (destinationPc == normalized) {
            destinationPc = normalize(normalized + 7)
        }
        if (play) {
            audioPlayer.stop()
            audioPlayer.playChord(normalized, Chords.MAJOR, PlayMode.ARPEGGIO)
        }
        challengeSolved = false
        challengeFeedback = ""
        selectedStepIndex = null
        renderAll()
        practiceScreen.pulseView.reset()
    }

    private fun assignKey(pc: Int, play: Boolean) {
        if (activeSlot == Slot.ORIGIN) {
            selectOrigin(pc, play)
        } else {
            selectDestination(pc, play)
        }
    }

    private fun selectDestination(pc: Int, play: Boolean) {
        val normalized = normalize(pc)
        if (normalized == originPc) {
            Toast.makeText(this, R.string.modulation_same_key, Toast.LENGTH_SHORT).show()
            return
        }
        destinationPc = normalized
        if (play) {
            audioPlayer.stop()
            audioPlayer.playChord(normalized, Chords.MAJOR, PlayMode.ARPEGGIO)
        }
        challengeSolved = false
        challengeFeedback = ""
        selectedStepIndex = null
        renderAll()
        practiceScreen.pulseView.reset()
    }

    private fun setActiveSlot(slot: Slot) {
        activeSlot = slot
        renderRoute()
    }

    private fun swapKeys() {
        val origin = originPc
        originPc = destinationPc
        destinationPc = origin
        activeSlot = Slot.ORIGIN
        challengeSolved = false
        challengeFeedback = ""
        selectedStepIndex = null
        stopPlayback()
        renderAll()
    }

    private fun setTempo(value: Int) {
        bpm = value
        if (playbackController.isRunning) {
            stopPlayback()
        }
        updatePlayButtons()
    }

    private fun showKeyDialog() {
        val keys = CircleOfFifths.keys
        val options = keys.map { key ->
            getString(
                R.string.modulation_key_option,
                key.majorName(useFlats),
                key.minorName(useFlats)
            )
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.modulation_key_dialog_title)
            .setSingleChoiceItems(options, circleIndex(originPc)) { dialog, which ->
                dialog.dismiss()
                selectOrigin(circleKeyPc(which), play = true)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun toggleAccidentals() {
        useFlats = !useFlats
        audioPlayer.useFlats = useFlats
        renderAll()
    }

    private fun toggleBridgePlayback() {
        val current = bridge ?: return
        if (playbackController.isRunning && playbackPurpose == PlaybackPurpose.BRIDGE) {
            stopPlayback()
        } else {
            startPlayback(
                current.steps.map { it.chord },
                PlaybackPurpose.BRIDGE
            )
        }
    }

    private fun togglePlayback(purpose: PlaybackPurpose, chords: List<ModulationChord>) {
        if (playbackController.isRunning && playbackPurpose == purpose) {
            stopPlayback()
        } else {
            startPlayback(chords, purpose)
        }
    }

    private fun startPlayback(chords: List<ModulationChord>, purpose: PlaybackPurpose) {
        stopPlayback()
        if (chords.isEmpty()) return
        playbackChords = chords
        playbackPurpose = purpose
        val current = bridge
        val colors = when (purpose) {
            PlaybackPurpose.ORIGIN -> List(chords.size) { getColor(R.color.modulation_origin) }
            PlaybackPurpose.DESTINATION ->
                List(chords.size) { getColor(R.color.modulation_destination) }
            PlaybackPurpose.FULL, PlaybackPurpose.BRIDGE -> current?.steps?.map {
                stepColor(it, current)
            } ?: List(chords.size) { getColor(R.color.accent) }
        }
        practiceScreen.pulseView.setBars(
            colors,
            if (purpose == PlaybackPurpose.FULL) current?.changeIndex ?: -1 else -1
        )
        playbackController.start(chords.size, bpm, BEATS_PER_CHORD)
        updatePlayButtons()
    }

    private fun stopPlayback() {
        if (::playbackController.isInitialized) playbackController.stop()
        if (::audioPlayer.isInitialized) audioPlayer.stop()
        runCatching { toneGenerator?.stopTone() }
        playbackPurpose = null
        playbackChords = emptyList()
        if (::practiceScreen.isInitialized) practiceScreen.pulseView.reset()
        if (::bridgeScreen.isInitialized) {
            bridgeScreen.playheadDot.animate().alpha(0f).setDuration(100).start()
            for (index in 0 until bridgeScreen.bridgeRow.childCount) {
                val child = bridgeScreen.bridgeRow.getChildAt(index)
                child.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                child.alpha = if (bridge?.steps?.getOrNull(index)?.isPivot == true) 1f else 0.96f
            }
        }
        updatePlayButtons()
    }

    private fun updatePlayButtons() {
        if (!::bridgeScreen.isInitialized) return
        val playing = playbackController.isRunning
        val bridgePlaying = playing && playbackPurpose == PlaybackPurpose.BRIDGE
        bridgeScreen.playBridgeButton.text = getString(
            if (bridgePlaying) R.string.modulation_bridge_stop else R.string.modulation_bridge_play
        )
        bridgeScreen.playBridgeButton.isSelected = bridgePlaying

        practiceScreen.playOriginButton.text = getString(
            if (playing && playbackPurpose == PlaybackPurpose.ORIGIN) {
                R.string.stop
            } else {
                R.string.modulation_play_origin
            }
        )
        practiceScreen.playDestinationButton.text = getString(
            if (playing && playbackPurpose == PlaybackPurpose.DESTINATION) {
                R.string.stop
            } else {
                R.string.modulation_play_destination
            }
        )
        practiceScreen.playFullButton.text = getString(
            if (playing && playbackPurpose == PlaybackPurpose.FULL) {
                R.string.stop
            } else {
                R.string.modulation_play_full
            }
        )
        practiceScreen.playOriginButton.isSelected =
            playing && playbackPurpose == PlaybackPurpose.ORIGIN
        practiceScreen.playDestinationButton.isSelected =
            playing && playbackPurpose == PlaybackPurpose.DESTINATION
        practiceScreen.playFullButton.isSelected =
            playing && playbackPurpose == PlaybackPurpose.FULL

        practiceScreen.tempoSlow.isSelected = bpm == 60
        practiceScreen.tempoMedium.isSelected = bpm == 90
        practiceScreen.tempoFast.isSelected = bpm == 120
        practiceScreen.tempoSlow.contentDescription =
            getString(R.string.modulation_tempo_slow)
        practiceScreen.tempoMedium.contentDescription =
            getString(R.string.modulation_tempo_medium)
        practiceScreen.tempoFast.contentDescription =
            getString(R.string.modulation_tempo_fast)
    }

    private fun highlightBridgeStone(index: Int) {
        for (position in 0 until bridgeScreen.bridgeRow.childCount) {
            val child = bridgeScreen.bridgeRow.getChildAt(position)
            val active = position == index
            child.animate()
                .scaleX(if (active) 1.06f else 1f)
                .scaleY(if (active) 1.06f else 1f)
                .setDuration(120)
                .start()
            child.alpha = if (active) 1f else 0.72f
        }
    }

    private fun updatePlayhead(index: Int) {
        bridgeScreen.playheadDot.post {
            val stone = bridgeScreen.bridgeRow.getChildAt(index) ?: return@post
            val dot = bridgeScreen.playheadDot
            val targetX = stone.x + stone.width / 2f - dot.width / 2f
            dot.animate()
                .x(targetX)
                .alpha(1f)
                .setDuration(140)
                .start()
        }
    }

    private fun showBridgeInfo(
        step: ModulationStep,
        current: ModulationBridge,
        animate: Boolean = true
    ) {
        val text = when {
            step.isPivot -> getString(
                R.string.modulation_info_pivot,
                step.originRoman.orEmpty(),
                keyName(originPc),
                step.destinationRoman.orEmpty(),
                keyName(destinationPc)
            )

            step === current.steps.lastOrNull() -> getString(
                R.string.modulation_info_destination_tonic,
                keyName(destinationPc)
            )

            step.isChangePoint -> getString(
                R.string.modulation_info_change,
                step.label(useFlats),
                keyName(originPc)
            )

            step.destinationRoman == "V7" -> getString(
                R.string.modulation_info_destination_dominant,
                step.destinationRoman,
                keyName(destinationPc)
            )

            step.originRoman == "I" -> getString(
                R.string.modulation_info_origin_tonic,
                keyName(originPc)
            )

            else -> getString(
                R.string.modulation_info_chord,
                step.label(useFlats),
                roleLabel(step, current)
            )
        }
        bridgeScreen.infoCard.text = text
        bridgeScreen.infoCard.visibility = View.VISIBLE
        if (animate) {
            bridgeScreen.infoCard.alpha = 0f
            bridgeScreen.infoCard.translationY = dpf(6f)
            bridgeScreen.infoCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160)
                .start()
        }
    }

    private fun roleLabel(step: ModulationStep, current: ModulationBridge): String = when {
        step === current.steps.lastOrNull() && step.isChangePoint ->
            getString(R.string.modulation_role_destination_tonic_change)
        step === current.steps.lastOrNull() -> getString(R.string.modulation_role_destination_tonic)
        step.isPivot -> getString(R.string.modulation_role_pivot)
        step.isChangePoint && step.destinationRoman == "V7" ->
            getString(R.string.modulation_role_dominant_change)
        step.isChangePoint -> getString(R.string.modulation_role_change)
        step.destinationRoman == "V7" -> getString(R.string.modulation_role_destination_dominant)
        step.originRoman == "I" -> getString(R.string.modulation_role_origin_tonic)
        step.originRoman == "V7" -> getString(R.string.modulation_role_origin_dominant)
        else -> getString(R.string.modulation_role_bridge)
    }

    private fun stepColor(step: ModulationStep, current: ModulationBridge): Int = when {
        step.isPivot -> getColor(R.color.circle_active)
        step === current.steps.lastOrNull() -> getColor(R.color.modulation_destination)
        step.originRoman == "I" -> getColor(R.color.modulation_origin)
        step.belongsToDestination && !step.belongsToOrigin ->
            getColor(R.color.modulation_destination)

        step.belongsToOrigin -> getColor(R.color.modulation_origin)
        else -> getColor(R.color.surface_elevated)
    }

    private fun stoneBackground(
        step: ModulationStep,
        current: ModulationBridge,
        quiz: Boolean
    ): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = dpf(14f)
        drawable.setStroke(dp(1), getColor(R.color.chip_border))

        if (quiz) {
            drawable.setColor(getColor(R.color.surface_elevated))
            return drawable
        }

        when {
            step.isPivot -> {
                drawable.orientation = GradientDrawable.Orientation.LEFT_RIGHT
                drawable.colors = intArrayOf(
                    getColor(R.color.modulation_pivot_start),
                    getColor(R.color.modulation_pivot_end)
                )
                drawable.setStroke(dp(2), Color.WHITE)
            }

            step === current.steps.lastOrNull() ->
                drawable.setColor(getColor(R.color.modulation_destination))

            step.belongsToDestination && !step.belongsToOrigin ->
                drawable.setColor(getColor(R.color.modulation_destination))

            step.originRoman == "I" ->
                drawable.setColor(getColor(R.color.modulation_origin))

            step.belongsToOrigin ->
                drawable.setColor(getColor(R.color.modulation_origin))

            else -> drawable.setColor(getColor(R.color.surface_elevated))
        }
        return drawable
    }

    private fun renderConnector(color: Int) {
        val container = routeScreen.routeConnector
        container.removeAllViews()
        repeat(5) {
            val dot = View(this)
            val size = dp(4)
            val params = LinearLayout.LayoutParams(size, size).apply {
                topMargin = dp(2)
                bottomMargin = dp(2)
            }
            dot.layoutParams = params
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            container.addView(dot)
        }
    }

    private fun setupDropTarget(target: View, slot: Slot) {
        target.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED ->
                    event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true

                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.isSelected = true
                    true
                }

                DragEvent.ACTION_DRAG_EXITED -> {
                    view.isSelected = activeSlot == slot
                    true
                }

                DragEvent.ACTION_DROP -> {
                    view.isSelected = activeSlot == slot
                    val pc = event.clipData?.getItemAt(0)?.text?.toString()?.toIntOrNull()
                    if (pc != null) {
                        setActiveSlot(slot)
                        assignKey(pc, play = true)
                    }
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    view.isSelected = activeSlot == slot
                    true
                }

                else -> true
            }
        }
    }

    private fun startKeyDrag(pc: Int) {
        val token = TextView(this).apply {
            text = keyName(pc)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(getColor(R.color.accent))
                setStroke(dp(2), Color.WHITE)
            }
        }
        val size = dp(72)
        val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        token.measure(spec, spec)
        token.layout(0, 0, size, size)

        val clip = ClipData.newPlainText(DRAG_LABEL, pc.toString())
        routeScreen.routeWheel.startDragAndDrop(
            clip,
            View.DragShadowBuilder(token),
            null,
            0
        )
    }

    private fun createChip(text: String, selected: Boolean, enabled: Boolean): TextView {
        val chip = layoutInflater.inflate(
            R.layout.item_circle_note,
            binding.root,
            false
        ) as TextView
        chip.text = text
        chip.isSelected = selected
        chip.isEnabled = enabled
        chip.alpha = if (enabled) 1f else 0.4f
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        chip.minHeight = dp(38)
        chip.setPadding(dp(12), dp(8), dp(12), dp(8))
        return chip
    }

    private fun animateWheelNotes() {
        val notes = wheelScreen.wheelNotes
        for (index in 0 until notes.childCount) {
            val child = notes.getChildAt(index)
            child.alpha = 0f
            child.animate()
                .alpha(1f)
                .setStartDelay(index * 45L)
                .setDuration(180)
                .start()
        }
    }

    private fun animateStone(view: View) {
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(110)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(110)
                    .start()
            }
            .start()
    }

    private fun shake(view: View) {
        ObjectAnimator.ofFloat(
            view,
            View.TRANSLATION_X,
            0f,
            dpf(10f),
            dpf(-10f),
            dpf(6f),
            dpf(-6f),
            0f
        ).setDuration(280).start()
    }

    private fun goToStep(index: Int) {
        val target = index.coerceIn(0, 3)
        if (target == currentScreen) {
            renderAll()
            return
        }
        stopPlayback()
        val forward = target > currentScreen
        binding.screenFlipper.setInAnimation(
            this,
            if (forward) R.anim.slide_in_right else R.anim.slide_in_left
        )
        binding.screenFlipper.setOutAnimation(
            this,
            if (forward) R.anim.slide_out_left else R.anim.slide_out_right
        )
        currentScreen = target
        binding.screenFlipper.displayedChild = currentScreen
        renderAll()
    }

    private fun restartModule() {
        stopPlayback()
        currentScreen = 0
        challengeSolved = false
        challengeFeedback = ""
        selectedStepIndex = null
        binding.screenFlipper.displayedChild = 0
        renderAll()
    }

    private fun distanceLabel(distance: Int): String = when {
        distance <= 1 -> getString(R.string.modulation_distance_near, distance)
        distance <= 3 -> getString(R.string.modulation_distance_medium, distance)
        else -> getString(R.string.modulation_distance_far, distance)
    }

    private fun signalFor(distance: Int): ModulationSignal = when {
        distance <= 1 -> ModulationSignal.NEAR
        distance <= 3 -> ModulationSignal.MEDIUM
        else -> ModulationSignal.FAR
    }

    private fun signalColor(signal: ModulationSignal): Int = when (signal) {
        ModulationSignal.NEAR -> getColor(R.color.modulation_near)
        ModulationSignal.MEDIUM -> getColor(R.color.modulation_medium)
        ModulationSignal.FAR -> getColor(R.color.modulation_far)
    }

    private fun keyName(pc: Int): String = Pitch.name(normalize(pc), useFlats)

    private fun minorName(pc: Int): String =
        CircleOfFifths.byIndex(circleIndex(pc)).minorName(useFlats)

    private fun circleIndex(pc: Int): Int {
        val index = CircleOfFifths.indexForMajorPc(pc)
        return if (index >= 0) index else 0
    }

    private fun circleKeyPc(index: Int): Int = CircleOfFifths.byIndex(index).majorPc

    private fun normalize(value: Int): Int = ((value % 12) + 12) % 12

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun dpf(value: Float): Float = value * density

    private companion object {
        const val STATE_ORIGIN = "modulation_origin"
        const val STATE_DESTINATION = "modulation_destination"
        const val STATE_SCREEN = "modulation_screen"
        const val STATE_BPM = "modulation_bpm"
        const val STATE_CHALLENGE = "modulation_challenge"
        const val STATE_TYPE = "modulation_type"
        const val STATE_VARIANT = "modulation_variant"
        const val DRAG_LABEL = "modulation_key"
        const val BEATS_PER_CHORD = 2
        const val METRONOME_VOLUME = 70
        const val METRONOME_TICK_MS = 45
    }
}
