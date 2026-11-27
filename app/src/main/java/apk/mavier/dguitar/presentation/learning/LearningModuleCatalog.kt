package apk.mavier.dguitar.presentation.learning

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import apk.mavier.dguitar.R

enum class LearningDestination {
    MAIN_CHORDS,
    MAIN_SCALES,
    MAIN_GUIDED_PROGRESSION,
    IMPROVISATION,
    SONGBOOK,
    CIRCLE_OF_FIFTHS,
    MODULATION,
    TUNER
}

data class LearningModule(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val imageRes: Int,
    val destination: LearningDestination,
    val enabled: Boolean = true
)

/**
 * Single source of truth for the module cards and their order. The activity
 * only maps each destination to an Intent, which keeps navigation out of the
 * catalog and the order testable.
 */
object LearningModuleCatalog {

    fun modules(guitar: Boolean): List<LearningModule> = listOf(
        LearningModule(
            titleRes = R.string.module_chords,
            descriptionRes = R.string.module_chords_desc,
            imageRes = R.drawable.portadaacordes,
            destination = LearningDestination.MAIN_CHORDS,
            enabled = guitar
        ),
        LearningModule(
            titleRes = R.string.module_scales,
            descriptionRes = R.string.module_scales_desc,
            imageRes = R.drawable.portadaescalas,
            destination = LearningDestination.MAIN_SCALES
        ),
        LearningModule(
            titleRes = R.string.module_progression,
            descriptionRes = R.string.module_progression_desc,
            imageRes = R.drawable.portadaprogresion,
            destination = LearningDestination.MAIN_GUIDED_PROGRESSION
        ),
        LearningModule(
            titleRes = R.string.module_improvisation,
            descriptionRes = R.string.module_improvisation_desc,
            imageRes = R.drawable.portadaimprovisacion,
            destination = LearningDestination.IMPROVISATION
        ),
        LearningModule(
            titleRes = R.string.module_quintas,
            descriptionRes = R.string.module_quintas_desc,
            imageRes = R.drawable.portadaquintas,
            destination = LearningDestination.CIRCLE_OF_FIFTHS
        ),
        LearningModule(
            titleRes = R.string.module_modulation,
            descriptionRes = R.string.module_modulation_desc,
            imageRes = R.drawable.portadamodulacion,
            destination = LearningDestination.MODULATION
        ),
        LearningModule(
            titleRes = R.string.module_songbook,
            descriptionRes = R.string.module_songbook_desc,
            imageRes = R.drawable.portadacancionero,
            destination = LearningDestination.SONGBOOK
        ),
        LearningModule(
            titleRes = R.string.module_tuner,
            descriptionRes = R.string.module_tuner_desc,
            imageRes = R.drawable.portadaafinacion,
            destination = LearningDestination.TUNER
        )
    )
}
