package apk.mavier.dguitar.domain.repository

import apk.mavier.dguitar.domain.model.ImprovSection

interface ImprovisationRepository {
    fun loadSections(): List<ImprovSection>
    fun saveSections(sections: List<ImprovSection>)
    fun loadBpm(): Int
    fun saveBpm(bpm: Int)
    fun loadShowDegrees(): Boolean
    fun saveShowDegrees(show: Boolean)
}
