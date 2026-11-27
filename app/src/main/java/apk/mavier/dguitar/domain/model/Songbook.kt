package apk.mavier.dguitar.domain.model

import java.util.UUID

enum class SongPartType(val label: String) {
    INTRO("Intro"),
    VERSO("Verso"),
    PRE_CORO("Pre-coro"),
    CORO("Coro"),
    PUENTE("Puente"),
    SOLO("Solo"),
    OUTRO("Outro"),
    OTRO("Otra")
}

data class SongChord(
    override val rootPc: Int,
    override val quality: ChordQuality,
    val bassPc: Int? = null
) : ChordSpec {

    override fun label(useFlats: Boolean): String {
        val bass = bassPc?.let { "/" + Pitch.name(it, useFlats) }.orEmpty()
        return symbol(useFlats) + bass
    }

    fun notes(useFlats: Boolean): List<String> {
        val chordNotes = quality.intervals
            .distinct()
            .sortedBy { ((it % 12) + 12) % 12 }
            .map { semitone -> ChordTheory.noteName(rootPc, quality, useFlats, semitone) }
        val bass = bassPc ?: return chordNotes
        val bassName = Pitch.name(bass, useFlats)
        return if (bassName in chordNotes) chordNotes else listOf(bassName) + chordNotes
    }

    fun notesText(useFlats: Boolean): String =
        notes(useFlats).joinToString(" · ")

    fun bassLabel(useFlats: Boolean): String? =
        bassPc?.let { Pitch.name(it, useFlats) }
}

data class SongBar(
    val chords: List<SongChord> = emptyList()
) {
    val normalizedChords: List<SongChord>
        get() = chords.take(MAX_CHORDS)

    companion object {
        const val MAX_CHORDS = 4
    }
}

data class SongPart(
    val name: String,
    val type: SongPartType = SongPartType.VERSO,
    val bars: List<SongBar> = emptyList(),
    val lyrics: String = "",
    val repeatCount: Int = 1
) {

    val chords: List<SongChord>
        get() = bars.flatMap { it.normalizedChords }

    val normalizedBars: List<SongBar>
        get() = if (bars.isEmpty()) listOf(SongBar()) else bars

    val normalizedRepeatCount: Int
        get() = Songbook.normalizeRepeatCount(repeatCount)
}

data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val bpm: Int = Songbook.DEFAULT_BPM,
    val parts: List<SongPart> = emptyList()
)

data class SongStep(
    val partIndex: Int,
    val chordIndex: Int,
    val partName: String,
    val partType: SongPartType,
    val chord: SongChord,
    val repeatIndex: Int = 0,
    val durationBeats: Double = BEATS_PER_BAR.toDouble(),
    val barIndex: Int = 0,
    val indexInBar: Int = 0
) {
    companion object {
        const val BEATS_PER_BAR = 4
    }
}

object Songbook {

    const val DEFAULT_BPM = 80
    const val MIN_BPM = 40
    const val MAX_BPM = 220
    const val MIN_REPEAT = 1
    const val MAX_REPEAT = 99

    fun normalizeBpm(bpm: Int): Int = bpm.coerceIn(MIN_BPM, MAX_BPM)

    fun normalizeRepeatCount(count: Int): Int =
        count.coerceIn(MIN_REPEAT, MAX_REPEAT)

    fun newSong(title: String, artist: String, bpm: Int = DEFAULT_BPM): Song =
        Song(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            artist = artist.trim(),
            bpm = normalizeBpm(bpm),
            parts = listOf(
                SongPart(
                    name = "Verso 1",
                    type = SongPartType.VERSO,
                    bars = listOf(SongBar())
                )
            )
        )

    fun steps(song: Song): List<SongStep> =
        song.parts.flatMapIndexed { partIndex, part ->
            val repeats = part.normalizedRepeatCount
            (0 until repeats).flatMap { repeatIndex ->
                var chordOffset = 0
                part.bars.flatMapIndexed { barIndex, bar ->
                    val barChords = bar.normalizedChords
                    if (barChords.isEmpty()) {
                        return@flatMapIndexed emptyList()
                    }
                    val durationBeats =
                        SongStep.BEATS_PER_BAR.toDouble() / barChords.size
                    val barSteps = barChords.mapIndexed { indexInBar, chord ->
                        SongStep(
                            partIndex = partIndex,
                            chordIndex = chordOffset + indexInBar,
                            partName = part.name,
                            partType = part.type,
                            chord = chord,
                            repeatIndex = repeatIndex,
                            durationBeats = durationBeats,
                            barIndex = barIndex,
                            indexInBar = indexInBar
                        )
                    }
                    chordOffset += barChords.size
                    barSteps
                }
            }
        }

    fun totalChords(song: Song): Int = song.parts.sumOf { it.chords.size }

    fun firstStepOfPart(song: Song, partIndex: Int): Int {
        if (partIndex <= 0) return 0
        var total = 0
        for (index in 0 until partIndex.coerceAtMost(song.parts.size)) {
            total += song.parts[index].chords.size *
                song.parts[index].normalizedRepeatCount
        }
        return total
    }

    fun partSummary(song: Song): String {
        val parts = song.parts.size
        val chords = totalChords(song)
        val partsLabel = if (parts == 1) "1 parte" else "$parts partes"
        val chordsLabel = if (chords == 1) "1 acorde" else "$chords acordes"
        return "$partsLabel · $chordsLabel"
    }

    fun firstChordSummary(song: Song, useFlats: Boolean, limit: Int = 6): String =
        song.parts
            .flatMap { it.chords }
            .take(limit)
            .joinToString(" · ") { it.label(useFlats) }

    /**
     * Built-in demonstration song. It is seeded once by the repository and can
     * be edited or deleted like any other song.
     */
    fun exampleSong(): Song {
        val d = SongChord(2, Chords.MAJOR)
        val dSharp5 = SongChord(2, Chords.AUGMENTED_SHARP_5)
        val g = SongChord(7, Chords.MAJOR)
        val a = SongChord(9, Chords.MAJOR)
        val d5 = SongChord(2, Chords.POWER)
        val gOverB = SongChord(7, Chords.MAJOR, bassPc = 11)
        val aOverCSharp = SongChord(9, Chords.MAJOR, bassPc = 1)

        return Song(
            id = EXAMPLE_ID,
            title = "Atado a un Sentimiento",
            artist = "Miguel Mateos",
            bpm = 80,
            parts = listOf(
                SongPart(
                    name = "Intro",
                    type = SongPartType.INTRO,
                    bars = listOf(
                        SongBar(listOf(d, dSharp5, g, dSharp5)),
                        SongBar(listOf(a, d, dSharp5, dSharp5)),
                        SongBar(listOf(g, a)),
                        SongBar(listOf(d5, dSharp5, g, a)),
                        SongBar(listOf(d5, dSharp5, g, a))
                    ),
                    lyrics = "Te quiero, te extraño\n" +
                        "Nada es igual, al ayer\n" +
                        "Oh! oh! oh! nada uouh no!"
                ),
                SongPart(
                    name = "Verso 1",
                    type = SongPartType.VERSO,
                    bars = listOf(
                        SongBar(listOf(d5, dSharp5, g)),
                        SongBar(listOf(a, d5, dSharp5)),
                        SongBar(listOf(g, a, d5))
                    ),
                    lyrics = "Te espero, deseo\n" +
                        "Como una roca espera al mar\n" +
                        "Te necesito ouh oh!"
                ),
                SongPart(
                    name = "Pre-Coro 1",
                    type = SongPartType.PRE_CORO,
                    bars = listOf(
                        SongBar(listOf(dSharp5, g, a, d5)),
                        SongBar(listOf(dSharp5, g, a))
                    ),
                    lyrics = "Y vivo, atado, a un sentimiento carnal\n" +
                        "Atado a tu luna, uouh no!"
                ),
                SongPart(
                    name = "Coro",
                    type = SongPartType.CORO,
                    bars = listOf(
                        SongBar(listOf(g, a)),
                        SongBar(listOf(gOverB, aOverCSharp)),
                        SongBar(listOf(g, a))
                    ),
                    lyrics = "Yo puedo tocar el fuego\n" +
                        "Puedo hasta quemarme entero\n" +
                        "Pero no me pidas, que te deje de amar"
                ),
                SongPart(
                    name = "Solo 1",
                    type = SongPartType.SOLO,
                    bars = listOf(SongBar(listOf(d, dSharp5))),
                    lyrics = "[Solo]"
                ),
                SongPart(
                    name = "Verso 2",
                    type = SongPartType.VERSO,
                    bars = listOf(
                        SongBar(listOf(d5, dSharp5, g)),
                        SongBar(listOf(a, d5, dSharp5)),
                        SongBar(listOf(g, a))
                    ),
                    lyrics = "Te llevo, clavada\n" +
                        "Como una espina en mi costado\n" +
                        "Entrando despacio, uoh uoh!"
                ),
                SongPart(
                    name = "Verso 3",
                    type = SongPartType.VERSO,
                    bars = listOf(
                        SongBar(listOf(d5, dSharp5, g)),
                        SongBar(listOf(a, d5, dSharp5)),
                        SongBar(listOf(g, a))
                    ),
                    lyrics = "Y quiero, que sepas\n" +
                        "Que eso está planeado, por amor\n" +
                        "Un poco de amor, nada más"
                ),
                SongPart(
                    name = "Coro (variación)",
                    type = SongPartType.CORO,
                    bars = listOf(
                        SongBar(listOf(g, a)),
                        SongBar(listOf(gOverB, aOverCSharp)),
                        SongBar(listOf(g, a, d, dSharp5)),
                        SongBar(listOf(d, dSharp5)),
                        SongBar(listOf(d, dSharp5, d, dSharp5))
                    ),
                    lyrics = "Puedo arrastrarme a tu puerta\n" +
                        "Puedo hacer que estés despierta\n" +
                        "Las mil y una noches porque estoy\n" +
                        "Atado a un sentimiento\n" +
                        "Solo estoy, atado a un sentimiento"
                ),
                SongPart(
                    name = "Solo 2",
                    type = SongPartType.SOLO,
                    bars = listOf(SongBar(listOf(g, a))),
                    lyrics = "[Solo]"
                ),
                SongPart(
                    name = "Estribillo final (repetitivo)",
                    type = SongPartType.CORO,
                    bars = listOf(
                        SongBar(listOf(d5, dSharp5, g)),
                        SongBar(listOf(a))
                    ),
                    lyrics = "Y vivo, atado\n" +
                        "A un sentimiento\n" +
                        "Y vivo, atado\n" +
                        "A un sentimiento\n" +
                        "Vivo, atado\n" +
                        "A un sentimiento",
                    repeatCount = 3
                ),
                SongPart(
                    name = "Estribillo final (cierre)",
                    type = SongPartType.CORO,
                    bars = listOf(
                        SongBar(listOf(d5, dSharp5, g)),
                        SongBar(listOf(a, d))
                    ),
                    lyrics = "Y vivo, atado\nA un sentimiento no uh no!"
                )
            )
        )
    }

    fun heyJudeSong(): Song {
        val f = SongChord(5, Chords.MAJOR)
        val c = SongChord(0, Chords.MAJOR)
        val c7 = SongChord(0, Chords.DOMINANT_7)
        val bb = SongChord(10, Chords.MAJOR)
        val eb = SongChord(3, Chords.MAJOR)
        val gm = SongChord(7, Chords.MINOR)
        val dm = SongChord(2, Chords.MINOR)
        val fOverA = SongChord(5, Chords.MAJOR, bassPc = 9)
        val dmOverA = SongChord(2, Chords.MINOR, bassPc = 9)

        fun bar(vararg chords: SongChord): SongBar = SongBar(chords.toList())

        return Song(
            id = HEY_JUDE_ID,
            title = "Hey Jude (Remastered 2015)",
            artist = "The Beatles",
            bpm = 75,
            parts = listOf(
                SongPart(
                    name = "Verso 1",
                    type = SongPartType.VERSO,
                    bars = listOf(
                        bar(f), bar(c), bar(c), bar(f),
                        bar(bb), bar(f), bar(c), bar(f)
                    )
                ),
                SongPart(
                    name = "Verso 2",
                    type = SongPartType.VERSO,
                    bars = listOf(
                        bar(f), bar(c), bar(c), bar(f),
                        bar(bb), bar(f), bar(c), bar(f), bar(f)
                    )
                ),
                SongPart(
                    name = "Puente 1",
                    type = SongPartType.PUENTE,
                    bars = listOf(
                        bar(bb, fOverA), bar(gm, f), bar(c), bar(f), bar(f),
                        bar(bb, fOverA), bar(gm, f), bar(c), bar(f)
                    )
                ),
                SongPart(
                    name = "Puente 2",
                    type = SongPartType.PUENTE,
                    bars = listOf(
                        bar(f, c), bar(c), bar(c, f), bar(f, c),
                        bar(c), bar(c, f), bar(f, bb), bar(bb, f),
                        bar(f, c), bar(c, f), bar(f)
                    )
                ),
                SongPart(
                    name = "Puente 3",
                    type = SongPartType.PUENTE,
                    bars = listOf(
                        bar(f, bb), bar(dmOverA, gm), bar(f, c), bar(c, f),
                        bar(f), bar(f, bb), bar(fOverA, gm)
                    )
                ),
                SongPart(
                    name = "Puente 4",
                    type = SongPartType.PUENTE,
                    bars = listOf(
                        bar(c), bar(c, f), bar(f), bar(c7), bar(c7),
                        bar(f), bar(c), bar(c7), bar(f)
                    )
                ),
                SongPart(
                    name = "Coro / Transición",
                    type = SongPartType.CORO,
                    bars = listOf(
                        bar(bb), bar(f), bar(c), bar(f), bar(f), bar(f)
                    )
                ),
                SongPart(
                    name = "Na na na 1",
                    type = SongPartType.OUTRO,
                    bars = listOf(
                        bar(eb), bar(bb), bar(f), bar(f),
                        bar(eb), bar(bb), bar(f), bar(f),
                        bar(eb), bar(bb), bar(f), bar(f),
                        bar(eb), bar(bb), bar(f), bar(f),
                        bar(eb), bar(bb)
                    )
                ),
                SongPart(
                    name = "Na na na 2",
                    type = SongPartType.OUTRO,
                    bars = listOf(
                        bar(f), bar(f), bar(eb), bar(bb),
                        bar(f), bar(f), bar(eb), bar(bb),
                        bar(f), bar(f), bar(eb), bar(bb),
                        bar(f), bar(f), bar(eb), bar(bb),
                        bar(f), bar(f), bar(eb), bar(bb),
                        bar(f), bar(f)
                    )
                ),
                SongPart(
                    name = "Na na na 3",
                    type = SongPartType.OUTRO,
                    bars = listOf(
                        bar(eb), bar(bb), bar(f), bar(f),
                        bar(eb), bar(bb), bar(f), bar(f),
                        bar(eb), bar(bb), bar(f), bar(f),
                        bar(eb), bar(bb), bar(f), bar(f),
                        bar(eb), bar(bb), bar(f), bar(f),
                        bar(eb), bar(bb), bar(f), bar(f),
                        bar(eb), bar(bb), bar(f), bar(f)
                    )
                ),
                SongPart(
                    name = "Final",
                    type = SongPartType.OUTRO,
                    bars = listOf(
                        bar(f), bar(f), bar(f), bar(f)
                    )
                )
            )
        )
    }

    fun yesterdaySong(): Song {
        val f = SongChord(5, Chords.MAJOR)
        val em = SongChord(4, Chords.MINOR)
        val em7 = SongChord(4, Chords.MINOR_7)
        val a = SongChord(9, Chords.MAJOR)
        val dm = SongChord(2, Chords.MINOR)
        val bb = SongChord(10, Chords.MAJOR)
        val c = SongChord(0, Chords.MAJOR)
        val g = SongChord(7, Chords.MAJOR)
        val em7b5 = SongChord(4, Chords.HALF_DIMINISHED)
        val a7 = SongChord(9, Chords.DOMINANT_7)
        val dm7 = SongChord(2, Chords.MINOR_7)
        val bbM7 = SongChord(10, Chords.MAJOR_7)
        val am = SongChord(9, Chords.MINOR)
        val gm = SongChord(7, Chords.MINOR)
        val c7 = SongChord(0, Chords.DOMINANT_7)
        val asus4 = SongChord(9, Chords.SUS_4)
        val bdim = SongChord(11, Chords.DIMINISHED)
        val dm7OnC = SongChord(2, Chords.MINOR_7, bassPc = 0)
        val amOnE = SongChord(9, Chords.MINOR, bassPc = 4)
        val cOnE = SongChord(0, Chords.MAJOR, bassPc = 4)

        fun bar(vararg chords: SongChord): SongBar = SongBar(chords.toList())

        return Song(
            id = YESTERDAY_ID,
            title = "Yesterday (Remastered 2015)",
            artist = "The Beatles",
            bpm = 96,
            parts = listOf(
                SongPart(
                    name = "Verso 1",
                    type = SongPartType.VERSO,
                    bars = listOf(
                        bar(f), bar(f), bar(f), bar(em, a),
                        bar(dm), bar(bb, c), bar(f)
                    )
                ),
                SongPart(
                    name = "Coro 1",
                    type = SongPartType.CORO,
                    bars = listOf(
                        bar(dm, g), bar(bb, f), bar(f),
                        bar(em7b5, a7), bar(dm, dm7OnC), bar(bb, c),
                        bar(f, amOnE)
                    )
                ),
                SongPart(
                    name = "Verso 2",
                    type = SongPartType.VERSO,
                    bars = listOf(
                        bar(dm7, g), bar(bb, f), bar(a7),
                        bar(dm, c, bbM7, dm), bar(gm, c7), bar(f),
                        bar(asus4, a7)
                    )
                ),
                SongPart(
                    name = "Coro 2",
                    type = SongPartType.CORO,
                    bars = listOf(
                        bar(dm, dm7OnC, bbM7, dm), bar(gm, c7), bar(f),
                        bar(f), bar(em7b5, a7), bar(dm, dm7OnC),
                        bar(bb, c)
                    )
                ),
                SongPart(
                    name = "Puente",
                    type = SongPartType.PUENTE,
                    bars = listOf(
                        bar(f, cOnE), bar(dm, g), bar(bb, f),
                        bar(em7, a), bar(dm, c, bbM7, dm),
                        bar(gm, c7), bar(f)
                    )
                ),
                SongPart(
                    name = "Verso 3",
                    type = SongPartType.VERSO,
                    bars = listOf(
                        bar(asus4, a), bar(dm, c, bbM7, am),
                        bar(gm, c7), bar(f), bar(f),
                        bar(em, a7), bar(dm)
                    )
                ),
                SongPart(
                    name = "Final",
                    type = SongPartType.OUTRO,
                    bars = listOf(
                        bar(bb, c), bar(f, amOnE), bar(dm, g),
                        bar(bb, f), bar(f, bdim), bar(bb, f),
                        bar(f), bar(f), bar(f)
                    )
                )
            )
        )
    }

    const val EXAMPLE_ID = "example-solo-importas-tu"
    private const val HEY_JUDE_ID = "example-hey-jude"
    private const val YESTERDAY_ID = "example-yesterday"
}

