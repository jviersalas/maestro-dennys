package apk.mavier.dguitar.domain.usecase

import apk.mavier.dguitar.domain.model.ChordQuality
import apk.mavier.dguitar.domain.model.CircleHighlight
import apk.mavier.dguitar.domain.model.CircleKey
import apk.mavier.dguitar.domain.model.CircleOfFifths
import apk.mavier.dguitar.domain.model.GuidedProgression
import apk.mavier.dguitar.domain.model.HarmonicChord

class BuildCircleOfFifthsUseCase {

    fun keys(): List<CircleKey> = CircleOfFifths.keys

    fun key(index: Int): CircleKey = CircleOfFifths.byIndex(index)

    fun indexForMajorPc(pitchClass: Int): Int = CircleOfFifths.indexForMajorPc(pitchClass)

    fun diatonicChords(key: CircleKey, minor: Boolean): List<HarmonicChord> =
        CircleOfFifths.diatonicChords(key, minor)

    fun romanFor(degreeIndex: Int, quality: ChordQuality): String =
        CircleOfFifths.romanFor(degreeIndex, quality)

    fun progressions(minor: Boolean): List<GuidedProgression> =
        CircleOfFifths.progressions(minor)

    fun highlight(key: CircleKey, minor: Boolean): CircleHighlight =
        CircleOfFifths.highlight(key, minor)
}
