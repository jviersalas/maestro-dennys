package apk.mavier.dguitar.data.preferences

import apk.mavier.dguitar.domain.model.SoundKind
import apk.mavier.dguitar.domain.model.SoundProfile

object SoundProfileCodec {

    private const val SYNTHETIC = "synthetic"
    private const val BUNDLED_PREFIX = "bundled:"
    private const val IMPORTED_PREFIX = "imported:"

    fun encode(profile: SoundProfile): String = when (profile.kind) {
        SoundKind.SYNTHETIC -> SYNTHETIC
        SoundKind.BUNDLED -> BUNDLED_PREFIX + profile.id
        SoundKind.IMPORTED -> IMPORTED_PREFIX + profile.id
    }

    fun parse(value: String?): SoundProfile {
        val raw = value?.trim().orEmpty()
        return when {
            raw == SYNTHETIC -> SoundProfile.synthetic
            raw.startsWith(BUNDLED_PREFIX) -> SoundProfile(
                SoundKind.BUNDLED,
                raw.removePrefix(BUNDLED_PREFIX)
            )
            raw.startsWith(IMPORTED_PREFIX) -> SoundProfile.imported(
                raw.removePrefix(IMPORTED_PREFIX)
            )
            else -> SoundProfile.bundledGuitar
        }
    }
}
