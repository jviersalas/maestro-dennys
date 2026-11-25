package apk.mavier.dguitar.domain.repository

import apk.mavier.dguitar.domain.model.ImprovInstrument
import apk.mavier.dguitar.domain.model.ImprovSection

interface ImprovisationRepository {
    fun loadSections(instrument: ImprovInstrument): List<ImprovSection>
    fun saveSections(instrument: ImprovInstrument, sections: List<ImprovSection>)
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
