package apk.mavier.dguitar.domain.model

enum class SoundKind { SYNTHETIC, BUNDLED, IMPORTED }

data class SoundProfile(
    val kind: SoundKind,
    val id: String = ""
) {
    companion object {
        val synthetic = SoundProfile(SoundKind.SYNTHETIC)
        val bundledGuitar = SoundProfile(
            SoundKind.BUNDLED,
            "sf2/guitar_acoustic.sf2"
        )

        fun imported(fileName: String): SoundProfile =
            SoundProfile(SoundKind.IMPORTED, fileName)
    }
}
