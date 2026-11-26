package apk.mavier.dguitar.domain.usecase

import apk.mavier.dguitar.domain.model.ChromaticVariant
import apk.mavier.dguitar.domain.model.ModulationAvailability
import apk.mavier.dguitar.domain.model.ModulationBridge
import apk.mavier.dguitar.domain.model.ModulationChord
import apk.mavier.dguitar.domain.model.ModulationScaleMap
import apk.mavier.dguitar.domain.model.ModulationTheory
import apk.mavier.dguitar.domain.model.ModulationType

class BuildModulationUseCase {

    fun distance(originPc: Int, destinationPc: Int): Int =
        ModulationTheory.distance(originPc, destinationPc)

    fun availability(originPc: Int, destinationPc: Int): ModulationAvailability =
        ModulationTheory.availability(originPc, destinationPc)

    fun bridge(
        originPc: Int,
        destinationPc: Int,
        type: ModulationType,
        chromaticVariant: ChromaticVariant? = null
    ): ModulationBridge = ModulationTheory.bridge(
        originPc,
        destinationPc,
        type,
        chromaticVariant
    )

    fun scaleMap(originPc: Int, destinationPc: Int): ModulationScaleMap =
        ModulationTheory.scaleMap(originPc, destinationPc)

    fun cadence(rootPc: Int): List<ModulationChord> = ModulationTheory.cadence(rootPc)
}
