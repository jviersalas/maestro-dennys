package apk.mavier.dguitar.presentation.common

import android.content.Context
import apk.mavier.dguitar.R
import apk.mavier.dguitar.domain.model.SoundKind
import apk.mavier.dguitar.domain.model.SoundProfile

/**
 * Shared presentation label for a sound profile. Kept outside the activities
 * so the synthetic/bundled/imported wording stays identical everywhere.
 */
fun soundProfileLabel(context: Context, profile: SoundProfile): String =
    when (profile.kind) {
        SoundKind.SYNTHETIC -> context.getString(R.string.sound_synthetic)
        SoundKind.BUNDLED -> SoundFontCatalog.find(profile.id)?.let {
            context.getString(it.labelRes)
        } ?: profile.id
        SoundKind.IMPORTED -> context.getString(
            R.string.sound_imported,
            profile.id.removeSuffix(".sf2")
        )
    }
