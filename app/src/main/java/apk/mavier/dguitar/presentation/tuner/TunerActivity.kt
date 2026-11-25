package apk.mavier.dguitar.presentation.tuner

import apk.mavier.dguitar.R

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import apk.mavier.dguitar.core.audio.PitchAnalyzer
import apk.mavier.dguitar.core.audio.PitchDetector
import apk.mavier.dguitar.databinding.ActivityTunerBinding
import apk.mavier.dguitar.domain.model.Pitch
import apk.mavier.dguitar.domain.model.TunerMath
import apk.mavier.dguitar.domain.model.TunerTargetResolver
import apk.mavier.dguitar.domain.model.Tunings
import kotlin.math.roundToInt

class TunerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TUNING = "tuning_index"
        const val EXTRA_FLATS = "use_flats"
        private const val REQUEST_AUDIO = 501
        private const val MIN_CONFIDENCE = 0.25f
        private const val HOLD_TIMEOUT_MS = 1_600L
        private const val STABLE_DURATION_MS = 500L
    }

    private lateinit var binding: ActivityTunerBinding
    private val detector = PitchDetector()
    private val stringButtons = mutableListOf<TextView>()

    private var tuning = Tunings.STANDARD
    private var useFlats = false

    private var selectedStringIndex: Int? = null
    private var autoStringIndex: Int? = null
    private var lastTargetIndex: Int? = null
    private var smoothedFrequency: Double? = null
    private var lastValidReadingAt = 0L
    private var stableSince = 0L
    private var isStable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTunerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tuning = Tunings.all.getOrElse(intent.getIntExtra(EXTRA_TUNING, 0)) { Tunings.STANDARD }
        useFlats = intent.getBooleanExtra(EXTRA_FLATS, false)

        detector.onPitch = { result ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) handlePitch(result)
            }
        }

        binding.closeButton.setOnClickListener { finish() }
        binding.autoButton.setOnClickListener {
            selectedStringIndex = null
            autoStringIndex = null
            lastTargetIndex = null
            smoothedFrequency = null
            stableSince = 0L
            isStable = false
            binding.inTuneBadge.visibility = View.INVISIBLE
            updateStringButtons()
            binding.statusText.text = getString(R.string.automatic)
        }
        binding.permissionButton.setOnClickListener { requestAudioPermission() }

        buildStringButtons()
        updateStringButtons()
        showIdleReading()
        binding.statusText.text = getString(R.string.listening)

        if (hasAudioPermission()) {
            startListening()
        } else {
            requestAudioPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAudioPermission() && !detector.isRunning) {
            startListening()
        }
    }

    override fun onPause() {
        super.onPause()
        detector.stop()
        lastValidReadingAt = 0L
        binding.gauge.updateReading(0f, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.stop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_AUDIO) return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            binding.statusText.text = getString(R.string.microphone_permission_needed)
            binding.permissionButton.visibility = View.VISIBLE
            binding.permissionButton.text = getString(R.string.enable_microphone)
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestAudioPermission() {
        if (hasAudioPermission()) {
            startListening()
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_AUDIO
        )
    }

    private fun startListening() {
        binding.permissionButton.visibility = View.GONE
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.listening)
        if (!detector.start()) {
            binding.statusText.text = getString(R.string.microphone_start_failed)
            binding.permissionButton.visibility = View.VISIBLE
            binding.permissionButton.text = getString(R.string.retry)
        }
    }

    private fun buildStringButtons() {
        binding.stringRow.removeAllViews()
        stringButtons.clear()

        for (index in tuning.strings.indices.reversed()) {
            val string = tuning.strings[index]
            val button = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
                gravity = Gravity.CENTER
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = ContextCompat.getDrawable(this@TunerActivity, R.drawable.bg_tuner_string)
                setTextColor(ContextCompat.getColorStateList(this@TunerActivity, R.color.tuner_string_text))
                isClickable = true
                isFocusable = true
                contentDescription = "Cuerda ${string.name(useFlats)}"
                tag = index
                text = string.name(useFlats)
                setOnClickListener {
                    if (selectedStringIndex == index) {
                        selectedStringIndex = null
                    } else {
                        selectedStringIndex = index
                        binding.noteText.text = string.name(useFlats)
                        binding.noteText.setTextColor(getColor(R.color.text_primary))
                        binding.hzText.text = getString(R.string.hz_placeholder)
                        binding.centsText.text = getString(R.string.cents_placeholder)
                        binding.centsText.setTextColor(getColor(R.color.text_secondary))
                        binding.statusText.text =
                            getString(R.string.string_status, string.name(useFlats))
                    }
                    autoStringIndex = null
                    lastTargetIndex = null
                    smoothedFrequency = null
                    stableSince = 0L
                    isStable = false
                    binding.inTuneBadge.visibility = View.INVISIBLE
                    updateStringButtons()
                }
            }
            stringButtons.add(button)
            binding.stringRow.addView(button)
        }
    }

    private fun updateStringButtons() {
        val target = selectedStringIndex ?: autoStringIndex
        for (button in stringButtons) {
            button.isSelected = (button.tag as? Int) == target
        }
        val auto = selectedStringIndex == null
        binding.autoButton.alpha = if (auto) 1f else 0.55f
        binding.autoButton.isSelected = auto
        binding.autoButton.contentDescription =
            if (auto) getString(R.string.auto_mode_on) else getString(R.string.auto_mode_off)
    }

    private fun showIdleReading() {
        stableSince = 0L
        isStable = false
        binding.inTuneBadge.visibility = View.INVISIBLE
        binding.noteText.text = getString(R.string.note_placeholder)
        binding.noteText.setTextColor(getColor(R.color.text_primary))
        binding.hzText.text = getString(R.string.hz_placeholder)
        binding.centsText.text = getString(R.string.cents_placeholder)
        binding.centsText.setTextColor(getColor(R.color.text_secondary))
        binding.gauge.updateReading(0f, false)
    }

    private fun handlePitch(result: PitchAnalyzer.Result?) {
        if (result == null || result.confidence < MIN_CONFIDENCE || result.frequency <= 0.0) {
            val now = SystemClock.elapsedRealtime()
            if (lastValidReadingAt != 0L && now - lastValidReadingAt < HOLD_TIMEOUT_MS) {
                return
            }
            handleNoSignal()
            return
        }

        lastValidReadingAt = SystemClock.elapsedRealtime()
        val frequency = result.frequency
        val targetIndex = resolveTarget(frequency) ?: run {
            handleNoSignal()
            return
        }
        val targetString = tuning.strings[targetIndex]
        val targetFrequency = Pitch.frequency(targetString.openMidi)

        if (lastTargetIndex != targetIndex) {
            smoothedFrequency = frequency
            lastTargetIndex = targetIndex
        } else {
            val previous = smoothedFrequency
            smoothedFrequency = if (previous == null) frequency else previous * 0.65 + frequency * 0.35
        }

        val smoothed = smoothedFrequency ?: frequency
        val cents = TunerMath.wrappedCents(smoothed, targetFrequency)
        val inTune = TunerMath.isInTune(cents)

        val now = lastValidReadingAt
        if (inTune) {
            if (stableSince == 0L) stableSince = now
        } else {
            stableSince = 0L
        }
        isStable = inTune && stableSince != 0L && now - stableSince >= STABLE_DURATION_MS

        binding.noteText.text = targetString.name(useFlats)
        binding.noteText.setTextColor(if (isStable) getColor(R.color.success) else getColor(R.color.text_primary))
        binding.hzText.text = TunerMath.formatFrequency(smoothed)
        binding.centsText.text = TunerMath.formatCents(cents)
        binding.centsText.setTextColor(if (inTune) getColor(R.color.success) else getColor(R.color.text_secondary))
        binding.inTuneBadge.visibility = if (isStable) View.VISIBLE else View.INVISIBLE
        binding.statusText.text = if (selectedStringIndex == null) {
            getString(R.string.automatic)
        } else {
            getString(R.string.string_status, targetString.name(useFlats))
        }
        binding.gauge.updateReading(cents.toFloat(), true)

        if (selectedStringIndex == null) {
            autoStringIndex = targetIndex
        } else {
            autoStringIndex = null
        }
        updateStringButtons()
    }

    private fun resolveTarget(frequency: Double): Int? =
        TunerTargetResolver.resolve(
            frequency = frequency,
            tuning = tuning,
            selectedStringIndex = selectedStringIndex,
            previousAutoIndex = autoStringIndex
        )

    private fun handleNoSignal() {
        lastValidReadingAt = 0L
        smoothedFrequency = null
        stableSince = 0L
        isStable = false
        binding.inTuneBadge.visibility = View.INVISIBLE
        binding.gauge.updateReading(0f, false)
        binding.hzText.text = getString(R.string.hz_placeholder)
        binding.centsText.text = getString(R.string.cents_placeholder)
        binding.centsText.setTextColor(getColor(R.color.text_secondary))
        val manualNote = selectedStringIndex?.let { tuning.strings.getOrNull(it)?.name(useFlats) }
        binding.statusText.text = if (manualNote != null) {
            getString(R.string.waiting_status, manualNote)
        } else {
            getString(R.string.listening)
        }
        binding.noteText.setTextColor(getColor(R.color.text_primary))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
