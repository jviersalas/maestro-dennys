package apk.mavier.dguitar.domain.model

import kotlin.math.abs

enum class ModulationType { DIRECT, PIVOT, CHROMATIC }

enum class ChromaticVariant { ORIGIN_IMPULSE, DESTINATION_DOMINANT }

enum class ModulationSignal { NEAR, MEDIUM, FAR }

enum class NoteRelation { COMMON, ORIGIN_ONLY, DESTINATION_ONLY }

data class ModulationChord(
    val rootPc: Int,
    val quality: ChordQuality
) {
    fun label(useFlats: Boolean): String = Pitch.name(rootPc, useFlats) + quality.symbol
}

data class ModulationStep(
    val chord: ModulationChord,
    val originRoman: String?,
    val destinationRoman: String?,
    val isPivot: Boolean,
    val belongsToOrigin: Boolean,
    val belongsToDestination: Boolean,
    val isChangePoint: Boolean
) {
    val rootPc: Int
        get() = chord.rootPc

    val quality: ChordQuality
        get() = chord.quality

    fun label(useFlats: Boolean): String = chord.label(useFlats)
}

data class ModulationBridge(
    val originPc: Int,
    val destinationPc: Int,
    val type: ModulationType,
    val chromaticVariant: ChromaticVariant?,
    val distance: Int,
    val signal: ModulationSignal,
    val steps: List<ModulationStep>,
    val pivotOriginRoman: String? = null,
    val pivotDestinationRoman: String? = null
) {
    val changeIndex: Int
        get() {
            val explicit = steps.indexOfFirst { it.isChangePoint }
            return if (explicit >= 0) explicit else steps.lastIndex.coerceAtLeast(0)
        }

    val changeStep: ModulationStep?
        get() = steps.getOrNull(changeIndex)
}

data class ModulationAvailability(
    val pivotAvailable: Boolean,
    val pivotOriginRoman: String? = null,
    val pivotDestinationRoman: String? = null
)

data class ModulationScaleMap(
    val originPcs: Set<Int>,
    val destinationPcs: Set<Int>
) {
    val commonPcs: Set<Int>
        get() = originPcs.intersect(destinationPcs)

    val originOnlyPcs: Set<Int>
        get() = originPcs - destinationPcs

    val destinationOnlyPcs: Set<Int>
        get() = destinationPcs - originPcs

    fun relationOf(pitchClass: Int): NoteRelation? {
        val pc = normalizePitchClass(pitchClass)
        val origin = pc in originPcs
        val destination = pc in destinationPcs
        return when {
            origin && destination -> NoteRelation.COMMON
            origin -> NoteRelation.ORIGIN_ONLY
            destination -> NoteRelation.DESTINATION_ONLY
            else -> null
        }
    }
}

object ModulationTheory {

    private val major = Scales.MAJOR
    private val majorTriad = Chords.MAJOR
    private val dominantSeventh = Chords.DOMINANT_7

    fun distance(originPc: Int, destinationPc: Int): Int {
        val a = circleIndex(originPc)
        val b = circleIndex(destinationPc)
        val raw = abs(a - b)
        return minOf(raw, 12 - raw)
    }

    fun signalFor(distance: Int): ModulationSignal = when {
        distance <= 1 -> ModulationSignal.NEAR
        distance <= 3 -> ModulationSignal.MEDIUM
        else -> ModulationSignal.FAR
    }

    fun availability(originPc: Int, destinationPc: Int): ModulationAvailability {
        val pivot = findPivot(originPc, destinationPc)
        return ModulationAvailability(
            pivotAvailable = pivot != null,
            pivotOriginRoman = pivot?.originRoman,
            pivotDestinationRoman = pivot?.destinationRoman
        )
    }

    fun bridge(
        originPc: Int,
        destinationPc: Int,
        type: ModulationType,
        chromaticVariant: ChromaticVariant? = null
    ): ModulationBridge {
        val origin = normalizePitchClass(originPc)
        val destination = normalizePitchClass(destinationPc)
        val distance = distance(origin, destination)

        if (origin == destination) {
            val step = step(
                ModulationChord(origin, majorTriad),
                origin,
                destination,
                isChangePoint = false
            )
            return ModulationBridge(
                originPc = origin,
                destinationPc = destination,
                type = type,
                chromaticVariant = null,
                distance = 0,
                signal = ModulationSignal.NEAR,
                steps = listOf(step)
            )
        }

        val pivot = if (type == ModulationType.PIVOT) {
            findPivot(origin, destination)
        } else {
            null
        }

        val steps = when {
            type == ModulationType.PIVOT && pivot != null -> pivotSteps(origin, destination, pivot)
            type == ModulationType.CHROMATIC -> chromaticSteps(
                origin,
                destination,
                chromaticVariant ?: ChromaticVariant.ORIGIN_IMPULSE
            )
            else -> directSteps(origin, destination)
        }

        return ModulationBridge(
            originPc = origin,
            destinationPc = destination,
            type = if (type == ModulationType.PIVOT && pivot == null) {
                ModulationType.DIRECT
            } else {
                type
            },
            chromaticVariant = if (type == ModulationType.CHROMATIC) {
                chromaticVariant ?: ChromaticVariant.ORIGIN_IMPULSE
            } else {
                null
            },
            distance = distance,
            signal = signalFor(distance),
            steps = steps,
            pivotOriginRoman = pivot?.originRoman,
            pivotDestinationRoman = pivot?.destinationRoman
        )
    }

    fun scaleMap(originPc: Int, destinationPc: Int): ModulationScaleMap {
        val origin = normalizePitchClass(originPc)
        val destination = normalizePitchClass(destinationPc)
        return ModulationScaleMap(
            originPcs = major.pitchClasses(origin),
            destinationPcs = major.pitchClasses(destination)
        )
    }

    fun cadence(rootPc: Int): List<ModulationChord> {
        val root = normalizePitchClass(rootPc)
        return listOf(
            ModulationChord(root, majorTriad),
            ModulationChord(normalizePitchClass(root + 5), majorTriad),
            ModulationChord(normalizePitchClass(root + 7), dominantSeventh),
            ModulationChord(root, majorTriad)
        )
    }

    private fun directSteps(originPc: Int, destinationPc: Int): List<ModulationStep> =
        markChange(
            listOf(
                step(ModulationChord(originPc, majorTriad), originPc, destinationPc),
                step(ModulationChord(destinationPc, majorTriad), originPc, destinationPc)
            )
        )

    private fun pivotSteps(
        originPc: Int,
        destinationPc: Int,
        pivot: PivotCandidate
    ): List<ModulationStep> {
        val dominant = ModulationChord(
            normalizePitchClass(destinationPc + 7),
            dominantSeventh
        )
        val pivotChord = ModulationChord(pivot.rootPc, pivot.quality)
        val steps = mutableListOf(
            step(ModulationChord(originPc, majorTriad), originPc, destinationPc),
            step(pivotChord, originPc, destinationPc, isPivot = true),
            step(dominant, originPc, destinationPc),
            step(ModulationChord(destinationPc, majorTriad), originPc, destinationPc)
        )
        return markChange(steps)
    }

    private fun chromaticSteps(
        originPc: Int,
        destinationPc: Int,
        variant: ChromaticVariant
    ): List<ModulationStep> {
        val approachRoot = when (variant) {
            ChromaticVariant.ORIGIN_IMPULSE ->
                normalizePitchClass(originPc + 7)
            ChromaticVariant.DESTINATION_DOMINANT ->
                normalizePitchClass(destinationPc + 7)
        }
        return markChange(
            listOf(
                step(ModulationChord(originPc, majorTriad), originPc, destinationPc),
                step(ModulationChord(approachRoot, dominantSeventh), originPc, destinationPc),
                step(ModulationChord(destinationPc, majorTriad), originPc, destinationPc)
            )
        )
    }

    private fun step(
        chord: ModulationChord,
        originPc: Int,
        destinationPc: Int,
        isPivot: Boolean = false,
        isChangePoint: Boolean = false
    ): ModulationStep {
        val originRoman = findDiatonicRoman(originPc, chord.rootPc, chord.quality)
        val destinationRoman = findDiatonicRoman(destinationPc, chord.rootPc, chord.quality)
        return ModulationStep(
            chord = chord,
            originRoman = originRoman,
            destinationRoman = destinationRoman,
            isPivot = isPivot,
            belongsToOrigin = originRoman != null,
            belongsToDestination = destinationRoman != null,
            isChangePoint = isChangePoint
        )
    }

    private fun markChange(steps: List<ModulationStep>): List<ModulationStep> {
        if (steps.isEmpty()) return steps
        val firstDestinationOnly = steps.indexOfFirst {
            it.belongsToDestination && !it.belongsToOrigin
        }
        val index = if (firstDestinationOnly >= 0) firstDestinationOnly else steps.lastIndex
        return steps.mapIndexed { position, item ->
            item.copy(isChangePoint = position == index)
        }
    }

    private fun findPivot(originPc: Int, destinationPc: Int): PivotCandidate? {
        val origin = normalizePitchClass(originPc)
        val destination = normalizePitchClass(destinationPc)
        if (origin == destination) return null

        val originChords = HarmonicCircle.chords(major, origin)
        val destinationChords = HarmonicCircle.chords(major, destination)

        return originChords.mapNotNull { originChord ->
            if (originChord.rootPc == origin) return@mapNotNull null
            val destinationChord = destinationChords.firstOrNull {
                it.rootPc == originChord.rootPc &&
                    it.quality.symbol == originChord.quality.symbol
            } ?: return@mapNotNull null
            if (destinationChord.rootPc == destination) return@mapNotNull null

            val originRoman = romanForChord(originChord.degree, originChord.quality)
            val destinationRoman = romanForChord(destinationChord.degree, destinationChord.quality)
            PivotCandidate(
                rootPc = originChord.rootPc,
                quality = originChord.quality,
                originRoman = originRoman,
                destinationRoman = destinationRoman,
                rank = pivotRank(destinationRoman)
            )
        }.minWithOrNull(compareBy<PivotCandidate>({ it.rank }, { it.rootPc }))
    }

    private fun pivotRank(roman: String): Int = when (roman.lowercase()) {
        "ii" -> 0
        "vi" -> 1
        "iv" -> 2
        "iii" -> 3
        else -> 4
    }

    private fun findDiatonicRoman(
        keyRootPc: Int,
        rootPc: Int,
        quality: ChordQuality
    ): String? {
        val root = normalizePitchClass(rootPc)
        val triads = HarmonicCircle.chords(major, keyRootPc)
        val sevenths = HarmonicCircle.chords(major, keyRootPc, sevenths = true)
        val match = (triads + sevenths).firstOrNull {
            it.rootPc == root && it.quality.symbol == quality.symbol
        } ?: return null
        return romanForChord(match.degree, match.quality)
    }

    private fun romanForChord(degree: Int, quality: ChordQuality): String {
        val base = CircleOfFifths.romanFor(degree - 1, quality)
        return when (quality.symbol) {
            "7" -> "${base}7"
            "maj7" -> "${base}maj7"
            else -> base
        }
    }

    private fun circleIndex(pitchClass: Int): Int {
        val index = CircleOfFifths.indexForMajorPc(pitchClass)
        return if (index >= 0) index else 0
    }

    private data class PivotCandidate(
        val rootPc: Int,
        val quality: ChordQuality,
        val originRoman: String,
        val destinationRoman: String,
        val rank: Int
    )
}

private fun normalizePitchClass(value: Int): Int = ((value % 12) + 12) % 12
