package apk.mavier.dguitar.domain.repository

import apk.mavier.dguitar.core.audio.Sf2SoundFont
import apk.mavier.dguitar.domain.model.SoundProfile
import java.io.InputStream

interface SoundFontRepository {
    fun load(profile: SoundProfile, callback: (Sf2SoundFont?) -> Unit)
    fun importFont(
        fileName: String,
        source: InputStream,
        callback: (Result<String>) -> Unit
    )
    fun importedNames(): List<String>
    fun deleteImported(fileName: String): Boolean
}
