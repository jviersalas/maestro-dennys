package apk.mavier.dguitar.presentation.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import apk.mavier.dguitar.databinding.ActivityInstrumentBinding
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.model.Tuning
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.presentation.main.MainActivity

class InstrumentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInstrumentBinding
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstrumentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsRepository = (application as DependencyProvider).settingsRepository

        binding.guitarCard.setOnClickListener { chooseGuitar() }
        binding.bassCard.setOnClickListener { chooseBass() }
    }

    private fun chooseGuitar() {
        val saved = settingsRepository.load().tuning
        val tuning = if (saved.strings.size == 6) saved else Tunings.STANDARD
        startWith(tuning)
    }

    private fun chooseBass() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Bajo")
            .setItems(arrayOf("4 cuerdas (E A D G)", "5 cuerdas (B E A D G)")) { _, which ->
                startWith(if (which == 0) Tunings.BASS_4 else Tunings.BASS_5)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startWith(tuning: Tuning) {
        val current = settingsRepository.load()
        val mode = when {
            tuning.strings.size < 6 && current.mode == ViewMode.CHORD -> ViewMode.SCALE
            current.mode == ViewMode.IMPROV -> ViewMode.SCALE
            else -> current.mode
        }
        settingsRepository.save(current.copy(tuning = tuning, mode = mode))

        startActivity(Intent(this, MainActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
