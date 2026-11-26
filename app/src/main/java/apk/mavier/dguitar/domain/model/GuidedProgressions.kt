package apk.mavier.dguitar.domain.model

/**
 * A chord inside a guided progression. [offset] is the distance in semitones
 * from the progression key, so the whole progression can be transposed by
 * simply changing the key.
 */
data class GuidedChord(
    val offset: Int,
    val quality: ChordQuality,
    val bars: Int = 1
)

data class GuidedProgression(
    val id: String,
    val name: String,
    val category: String,
    val degrees: String,
    val description: String,
    val chords: List<GuidedChord>,
    val suggestedScale: ScaleMode,
    val scaleOffset: Int = 0
) {

    val totalBars: Int
        get() = chords.sumOf { it.bars }

    fun scaleRoot(keyRootPc: Int): Int = normalizePitchClass(keyRootPc + scaleOffset)

    fun improvChords(keyRootPc: Int): List<ImprovChord> = chords.map { chord ->
        ImprovChord(
            rootPc = normalizePitchClass(keyRootPc + chord.offset),
            quality = chord.quality,
            barsPerChord = chord.bars
        )
    }

    fun labels(keyRootPc: Int, useFlats: Boolean): List<String> = chords.map { chord ->
        Pitch.name(normalizePitchClass(keyRootPc + chord.offset), useFlats) +
            chord.quality.symbol
    }

    fun scaleLabel(keyRootPc: Int, useFlats: Boolean): String =
        "${suggestedScale.name} en ${Pitch.name(scaleRoot(keyRootPc), useFlats)}"
}

private fun normalizePitchClass(value: Int): Int = ((value % 12) + 12) % 12

object GuidedProgressions {

    private val major = Scales.MAJOR

    val all = listOf(
        GuidedProgression(
            id = "pop-1564",
            name = "I–V–vi–IV",
            category = "Pop",
            degrees = "I–V–vi–IV",
            description = "El giro pop más usado: tónica, dominante y relativas.",
            chords = listOf(
                GuidedChord(0, Chords.MAJOR),
                GuidedChord(7, Chords.MAJOR),
                GuidedChord(9, Chords.MINOR),
                GuidedChord(5, Chords.MAJOR)
            ),
            suggestedScale = major
        ),
        GuidedProgression(
            id = "pop-6415",
            name = "vi–IV–I–V",
            category = "Pop",
            degrees = "vi–IV–I–V",
            description = "La misma familia empezando por la relativa menor.",
            chords = listOf(
                GuidedChord(9, Chords.MINOR),
                GuidedChord(5, Chords.MAJOR),
                GuidedChord(0, Chords.MAJOR),
                GuidedChord(7, Chords.MAJOR)
            ),
            suggestedScale = major
        ),
        GuidedProgression(
            id = "major-145",
            name = "I–IV–V",
            category = "Rock",
            degrees = "I–IV–V",
            description = "Los tres acordes primarios; base del rock y el folk.",
            chords = listOf(
                GuidedChord(0, Chords.MAJOR),
                GuidedChord(5, Chords.MAJOR),
                GuidedChord(7, Chords.MAJOR)
            ),
            suggestedScale = major
        ),
        GuidedProgression(
            id = "pop-1645",
            name = "I–vi–IV–V",
            category = "Balada",
            degrees = "I–vi–IV–V",
            description = "Progresión de los años 50, dulce y circular.",
            chords = listOf(
                GuidedChord(0, Chords.MAJOR),
                GuidedChord(9, Chords.MINOR),
                GuidedChord(5, Chords.MAJOR),
                GuidedChord(7, Chords.DOMINANT_7)
            ),
            suggestedScale = major
        ),
        GuidedProgression(
            id = "cadencia-rota",
            name = "I–IV–V–vi",
            category = "Balada",
            degrees = "I–IV–V–vi",
            description = "Cadencia rota: el V resuelve en vi en vez de la tónica.",
            chords = listOf(
                GuidedChord(0, Chords.MAJOR),
                GuidedChord(5, Chords.MAJOR),
                GuidedChord(7, Chords.MAJOR),
                GuidedChord(9, Chords.MINOR)
            ),
            suggestedScale = major
        ),
        GuidedProgression(
            id = "jazz-251",
            name = "ii–V–I",
            category = "Jazz",
            degrees = "ii–V–I",
            description = "La célula base del jazz: preparación, tensión y reposo.",
            chords = listOf(
                GuidedChord(2, Chords.MINOR_7),
                GuidedChord(7, Chords.DOMINANT_7),
                GuidedChord(0, Chords.MAJOR_7)
            ),
            suggestedScale = major
        ),
        GuidedProgression(
            id = "jazz-turnaround",
            name = "I–vi–ii–V",
            category = "Jazz",
            degrees = "I–vi–ii–V",
            description = "Turnaround clásico para volver al principio del ciclo.",
            chords = listOf(
                GuidedChord(0, Chords.MAJOR_7),
                GuidedChord(9, Chords.MINOR_7),
                GuidedChord(2, Chords.MINOR_7),
                GuidedChord(7, Chords.DOMINANT_7)
            ),
            suggestedScale = major
        ),
        GuidedProgression(
            id = "jazz-minor-251",
            name = "iiø–V–i",
            category = "Jazz menor",
            degrees = "iiø–V–i",
            description = "Cadencia menor con semidisminuido y dominante.",
            chords = listOf(
                GuidedChord(2, Chords.HALF_DIMINISHED),
                GuidedChord(7, Chords.DOMINANT_7),
                GuidedChord(0, Chords.MINOR_7)
            ),
            suggestedScale = Scales.HARMONIC_MINOR
        ),
        GuidedProgression(
            id = "blues-12",
            name = "12-bar blues",
            category = "Blues",
            degrees = "I I I I · IV IV I I · V IV I V",
            description = "Doce compases con séptimas: la forma madre del blues.",
            chords = listOf(
                GuidedChord(0, Chords.DOMINANT_7, bars = 4),
                GuidedChord(5, Chords.DOMINANT_7, bars = 2),
                GuidedChord(0, Chords.DOMINANT_7, bars = 2),
                GuidedChord(7, Chords.DOMINANT_7, bars = 1),
                GuidedChord(5, Chords.DOMINANT_7, bars = 1),
                GuidedChord(0, Chords.DOMINANT_7, bars = 1),
                GuidedChord(7, Chords.DOMINANT_7, bars = 1)
            ),
            suggestedScale = Scales.BLUES_MINOR
        ),
        GuidedProgression(
            id = "andaluza",
            name = "i–VII–VI–V",
            category = "Andaluza",
            degrees = "i–VII–VI–V",
            description = "Cadencia andaluza; el sonido flamenco por excelencia.",
            chords = listOf(
                GuidedChord(0, Chords.MINOR),
                GuidedChord(10, Chords.MAJOR),
                GuidedChord(8, Chords.MAJOR),
                GuidedChord(7, Chords.MAJOR)
            ),
            suggestedScale = Scales.PHRYGIAN_DOMINANT
        ),
        GuidedProgression(
            id = "minor-1637",
            name = "i–VI–III–VII",
            category = "Menor",
            degrees = "i–VI–III–VII",
            description = "Progresión menor natural muy común en rock y pop.",
            chords = listOf(
                GuidedChord(0, Chords.MINOR),
                GuidedChord(8, Chords.MAJOR),
                GuidedChord(3, Chords.MAJOR),
                GuidedChord(10, Chords.MAJOR)
            ),
            suggestedScale = Scales.NATURAL_MINOR
        ),
        GuidedProgression(
            id = "minor-145",
            name = "i–iv–v",
            category = "Menor",
            degrees = "i–iv–v",
            description = "Los tres acordes menores primarios de la tonalidad.",
            chords = listOf(
                GuidedChord(0, Chords.MINOR),
                GuidedChord(5, Chords.MINOR),
                GuidedChord(7, Chords.MINOR)
            ),
            suggestedScale = Scales.NATURAL_MINOR
        ),
        GuidedProgression(
            id = "pachelbel",
            name = "Canon I–V–vi–iii–IV–I–IV–V",
            category = "Clásica",
            degrees = "I–V–vi–iii–IV–I–IV–V",
            description = "El canon de Pachelbel, favorito de la música clásica y pop.",
            chords = listOf(
                GuidedChord(0, Chords.MAJOR),
                GuidedChord(7, Chords.MAJOR),
                GuidedChord(9, Chords.MINOR),
                GuidedChord(4, Chords.MINOR),
                GuidedChord(5, Chords.MAJOR),
                GuidedChord(0, Chords.MAJOR),
                GuidedChord(5, Chords.MAJOR),
                GuidedChord(7, Chords.MAJOR)
            ),
            suggestedScale = major
        )
    )

    fun byId(id: String): GuidedProgression? = all.firstOrNull { it.id == id }
}
