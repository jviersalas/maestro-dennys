package apk.mavier.dguitar.presentation.learning

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import apk.mavier.dguitar.R
import apk.mavier.dguitar.databinding.ActivityLearningMenuBinding
import apk.mavier.dguitar.databinding.ItemLearningModuleBinding
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.presentation.common.instrumentLabel
import apk.mavier.dguitar.presentation.improvisation.ImprovisationActivity
import apk.mavier.dguitar.presentation.main.MainActivity
import apk.mavier.dguitar.presentation.onboarding.InstrumentActivity
import apk.mavier.dguitar.presentation.tuner.TunerActivity
import apk.mavier.dguitar.presentation.ui.applySystemBarInsets

class LearningMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLearningMenuBinding
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearningMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.root.applySystemBarInsets()

        settingsRepository = (application as DependencyProvider).settingsRepository
        binding.changeInstrumentButton.setOnClickListener {
            startActivity(Intent(this, InstrumentActivity::class.java))
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val config = settingsRepository.load()
        val guitar = config.tuning.strings.size >= 6
        binding.menuSubtitle.text = getString(
            R.string.learning_menu_subtitle,
            instrumentLabel(config.tuning.strings.size)
        )

        val modules = listOf(
            LearningModule(
                titleRes = R.string.module_chords,
                descriptionRes = R.string.module_chords_desc,
                imageRes = R.drawable.portadaacordes,
                enabled = guitar
            ) { openMain(ViewMode.CHORD) },
            LearningModule(
                titleRes = R.string.module_scales,
                descriptionRes = R.string.module_scales_desc,
                imageRes = R.drawable.portadaescalas,
                enabled = true
            ) { openMain(ViewMode.SCALE) },
            LearningModule(
                titleRes = R.string.module_progression,
                descriptionRes = R.string.module_progression_desc,
                imageRes = R.drawable.portadaprogresion,
                enabled = true
            ) { openMain(ViewMode.SCALE, openGuided = true) },
            LearningModule(
                titleRes = R.string.module_improvisation,
                descriptionRes = R.string.module_improvisation_desc,
                imageRes = R.drawable.portadaimprovisacion,
                enabled = true
            ) { openImprovisation() },
            LearningModule(
                titleRes = R.string.module_tuner,
                descriptionRes = R.string.module_tuner_desc,
                imageRes = R.drawable.portadaafinacion,
                enabled = true
            ) { openTuner() }
        )

        val row = binding.modulesRow
        row.removeAllViews()
        for (module in modules) {
            val card = ItemLearningModuleBinding.inflate(layoutInflater, row, false)
            card.moduleImage.setImageResource(module.imageRes)
            card.moduleTitle.setText(module.titleRes)
            card.moduleDescription.setText(module.descriptionRes)
            card.root.alpha = if (module.enabled) 1f else 0.55f
            card.root.contentDescription = buildString {
                append(getString(module.titleRes))
                append(". ")
                append(getString(module.descriptionRes))
                if (!module.enabled) {
                    append(". ")
                    append(getString(R.string.module_chords_bass))
                }
            }
            card.root.setOnClickListener {
                if (module.enabled) {
                    module.onOpen()
                } else {
                    Toast.makeText(
                        this,
                        R.string.module_chords_bass,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            row.addView(card.root)
        }
    }

    private fun openMain(mode: ViewMode, openGuided: Boolean = false) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_MODE, mode.name)
                if (openGuided) putExtra(MainActivity.EXTRA_OPEN_GUIDED, true)
            }
        )
    }

    private fun openImprovisation() {
        startActivity(Intent(this, ImprovisationActivity::class.java))
    }

    private fun openTuner() {
        val config = settingsRepository.load()
        startActivity(
            Intent(this, TunerActivity::class.java).apply {
                putExtra(TunerActivity.EXTRA_TUNING, Tunings.all.indexOf(config.tuning))
                putExtra(TunerActivity.EXTRA_FLATS, config.useFlats)
            }
        )
    }

    private data class LearningModule(
        val titleRes: Int,
        val descriptionRes: Int,
        val imageRes: Int,
        val enabled: Boolean,
        val onOpen: () -> Unit
    )
}
