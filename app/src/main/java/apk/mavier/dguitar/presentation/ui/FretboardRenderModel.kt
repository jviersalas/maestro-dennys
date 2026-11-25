package apk.mavier.dguitar.presentation.ui

import apk.mavier.dguitar.domain.model.CagedPatternOccurrence
import apk.mavier.dguitar.domain.model.CagedPatterns
import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.HarmonicChord
import apk.mavier.dguitar.domain.model.HarmonicCircle
import apk.mavier.dguitar.domain.model.ViewMode
import apk.mavier.dguitar.presentation.theme.ChordColorProvider

data class ChordHit(
    val rootPc: Int,
    val role: Int,
    val color: Int,
    val degree: Int
)

class FretboardRenderModel private constructor(
    val overlayChords: List<HarmonicChord>,
    val scalePitchClasses: Set<Int>,
    val chordPitchClasses: Set<Int>,
    val selectedOccurrences: List<CagedPatternOccurrence>,
    private val hitsByPitchClass: Map<Int, List<ChordHit>>
) {

    fun hitsFor(pitchClass: Int): List<ChordHit> =
        hitsByPitchClass[pitchClass] ?: emptyList()

    companion object {

        fun build(config: FretboardConfig): FretboardRenderModel {
            val overlayChords = HarmonicCircle.effectiveChords(config)
            val selectedOccurrences = if (
                config.mode == ViewMode.SCALE && config.selectedPattern != null
            ) {
                CagedPatterns.occurrencesFor(config.selectedPattern)
            } else {
                emptyList()
            }

            val hits = HashMap<Int, List<ChordHit>>()
            if (overlayChords.isNotEmpty()) {
                for (pitchClass in 0..11) {
                    val list = ArrayList<ChordHit>(overlayChords.size)
                    for (chord in overlayChords) {
                        val role = chord.quality.roleOf(chord.rootPc, pitchClass) ?: continue
                        list.add(
                            ChordHit(
                                rootPc = chord.rootPc,
                                role = role,
                                color = ChordColorProvider.shadeForRole(
                                    ChordColorProvider.baseColor(chord.rootPc),
                                    role
                                ),
                                degree = chord.degree
                            )
                        )
                    }
                    if (list.isNotEmpty()) hits[pitchClass] = list
                }
            }

            return FretboardRenderModel(
                overlayChords = overlayChords,
                scalePitchClasses = config.scale.pitchClasses(config.rootPc),
                chordPitchClasses = config.chord.pitchClasses(config.rootPc),
                selectedOccurrences = selectedOccurrences,
                hitsByPitchClass = hits
            )
        }
    }
}
