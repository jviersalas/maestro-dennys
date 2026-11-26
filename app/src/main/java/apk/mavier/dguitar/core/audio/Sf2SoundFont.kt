package apk.mavier.dguitar.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.pow

data class Sf2Sample(
    val name: String,
    val sampleRate: Int,
    val originalKey: Int,
    val pitchCorrection: Int,
    val start: Int,
    val end: Int,
    val loopStart: Int,
    val loopEnd: Int,
    val pcm: ShortBuffer
) {
    fun amplitudeAt(index: Int): Float {
        if (index < start || index >= end) return 0f
        return pcm.get(index) / 32768f
    }
}

data class Sf2Zone(
    val keyLow: Int,
    val keyHigh: Int,
    val velocityLow: Int,
    val velocityHigh: Int,
    val sample: Sf2Sample,
    val rootKey: Int,
    val coarseTune: Int,
    val fineTune: Int,
    val attenuationCb: Int,
    val pan: Int,
    val sampleModes: Int,
    val start: Int,
    val end: Int,
    val loopStart: Int,
    val loopEnd: Int,
    val attackTc: Int,
    val holdTc: Int,
    val decayTc: Int,
    val sustainCb: Int,
    val releaseTc: Int
) {
    val loops: Boolean
        get() = (sampleModes and 0x03) != 0 &&
            loopEnd - loopStart >= MIN_LOOP_FRAMES &&
            loopStart >= start &&
            loopEnd <= end

    fun matches(key: Int, velocity: Int): Boolean =
        key in keyLow..keyHigh && velocity in velocityLow..velocityHigh

    fun playbackRatio(midi: Int, outputRate: Int): Double {
        val semitones = (midi - rootKey) + coarseTune +
            (fineTune + sample.pitchCorrection) / 100.0
        return 2.0.pow(semitones / 12.0) * (sample.sampleRate.toDouble() / outputRate)
    }

    companion object {
        const val MIN_LOOP_FRAMES = 512
    }
}

data class Sf2Preset(
    val name: String,
    val bank: Int,
    val program: Int,
    val zones: List<Sf2Zone>
)

class Sf2SoundFont(
    val presets: List<Sf2Preset>,
    val metadata: Map<String, String>
) {

    fun preset(bank: Int = 0, program: Int = 0): Sf2Preset? =
        presets.firstOrNull { it.bank == bank && it.program == program }
            ?: presets.firstOrNull()

    fun zonesFor(
        midi: Int,
        velocity: Int,
        bank: Int = 0,
        program: Int = 0
    ): List<Sf2Zone> {
        val preset = preset(bank, program) ?: return emptyList()
        val matches = preset.zones.filter { it.matches(midi, velocity) }
        if (matches.isNotEmpty()) return matches.take(MAX_LAYERS)
        val nearest = preset.zones.minByOrNull { keyDistance(it, midi) }
        return listOfNotNull(nearest)
    }

    private fun keyDistance(zone: Sf2Zone, midi: Int): Int = when {
        midi < zone.keyLow -> zone.keyLow - midi
        midi > zone.keyHigh -> midi - zone.keyHigh
        else -> 0
    }

    companion object {
        const val MAX_LAYERS = 4

        fun parse(bytes: ByteArray): Sf2SoundFont {
            require(bytes.size >= 12) { "SoundFont demasiado pequeño" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(readTag(buffer) == "RIFF") { "El archivo no es RIFF" }
            readUInt32(buffer)
            require(readTag(buffer) == "sfbk") { "El archivo no es SoundFont 2" }

            val metadata = mutableMapOf<String, String>()
            val pdta = mutableMapOf<String, ByteArray>()
            var pcm: ShortBuffer? = null

            while (buffer.remaining() >= 8) {
                val id = readTag(buffer)
                val size = readUInt32(buffer).toInt()
                if (size < 0) break
                val dataStart = buffer.position()
                val dataEnd = dataStart + size
                if (dataEnd > buffer.limit()) break

                if (id == "LIST") {
                    val listType = readTag(buffer)
                    when (listType) {
                        "INFO" -> parseInfo(buffer, dataEnd, metadata)
                        "sdta" -> pcm = parseSdta(buffer, dataEnd, bytes)
                        "pdta" -> parsePdta(buffer, dataEnd, pdta)
                    }
                    buffer.position(dataEnd)
                } else {
                    buffer.position(dataEnd)
                }
                if ((size and 1) == 1 && buffer.position() < buffer.limit()) {
                    buffer.position(buffer.position() + 1)
                }
            }

            val sampleData = pcm ?: ShortBuffer.allocate(0)
            val presets = buildPresets(pdta, sampleData)
            return Sf2SoundFont(presets, metadata)
        }

        private fun parseInfo(
            buffer: ByteBuffer,
            end: Int,
            out: MutableMap<String, String>
        ) {
            while (buffer.position() + 8 <= end) {
                val id = readTag(buffer)
                val size = readUInt32(buffer).toInt()
                val dataStart = buffer.position()
                val dataEnd = dataStart + size
                if (dataEnd > end) break
                val data = ByteArray(size)
                buffer.get(data)
                out[id] = String(data, Charsets.UTF_8).trimEnd('\u0000', ' ')
                buffer.position((dataEnd + (size and 1)).coerceAtMost(end))
            }
            buffer.position(end)
        }

        private fun parseSdta(
            buffer: ByteBuffer,
            end: Int,
            source: ByteArray
        ): ShortBuffer? {
            var pcm: ShortBuffer? = null
            while (buffer.position() + 8 <= end) {
                val id = readTag(buffer)
                val size = readUInt32(buffer).toInt()
                val dataStart = buffer.position()
                val dataEnd = dataStart + size
                if (dataEnd > end) break
                if (id == "smpl" && size >= 2) {
                    pcm = ByteBuffer.wrap(source, dataStart, size)
                        .slice()
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                }
                buffer.position((dataEnd + (size and 1)).coerceAtMost(end))
            }
            buffer.position(end)
            return pcm
        }

        private fun parsePdta(
            buffer: ByteBuffer,
            end: Int,
            out: MutableMap<String, ByteArray>
        ) {
            while (buffer.position() + 8 <= end) {
                val id = readTag(buffer)
                val size = readUInt32(buffer).toInt()
                val dataStart = buffer.position()
                val dataEnd = dataStart + size
                if (dataEnd > end) break
                out[id] = buffer.array().copyOfRange(dataStart, dataEnd)
                buffer.position((dataEnd + (size and 1)).coerceAtMost(end))
            }
            buffer.position(end)
        }

        private fun buildPresets(
            pdta: Map<String, ByteArray>,
            pcm: ShortBuffer
        ): List<Sf2Preset> {
            val phdr = pdta["phdr"] ?: return emptyList()
            val pbag = pdta["pbag"] ?: return emptyList()
            val pgen = pdta["pgen"] ?: return emptyList()
            val inst = pdta["inst"] ?: return emptyList()
            val ibag = pdta["ibag"] ?: return emptyList()
            val igen = pdta["igen"] ?: return emptyList()
            val shdr = pdta["shdr"] ?: return emptyList()

            val presets = parsePresetRecords(phdr)
            val instruments = parseInstrumentRecords(inst)
            val samples = parseSampleRecords(shdr, pcm)
            val pbagIndices = bagGeneratorIndices(pbag)
            val ibagIndices = bagGeneratorIndices(ibag)
            val pgenCount = pgen.size / 4
            val igenCount = igen.size / 4
            val pbagCount = pbag.size / 4
            val ibagCount = ibag.size / 4

            return presets.mapIndexed { presetIndex, preset ->
                val presetZoneEnd = presets.getOrNull(presetIndex + 1)?.bagIndex
                    ?: pbagCount
                val zones = mutableListOf<Sf2Zone>()
                var presetGlobal = emptyMap<Int, Int>()
                for (presetZone in preset.bagIndex until presetZoneEnd) {
                    val genStart = pbagIndices.getOrElse(presetZone) { pgenCount }
                    val genEnd = pbagIndices.getOrElse(presetZone + 1) { pgenCount }
                    val local = readGenerators(pgen, genStart, genEnd)
                    val presetGens = if (presetGlobal.isEmpty()) {
                        local
                    } else {
                        presetGlobal + local
                    }
                    val instrumentId = presetGens[GEN_INSTRUMENT]
                    if (instrumentId == null) {
                        presetGlobal = presetGens
                        continue
                    }
                    val instrument = instruments.getOrNull(instrumentId) ?: continue
                    val instrumentEnd = instruments.getOrNull(instrumentId + 1)?.bagIndex
                        ?: ibagCount
                    var instrumentGlobal = emptyMap<Int, Int>()
                    for (instrumentZone in instrument.bagIndex until instrumentEnd) {
                        val instGenStart = ibagIndices.getOrElse(instrumentZone) { igenCount }
                        val instGenEnd = ibagIndices.getOrElse(instrumentZone + 1) { igenCount }
                        val instLocal = readGenerators(igen, instGenStart, instGenEnd)
                        val instrumentGens = if (instrumentGlobal.isEmpty()) {
                            instLocal
                        } else {
                            instrumentGlobal + instLocal
                        }
                        val sampleId = instrumentGens[GEN_SAMPLE_ID]
                        if (sampleId == null) {
                            instrumentGlobal = instrumentGens
                            continue
                        }
                        val sample = samples.getOrNull(sampleId) ?: continue
                        buildZone(presetGens, instrumentGens, sample)?.let { zones.add(it) }
                    }
                }
                Sf2Preset(preset.name, preset.bank, preset.program, zones)
            }
        }

        private fun buildZone(
            preset: Map<Int, Int>,
            instrument: Map<Int, Int>,
            sample: Sf2Sample
        ): Sf2Zone? {
            val presetKey = rangeOf(preset, GEN_KEY_RANGE, 0, 127)
            val instrumentKey = rangeOf(instrument, GEN_KEY_RANGE, 0, 127)
            val keyLow = maxOf(presetKey.first, instrumentKey.first)
            val keyHigh = minOf(presetKey.second, instrumentKey.second)
            if (keyLow > keyHigh) return null

            val presetVel = rangeOf(preset, GEN_VEL_RANGE, 0, 127)
            val instrumentVel = rangeOf(instrument, GEN_VEL_RANGE, 0, 127)
            val velLow = maxOf(presetVel.first, instrumentVel.first)
            val velHigh = minOf(presetVel.second, instrumentVel.second)
            if (velLow > velHigh) return null

            val start = sample.start + offset(preset, instrument, GEN_START_ADDR, GEN_START_ADDR_COARSE)
            val end = sample.end + offset(preset, instrument, GEN_END_ADDR, GEN_END_ADDR_COARSE)
            val loopStart = sample.loopStart +
                offset(preset, instrument, GEN_LOOP_START, GEN_LOOP_START_COARSE)
            val loopEnd = sample.loopEnd +
                offset(preset, instrument, GEN_LOOP_END, GEN_LOOP_END_COARSE)

            val limit = minOf(sample.end, sample.pcm.limit())
            if (limit <= sample.start + 1) return null
            val safeStart = start.coerceIn(sample.start, limit - 1)
            val safeEnd = end.coerceIn(safeStart + 1, limit)
            if (safeEnd <= safeStart) return null
            val safeLoopStart = loopStart.coerceIn(safeStart, safeEnd - 1)
            val safeLoopEnd = loopEnd.coerceIn(safeLoopStart + 1, safeEnd)

            return Sf2Zone(
                keyLow = keyLow,
                keyHigh = keyHigh,
                velocityLow = velLow,
                velocityHigh = velHigh,
                sample = sample,
                rootKey = instrument[GEN_ROOT_KEY] ?: sample.originalKey,
                coarseTune = generator(preset, GEN_COARSE_TUNE, 0) +
                    generator(instrument, GEN_COARSE_TUNE, 0),
                fineTune = generator(preset, GEN_FINE_TUNE, 0) +
                    generator(instrument, GEN_FINE_TUNE, 0),
                attenuationCb = generator(preset, GEN_ATTENUATION, 0) +
                    generator(instrument, GEN_ATTENUATION, 0),
                pan = (generator(preset, GEN_PAN, 0) +
                    generator(instrument, GEN_PAN, 0)).coerceIn(-500, 500),
                sampleModes = instrument[GEN_SAMPLE_MODES]
                    ?: preset[GEN_SAMPLE_MODES]
                    ?: 0,
                start = safeStart,
                end = safeEnd,
                loopStart = safeLoopStart,
                loopEnd = safeLoopEnd,
                attackTc = envelope(preset, instrument, GEN_ATTACK),
                holdTc = envelope(preset, instrument, GEN_HOLD),
                decayTc = envelope(preset, instrument, GEN_DECAY),
                sustainCb = envelope(preset, instrument, GEN_SUSTAIN, 0),
                releaseTc = envelope(preset, instrument, GEN_RELEASE)
            )
        }

        private fun offset(
            preset: Map<Int, Int>,
            instrument: Map<Int, Int>,
            fine: Int,
            coarse: Int
        ): Int = generator(preset, fine, 0) +
            generator(instrument, fine, 0) +
            (generator(preset, coarse, 0) + generator(instrument, coarse, 0)) * 32768

        private fun generator(gens: Map<Int, Int>, op: Int, default: Int): Int =
            gens[op] ?: default

        private fun envelope(
            preset: Map<Int, Int>,
            instrument: Map<Int, Int>,
            op: Int,
            default: Int = DIV_12000
        ): Int = instrument[op] ?: preset[op] ?: default

        private fun rangeOf(
            gens: Map<Int, Int>,
            op: Int,
            defaultLow: Int,
            defaultHigh: Int
        ): Pair<Int, Int> {
            val raw = gens[op] ?: return defaultLow to defaultHigh
            val low = raw and 0xFF
            val high = (raw ushr 8) and 0xFF
            return low to high
        }

        private fun readGenerators(
            data: ByteArray,
            fromRecord: Int,
            toRecord: Int
        ): Map<Int, Int> {
            val result = HashMap<Int, Int>()
            var record = fromRecord
            while (record < toRecord && record * 4 + 4 <= data.size) {
                val offset = record * 4
                val op = u16(data, offset)
                result[op] = s16(data, offset + 2)
                record++
            }
            return result
        }

        private fun bagGeneratorIndices(data: ByteArray): IntArray {
            val count = data.size / 4
            return IntArray(count) { u16(data, it * 4) }
        }

        private data class PresetRecord(
            val name: String,
            val bank: Int,
            val program: Int,
            val bagIndex: Int
        )

        private fun parsePresetRecords(data: ByteArray): List<PresetRecord> {
            val count = data.size / 38
            val result = mutableListOf<PresetRecord>()
            for (index in 0 until count) {
                val offset = index * 38
                val name = name20(data, offset)
                if (name == "EOP") break
                result.add(
                    PresetRecord(
                        name = name,
                        bank = u16(data, offset + 22),
                        program = u16(data, offset + 20),
                        bagIndex = u16(data, offset + 24)
                    )
                )
            }
            return result
        }

        private data class InstrumentRecord(
            val name: String,
            val bagIndex: Int
        )

        private fun parseInstrumentRecords(data: ByteArray): List<InstrumentRecord> {
            val count = data.size / 22
            val result = mutableListOf<InstrumentRecord>()
            for (index in 0 until count) {
                val offset = index * 22
                val name = name20(data, offset)
                if (name == "EOI") break
                result.add(InstrumentRecord(name, u16(data, offset + 20)))
            }
            return result
        }

        private fun parseSampleRecords(
            data: ByteArray,
            pcm: ShortBuffer
        ): List<Sf2Sample> {
            val count = data.size / 46
            val limit = pcm.limit()
            val result = mutableListOf<Sf2Sample>()
            for (index in 0 until count) {
                val offset = index * 46
                val name = name20(data, offset)
                if (name == "EOS") break
                val start = u32(data, offset + 20).toInt()
                val end = u32(data, offset + 24).toInt().coerceAtMost(limit)
                if (start !in 0 until limit || end <= start) continue
                val loopStart = u32(data, offset + 28).toInt().coerceIn(start, end - 1)
                val loopEnd = u32(data, offset + 32).toInt().coerceIn(loopStart + 1, end)
                result.add(
                    Sf2Sample(
                        name = name,
                        sampleRate = u32(data, offset + 36).toInt().coerceAtLeast(1),
                        originalKey = data[offset + 40].toInt() and 0xFF,
                        pitchCorrection = data[offset + 41].toInt(),
                        start = start,
                        end = end,
                        loopStart = loopStart,
                        loopEnd = loopEnd,
                        pcm = pcm
                    )
                )
            }
            return result
        }

        private fun name20(data: ByteArray, offset: Int): String =
            String(data, offset, 20, Charsets.ISO_8859_1)
                .trimEnd('\u0000', ' ')

        private fun u16(data: ByteArray, offset: Int): Int =
            (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)

        private fun s16(data: ByteArray, offset: Int): Int =
            u16(data, offset).toShort().toInt()

        private fun u32(data: ByteArray, offset: Int): Long =
            (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)

        private fun readTag(buffer: ByteBuffer): String {
            val data = ByteArray(4)
            buffer.get(data)
            return String(data, Charsets.US_ASCII)
        }

        private fun readUInt32(buffer: ByteBuffer): Long =
            buffer.int.toLong() and 0xFFFFFFFFL

        private const val DIV_12000 = -12000

        private const val GEN_START_ADDR = 0
        private const val GEN_END_ADDR = 1
        private const val GEN_LOOP_START = 2
        private const val GEN_LOOP_END = 3
        private const val GEN_START_ADDR_COARSE = 4
        private const val GEN_END_ADDR_COARSE = 12
        private const val GEN_PAN = 17
        private const val GEN_ATTACK = 34
        private const val GEN_HOLD = 35
        private const val GEN_DECAY = 36
        private const val GEN_SUSTAIN = 37
        private const val GEN_RELEASE = 38
        private const val GEN_INSTRUMENT = 41
        private const val GEN_KEY_RANGE = 43
        private const val GEN_VEL_RANGE = 44
        private const val GEN_LOOP_START_COARSE = 45
        private const val GEN_ATTENUATION = 48
        private const val GEN_LOOP_END_COARSE = 50
        private const val GEN_COARSE_TUNE = 51
        private const val GEN_FINE_TUNE = 52
        private const val GEN_SAMPLE_ID = 53
        private const val GEN_SAMPLE_MODES = 54
        private const val GEN_ROOT_KEY = 58
    }
}
