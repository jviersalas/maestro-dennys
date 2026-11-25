package apk.mavier.dguitar.domain.repository

import apk.mavier.dguitar.domain.model.ImprovInstrument
import apk.mavier.dguitar.domain.model.ImprovProgression

interface ImprovisationRepository {
    fun loadProgressions(instrument: ImprovInstrument): List<ImprovProgression>
    fun saveProgressions(instrument: ImprovInstrument, progressions: List<ImprovProgression>)
    fun loadSelectedProgressionId(instrument: ImprovInstrument): String?
    fun saveSelectedProgressionId(instrument: ImprovInstrument, id: String)
    fun loadBpm(instrument: ImprovInstrument): Int
    fun saveBpm(instrument: ImprovInstrument, bpm: Int)
    fun loadShowDegrees(): Boolean
    fun saveShowDegrees(show: Boolean)
    fun loadScaleRoot(): Int
    fun saveScaleRoot(rootPc: Int)
    fun loadScaleIndex(): Int
    fun saveScaleIndex(index: Int)
    fun loadShowScale(): Boolean
    fun saveShowScale(show: Boolean)
    fun loadShowChord(): Boolean
    fun saveShowChord(show: Boolean)
    fun loadShowColors(): Boolean
    fun saveShowColors(show: Boolean)
    fun loadMetronome(): Boolean
    fun saveMetronome(enabled: Boolean)
}
