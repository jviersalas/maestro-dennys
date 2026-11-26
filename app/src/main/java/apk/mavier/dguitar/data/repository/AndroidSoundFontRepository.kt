package apk.mavier.dguitar.data.repository

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import apk.mavier.dguitar.core.audio.Sf2SoundFont
import apk.mavier.dguitar.domain.model.SoundKind
import apk.mavier.dguitar.domain.model.SoundProfile
import apk.mavier.dguitar.domain.repository.SoundFontRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

class AndroidSoundFontRepository(context: Context) : SoundFontRepository {

    private val appContext = context.applicationContext
    private val importedDir = File(appContext.filesDir, "soundfonts")
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DGuitarSoundFont").apply { priority = Thread.NORM_PRIORITY }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cachedKey: String? = null
    private var cachedFont: Sf2SoundFont? = null

    override fun load(profile: SoundProfile, callback: (Sf2SoundFont?) -> Unit) {
        if (profile.kind == SoundKind.SYNTHETIC) {
            mainHandler.post { callback(null) }
            return
        }
        executor.execute {
            val cacheKey = cacheKey(profile)
            val font = if (cacheKey == cachedKey) {
                cachedFont
            } else {
                val loaded = runCatching {
                    open(profile)?.use { Sf2SoundFont.parse(it.readBytes()) }
                }.onFailure {
                    Log.w(TAG, "No se pudo cargar ${profile.kind} ${profile.id}", it)
                }.getOrNull()
                if (loaded != null) {
                    cachedKey = cacheKey
                    cachedFont = loaded
                    Log.i(
                        TAG,
                        "SF2 cargado: ${loaded.presets.size} presets, " +
                            "${loaded.presets.sumOf { preset -> preset.zones.size }} zonas"
                    )
                }
                loaded
            }
            mainHandler.post { callback(font) }
        }
    }

    override fun importFont(
        fileName: String,
        source: InputStream,
        callback: (Result<String>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val bytes = source.use { readLimited(it, MAX_IMPORT_BYTES) }
                check(bytes.isNotEmpty()) { "El archivo está vacío" }
                val font = Sf2SoundFont.parse(bytes)
                importedDir.mkdirs()
                val target = uniqueTarget(sanitizeName(fileName))
                try {
                    target.outputStream().use { it.write(bytes) }
                } catch (error: Exception) {
                    target.delete()
                    throw error
                }
                cachedKey = cacheKeyForFile(target)
                cachedFont = font
                Log.i(TAG, "SF2 importado: ${target.name}")
                target.name
            }
            mainHandler.post { callback(result) }
        }
    }

    override fun importedNames(): List<String> =
        importedFiles().map { it.name }

    override fun deleteImported(fileName: String): Boolean {
        val file = importedFile(fileName)
        val deleted = file.isFile && file.delete()
        if (deleted) {
            cachedKey = null
            cachedFont = null
        }
        return deleted
    }

    private fun open(profile: SoundProfile): InputStream? = when (profile.kind) {
        SoundKind.SYNTHETIC -> null
        SoundKind.BUNDLED -> appContext.assets.open(profile.id)
        SoundKind.IMPORTED -> importedFile(profile.id).inputStream()
    }

    private fun cacheKey(profile: SoundProfile): String = when (profile.kind) {
        SoundKind.SYNTHETIC -> "synthetic"
        SoundKind.BUNDLED -> "asset:${profile.id}"
        SoundKind.IMPORTED -> cacheKeyForFile(importedFile(profile.id))
    }

    private fun cacheKeyForFile(file: File): String =
        "file:${file.name}:${file.length()}:${file.lastModified()}"

    private fun importedFile(fileName: String): File =
        File(importedDir, File(fileName).name)

    private fun importedFiles(): List<File> {
        importedDir.mkdirs()
        return importedDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".sf2", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    private fun uniqueTarget(fileName: String): File {
        var target = File(importedDir, fileName)
        if (!target.exists()) return target
        val base = target.name.removeSuffix(".sf2")
        var index = 1
        while (target.exists()) {
            target = File(importedDir, "$base-$index.sf2")
            index++
        }
        return target
    }

    private fun sanitizeName(fileName: String): String {
        val baseName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val base = baseName
            .substringBeforeLast('.', baseName)
            .filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }
            .trim()
            .take(60)
            .ifBlank { "sonido" }
        return "$base.sf2"
    }

    private fun readLimited(input: InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            check(total <= maxBytes) { "El archivo SF2 supera el límite permitido" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val TAG = "SoundFontRepository"
        const val MAX_IMPORT_BYTES = 64L * 1024 * 1024
    }
}
