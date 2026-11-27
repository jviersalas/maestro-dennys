package apk.mavier.dguitar.core.audio

/**
 * Small seam in front of the audio engine. Activities depend on this instead
 * of the concrete [GuitarSynth], which keeps lifecycle and sound-profile logic
 * in one module and allows a fake output in tests.
 */
interface AudioOutput {

    var bassMode: Boolean

    fun start()
    fun stop()
    fun pluck(stringIndex: Int, midi: Int)
    fun silence()
    fun loadSoundFont(font: Sf2SoundFont?)
}
