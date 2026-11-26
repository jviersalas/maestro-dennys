package apk.mavier.dguitar.presentation.common

import androidx.annotation.StringRes
import apk.mavier.dguitar.R
import apk.mavier.dguitar.domain.model.SoundKind
import apk.mavier.dguitar.domain.model.SoundProfile

enum class SoundFontFamily { GUITAR, BASS }

data class BundledSoundFont(
    val assetPath: String,
    @param:StringRes val labelRes: Int,
    val family: SoundFontFamily
)

object SoundFontCatalog {

    val guitarAcoustic = BundledSoundFont(
        assetPath = "sf2/guitar_acoustic.sf2",
        labelRes = R.string.sound_guitar_acoustic,
        family = SoundFontFamily.GUITAR
    )
    val bassYr = BundledSoundFont(
        assetPath = "sf2/bass_yr.sf2",
        labelRes = R.string.sound_bass_yr,
        family = SoundFontFamily.BASS
    )

    val all = listOf(guitarAcoustic, bassYr)

    fun forFamily(family: SoundFontFamily): List<BundledSoundFont> =
        all.filter { it.family == family }

    fun find(assetPath: String): BundledSoundFont? =
        all.firstOrNull { it.assetPath == assetPath }

    fun defaultFor(family: SoundFontFamily): BundledSoundFont =
        if (family == SoundFontFamily.BASS) bassYr else guitarAcoustic

    fun resolve(profile: SoundProfile, family: SoundFontFamily): SoundProfile {
        if (profile.kind != SoundKind.BUNDLED) return profile
        val entry = find(profile.id)
        if (entry == null || entry.family != family) {
            return SoundProfile(
                SoundKind.BUNDLED,
                defaultFor(family).assetPath
            )
        }
        return profile
    }
}
