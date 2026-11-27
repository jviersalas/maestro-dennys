package apk.mavier.dguitar.presentation.learning

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import apk.mavier.dguitar.R
import apk.mavier.dguitar.databinding.ActivityLearningMenuBinding
import apk.mavier.dguitar.databinding.ItemLearningModuleFlipBinding
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.model.Tunings
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.domain.repository.SettingsRepository
import apk.mavier.dguitar.presentation.common.instrumentLabel
import apk.mavier.dguitar.presentation.circle.CircleOfFifthsActivity
import apk.mavier.dguitar.presentation.improvisation.ImprovisationActivity
import apk.mavier.dguitar.presentation.main.MainActivity
import apk.mavier.dguitar.presentation.modulation.ModulationActivity
import apk.mavier.dguitar.presentation.onboarding.InstrumentActivity
import apk.mavier.dguitar.presentation.songbook.SongbookActivity
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

        val modules = LearningModuleCatalog.modules(guitar)

        val row = binding.modulesRow
        row.removeAllViews()
        val flipCards = mutableListOf<ModuleFlipCard>()
        for (module in modules) {
            val card = ItemLearningModuleFlipBinding.inflate(layoutInflater, row, false)
            card.moduleImage.setImageResource(module.imageRes)
            card.backTitle.setText(module.titleRes)
            card.backDescription.setText(module.descriptionRes)
            card.root.contentDescription = buildString {
                append(getString(module.titleRes))
                append(". ")
                append(getString(module.descriptionRes))
                if (!module.enabled) {
                    append(". ")
                    append(getString(R.string.module_chords_bass))
                }
            }
            val flipCard = ModuleFlipCard(
                binding = card,
                enabled = module.enabled,
                onOpen = { open(module.destination) },
                onDisabled = {
                    Toast.makeText(
                        this,
                        R.string.module_chords_bass,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onRevealed = { revealed ->
                    flipCards.forEach { other ->
                        if (other !== revealed) other.collapse()
                    }
                }
            )
            flipCards.add(flipCard)
            row.addView(card.root)
        }
    }

    private fun open(destination: LearningDestination) {
        when (destination) {
            LearningDestination.MAIN_CHORDS -> openMain(ViewMode.CHORD)
            LearningDestination.MAIN_SCALES -> openMain(ViewMode.SCALE)
            LearningDestination.MAIN_GUIDED_PROGRESSION ->
                openMain(ViewMode.SCALE, openGuided = true)
            LearningDestination.IMPROVISATION -> openImprovisation()
            LearningDestination.SONGBOOK -> openSongbook()
            LearningDestination.CIRCLE_OF_FIFTHS -> openCircleOfFifths()
            LearningDestination.MODULATION -> openModulation()
            LearningDestination.TUNER -> openTuner()
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

    private fun openModulation() {
        startActivity(Intent(this, ModulationActivity::class.java))
    }

    private fun openCircleOfFifths() {
        startActivity(Intent(this, CircleOfFifthsActivity::class.java))
    }

    private fun openImprovisation() {
        startActivity(Intent(this, ImprovisationActivity::class.java))
    }

    private fun openSongbook() {
        startActivity(Intent(this, SongbookActivity::class.java))
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
}
