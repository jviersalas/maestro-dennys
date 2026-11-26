package apk.mavier.dguitar.core.audio

interface PlayableVoice {
    val ageSamples: Long
    val finished: Boolean
    fun next(): Float
}
