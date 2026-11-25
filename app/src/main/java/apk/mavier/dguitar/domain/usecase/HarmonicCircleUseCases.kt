package apk.mavier.dguitar.domain.usecase

import apk.mavier.dguitar.domain.model.FretboardConfig
import apk.mavier.dguitar.domain.model.HarmonicChord
import apk.mavier.dguitar.domain.model.HarmonicCircle
import apk.mavier.dguitar.domain.model.ScaleMode

class BuildHarmonicCircleUseCase {

    fun supports(scale: ScaleMode): Boolean = HarmonicCircle.supports(scale)

    fun chords(
        scale: ScaleMode,
        rootPc: Int,
        sevenths: Boolean
    ): List<HarmonicChord> = HarmonicCircle.chords(scale, rootPc, sevenths)

    fun effective(config: FretboardConfig): List<HarmonicChord> =
        HarmonicCircle.effectiveChords(config)
}
