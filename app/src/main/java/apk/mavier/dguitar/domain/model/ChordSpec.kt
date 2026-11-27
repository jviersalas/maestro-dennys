package apk.mavier.dguitar.domain.model

/**
 * A chord reduced to the two facts every part of the app needs: its root and
 * its quality. Songbook chords, improvisation chords, harmonic-circle chords
 * and modulation chords all adapt to this small interface, so naming and
 * auditioning code can be shared.
 */
interface ChordSpec {

    val rootPc: Int
    val quality: ChordQuality

    /** Written symbol, e.g. `G`, `Gm` or `G/B`. */
    fun symbol(useFlats: Boolean): String =
        Pitch.name(rootPc, useFlats) + quality.symbol

    /** Spanish reading, e.g. `Sol mayor`. */
    fun reading(useFlats: Boolean): String =
        ChordTheory.displayName(rootPc, quality, useFlats)

    /** Default label. Chord types with extra information, such as a bass note, override it. */
    fun label(useFlats: Boolean): String = symbol(useFlats)
}
