package apk.mavier.dguitar.domain.model

data class ChordTone(
    val semitone: Int,
    val degree: String,
    val intervalName: String
)

data class ChordDiagramString(
    val fret: Int?,
    val noteName: String,
    val solfege: String,
    val roleSemitone: Int?
)

data class ChordDiagramData(
    val rootPc: Int,
    val title: String,
    val subtitle: String,
    val strings: List<ChordDiagramString>,
    val baseFret: Int,
    val fretSpan: Int,
    val barreFret: Int?,
    val notesSolfege: List<String>,
    val notesAnglo: List<String>
)

/**
 * Turns a [ChordQuality] into the information the chord guide shows: the notes
 * that build the chord, how it is named and written, and a compact diagram.
 */
object ChordTheory {

    fun tones(quality: ChordQuality): List<ChordTone> =
        quality.intervals.distinct().map { semitone ->
            ChordTone(
                semitone = semitone,
                degree = degreeLabel(quality, semitone),
                intervalName = intervalName(quality, semitone)
            )
        }

    private fun isAugmented(quality: ChordQuality): Boolean =
        quality === Chords.AUGMENTED ||
            quality.symbol == "aug" ||
            quality.symbol.contains("#5")

    private fun isDiminishedSeventh(quality: ChordQuality): Boolean =
        quality.symbol == "dim7"

    private fun degreeLabel(quality: ChordQuality, semitone: Int): String {
        if (semitone >= 12) {
            return when (semitone) {
                12 -> "8"
                13 -> "b9"
                14 -> "9"
                15 -> "#9"
                16 -> "10"
                17 -> "11"
                18 -> "#11"
                19 -> "12"
                20 -> "b13"
                21 -> "13"
                else -> "?"
            }
        }
        val normalized = ((semitone % 12) + 12) % 12
        if (normalized == 8 && isAugmented(quality)) return "#5"
        if (normalized == 9 && isDiminishedSeventh(quality)) return "bb7"
        return when (normalized) {
            0 -> "1"
            1 -> "b2"
            2 -> "2"
            3 -> "b3"
            4 -> "3"
            5 -> "4"
            6 -> "b5"
            7 -> "5"
            8 -> "b6"
            9 -> "6"
            10 -> "b7"
            11 -> "7"
            else -> "?"
        }
    }

    private fun intervalName(quality: ChordQuality, semitone: Int): String {
        if (semitone >= 12) {
            return when (semitone) {
                12 -> "Octava"
                13 -> "Novena menor"
                14 -> "Novena mayor"
                15 -> "Novena aumentada"
                16 -> "Décima mayor"
                17 -> "Oncena justa"
                18 -> "Oncena aumentada"
                20 -> "Trecena menor"
                21 -> "Trecena mayor"
                else -> "Extensión"
            }
        }
        val normalized = ((semitone % 12) + 12) % 12
        if (normalized == 8 && isAugmented(quality)) return "Quinta aumentada"
        if (normalized == 9 && isDiminishedSeventh(quality)) return "Séptima disminuida"
        return when (normalized) {
            0 -> "Tónica"
            1 -> "Segunda menor"
            2 -> "Segunda mayor"
            3 -> "Tercera menor"
            4 -> "Tercera mayor"
            5 -> "Cuarta justa"
            6 -> "Quinta disminuida"
            7 -> "Quinta justa"
            8 -> "Sexta menor"
            9 -> "Sexta mayor"
            10 -> "Séptima menor"
            11 -> "Séptima mayor"
            else -> "Intervalo"
        }
    }

    fun displayName(rootPc: Int, quality: ChordQuality, useFlats: Boolean): String =
        "${Pitch.solfegeName(rootPc, useFlats)} ${qualityName(quality)}"

    fun qualityName(quality: ChordQuality): String = when (quality.symbol) {
        "" -> "mayor"
        "m" -> "menor"
        "5" -> "quinta (power)"
        "7" -> "séptima dominante"
        "maj7" -> "séptima mayor"
        "m7" -> "menor séptima"
        "dim" -> "disminuido"
        "dim7" -> "disminuido séptima"
        "m7b5" -> "semidisminuido"
        "aug" -> "aumentado"
        "sus2" -> "suspendido 2"
        "sus4" -> "suspendido 4"
        "6" -> "sexta"
        "m6" -> "menor sexta"
        "add9" -> "novena añadida"
        "9" -> "novena"
        "m9" -> "menor novena"
        "maj9" -> "novena mayor"
        "mMaj7" -> "menor con séptima mayor"
        "maj7#5" -> "séptima mayor aumentada"
        "7#5" -> "séptima aumentada"
        "dimMaj7" -> "disminuido con séptima mayor"
        "7sus2" -> "séptima suspendida 2"
        "7sus4" -> "séptima suspendida 4"
        else -> quality.name.lowercase()
    }

    fun formationText(rootPc: Int, quality: ChordQuality, useFlats: Boolean): String {
        val rootSolfege = Pitch.solfegeName(rootPc, useFlats)
        val rootName = Pitch.name(rootPc, useFlats)
        val tones = tones(quality)
        val formula = tones.joinToString(" · ") { it.degree }
        val notes = tones.joinToString(" · ") {
            Pitch.solfegeName(((rootPc + it.semitone) % 12 + 12) % 12, useFlats)
        }
        return "Se parte de la nota raíz $rootSolfege ($rootName) y se aplica la fórmula " +
            "$formula. Eso da las notas $notes."
    }

    fun namingText(rootPc: Int, quality: ChordQuality, useFlats: Boolean): String {
        val rootName = Pitch.name(rootPc, useFlats)
        val rootSolfege = Pitch.solfegeName(rootPc, useFlats)
        val symbol = rootName + quality.symbol
        val display = displayName(rootPc, quality, useFlats)
        val rule = "El nombre tiene dos partes: la nota raíz y la calidad. La raíz va en " +
            "mayúscula y la calidad se escribe como sufijo."
        val detail = when {
            quality.symbol.isEmpty() ->
                "$rootName es mayor, así que no lleva sufijo: se escribe $symbol y se lee " +
                    "«$display». $rootName se llama $rootSolfege."
            quality.symbol == "m" ->
                "$symbol usa el sufijo m (menor): se lee «$display». $rootName se llama " +
                    "$rootSolfege."
            else ->
                "$symbol usa el sufijo ${quality.symbol}: se lee «$display». $rootName se " +
                    "llama $rootSolfege."
        }
        return "$rule\n$detail"
    }

    fun notesText(rootPc: Int, quality: ChordQuality, useFlats: Boolean): String =
        tones(quality).joinToString("\n") { tone ->
            val pc = ((rootPc + tone.semitone) % 12 + 12) % 12
            "• ${Pitch.solfegeName(pc, useFlats)} (${Pitch.name(pc, useFlats)}) — " +
                "${tone.intervalName} (${tone.degree})"
        }

    fun diagram(
        rootPc: Int,
        quality: ChordQuality,
        tuning: Tuning,
        useFlats: Boolean
    ): ChordDiagramData {
        val voicing = voicing(rootPc, quality, tuning)
        val tones = tones(quality)
        val notes = tones.map { tone ->
            val pc = ((rootPc + tone.semitone) % 12 + 12) % 12
            Pitch.solfegeName(pc, useFlats) to Pitch.name(pc, useFlats)
        }

        val fretted = voicing.frets.values.filter { it > 0 }
        val minFret = fretted.minOrNull() ?: 0
        val maxFret = fretted.maxOrNull() ?: 0
        val hasOpen = voicing.frets.values.any { it == 0 }
        val baseFret = if (hasOpen || minFret <= 1) 1 else minFret
        val fretSpan = if (fretted.isEmpty()) 4 else (maxFret - baseFret + 1).coerceIn(4, 5)

        val strings = ArrayList<ChordDiagramString>(tuning.strings.size)
        for (stringIndex in tuning.strings.indices.reversed()) {
            val fret = voicing.frets[stringIndex]
            if (fret == null) {
                strings.add(ChordDiagramString(null, "x", "x", null))
            } else {
                val pc = Pitch.pitchClass(tuning.strings[stringIndex].openMidi + fret)
                strings.add(
                    ChordDiagramString(
                        fret = fret,
                        noteName = Pitch.name(pc, useFlats),
                        solfege = Pitch.solfegeName(pc, useFlats),
                        roleSemitone = quality.roleOf(rootPc, pc)
                    )
                )
            }
        }

        return ChordDiagramData(
            rootPc = rootPc,
            title = Pitch.name(rootPc, useFlats) + quality.symbol,
            subtitle = displayName(rootPc, quality, useFlats),
            strings = strings,
            baseFret = baseFret,
            fretSpan = fretSpan,
            barreFret = voicing.barreFret,
            notesSolfege = notes.map { it.first },
            notesAnglo = notes.map { it.second }
        )
    }

    private data class Voicing(
        val frets: Map<Int, Int>,
        val barreFret: Int?
    )

    private fun voicing(rootPc: Int, quality: ChordQuality, tuning: Tuning): Voicing {
        if (tuning == Tunings.STANDARD) {
            val standard = StandardChordShapes.shapeFor(rootPc, quality)
            if (standard != null) {
                val frets = HashMap<Int, Int>()
                for (lowIndex in standard.fretsLowToHigh.indices) {
                    val fret = standard.fretsLowToHigh[lowIndex] ?: continue
                    frets[5 - lowIndex] = fret
                }
                return Voicing(frets, standard.barreFret)
            }
        }

        val config = FretboardConfig(
            tuning = tuning,
            mode = ViewMode.CHORD,
            rootPc = rootPc,
            chord = quality
        )
        val occurrence = ChordVoicingResolver.bestDefaultOccurrence(config)
            ?: return Voicing(emptyMap(), null)
        val formation = ChordVoicingResolver.buildChordFormation(config, occurrence)
        return Voicing(
            frets = formation.associate { it.stringIndex to it.fret },
            barreFret = ChordVoicingResolver.detectBarreFret(formation)
        )
    }
}
