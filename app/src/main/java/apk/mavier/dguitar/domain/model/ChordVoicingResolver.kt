package apk.mavier.dguitar.domain.model

data class VoicingKey(
    val stringIndex: Int,
    val fret: Int
)

data class ChordVoicing(
    val positions: Set<VoicingKey>,
    val barreFret: Int? = null,
    val barreAllStrings: Boolean = false
)

object ChordVoicingResolver {

    fun buildChordFormation(
        cfg: FretboardConfig,
        occurrence: CagedPatternOccurrence
    ): Set<VoicingKey> {
        val chordPitchClasses = cfg.chord.pitchClasses(cfg.rootPc)
        val strings = cfg.tuning.strings
        val base = HashSet<VoicingKey>()

        for (stringIndex in strings.indices) {
            val openMidi = strings[stringIndex].openMidi
            for (fret in occurrence.startFret..occurrence.endFret) {
                if (Pitch.pitchClass(openMidi + fret) in chordPitchClasses) {
                    base.add(VoicingKey(stringIndex, fret))
                    break
                }
            }
        }

        val barreFret = detectBarreFret(base) ?: return base

        val formation = HashSet<VoicingKey>()
        for (stringIndex in strings.indices) {
            val openMidi = strings[stringIndex].openMidi
            for (fret in barreFret..occurrence.endFret) {
                if (Pitch.pitchClass(openMidi + fret) in chordPitchClasses) {
                    formation.add(VoicingKey(stringIndex, fret))
                    break
                }
            }
        }
        return formation
    }

    fun bestDefaultOccurrence(
        cfg: FretboardConfig,
        occurrences: List<CagedPatternOccurrence> = CagedPatterns.occurrences
    ): CagedPatternOccurrence? {
        var best: CagedPatternOccurrence? = null
        var bestSize = 0

        for (occurrence in occurrences) {
            val size = buildChordFormation(cfg, occurrence).size
            if (size > bestSize) {
                bestSize = size
                best = occurrence
            }
        }
        return best
    }

    fun currentChordFormation(cfg: FretboardConfig, centerFret: Float): Set<VoicingKey> {
        if (cfg.mode != ViewMode.CHORD) return emptySet()

        val occurrence = if (cfg.chordPattern != null) {
            activeChordOccurrence(cfg, centerFret)
        } else {
            bestDefaultOccurrence(cfg)
        }
        if (occurrence != null) {
            val formation = buildChordFormation(cfg, occurrence)
            if (formation.isNotEmpty()) return formation
        }

        val fallback = bestDefaultOccurrence(cfg) ?: return emptySet()
        return buildChordFormation(cfg, fallback)
    }

    fun voicing(cfg: FretboardConfig, centerFret: Float): ChordVoicing {
        if (cfg.mode != ViewMode.CHORD) return ChordVoicing(emptySet())

        if (cfg.chordPattern == null && cfg.tuning == Tunings.STANDARD) {
            val standard = StandardChordShapes.shapeFor(cfg.rootPc, cfg.chord)
            if (standard != null) {
                val positions = HashSet<VoicingKey>()
                for (lowIndex in standard.fretsLowToHigh.indices) {
                    val fret = standard.fretsLowToHigh[lowIndex] ?: continue
                    val stringIndex = 5 - lowIndex
                    if (stringIndex in cfg.tuning.strings.indices) {
                        positions.add(VoicingKey(stringIndex, fret))
                    }
                }
                if (positions.isNotEmpty()) {
                    return ChordVoicing(
                        positions = positions,
                        barreFret = standard.barreFret,
                        barreAllStrings = standard.barreFret != null
                    )
                }
            }
        }

        val heuristic = currentChordFormation(cfg, centerFret)
        return ChordVoicing(
            positions = heuristic,
            barreFret = detectBarreFret(heuristic),
            barreAllStrings = false
        )
    }

    fun detectBarreFret(formation: Set<VoicingKey>): Int? {
        var best = -1
        for ((fret, positions) in formation.groupBy { it.fret }) {
            if (fret <= 0 || positions.size < 3) continue

            val indexes = positions.map { it.stringIndex }
            val minString = indexes.minOrNull() ?: continue
            val maxString = indexes.maxOrNull() ?: continue
            if (maxString - minString >= 4 && (best == -1 || fret < best)) {
                best = fret
            }
        }
        return if (best > 0) best else null
    }

    fun activeChordOccurrence(
        cfg: FretboardConfig,
        centerFret: Float
    ): CagedPatternOccurrence? {
        val pattern = cfg.chordPattern ?: return null
        val occurrences = CagedPatterns.occurrencesFor(pattern)
        if (occurrences.isEmpty()) return null
        if (occurrences.size == 1) return occurrences.first()

        return occurrences.firstOrNull {
            centerFret >= it.startFret && centerFret <= it.endFret
        } ?: occurrences.minByOrNull { occurrence ->
            when {
                centerFret < occurrence.startFret -> occurrence.startFret - centerFret
                centerFret > occurrence.endFret -> centerFret - occurrence.endFret
                else -> 0f
            }
        }
    }
}
