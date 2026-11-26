package apk.mavier.dguitar.domain.usecase

import apk.mavier.dguitar.domain.model.GuidedProgression
import apk.mavier.dguitar.domain.model.GuidedProgressions
import apk.mavier.dguitar.domain.model.ImprovProgression
import apk.mavier.dguitar.domain.model.ImprovSection
import apk.mavier.dguitar.domain.model.Pitch

class BuildGuidedProgressionUseCase {

    fun all(): List<GuidedProgression> = GuidedProgressions.all

    fun byId(id: String): GuidedProgression? = GuidedProgressions.byId(id)

    fun sections(progression: GuidedProgression, keyRootPc: Int): List<ImprovSection> =
        listOf(ImprovSection(progression.category, progression.improvChords(keyRootPc)))

    fun progressionId(progression: GuidedProgression, keyRootPc: Int): String =
        "guided-${progression.id}-${((keyRootPc % 12) + 12) % 12}"

    fun buildProgression(
        progression: GuidedProgression,
        keyRootPc: Int,
        useFlats: Boolean
    ): ImprovProgression = ImprovProgression(
        id = progressionId(progression, keyRootPc),
        name = "${progression.name} · ${Pitch.name(keyRootPc, useFlats)}",
        sections = sections(progression, keyRootPc)
    )
}
