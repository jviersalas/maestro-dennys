package apk.mavier.dguitar.presentation.improvisation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import apk.mavier.dguitar.domain.di.DependencyProvider
import apk.mavier.dguitar.domain.model.ImprovInstrument
import apk.mavier.dguitar.domain.repository.ImprovisationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImprovisationSettingsState(
    val bpm: Int,
    val showDegrees: Boolean,
    val showScale: Boolean,
    val showChord: Boolean,
    val showColors: Boolean,
    val metronome: Boolean,
    val scaleRootPc: Int,
    val scaleIndex: Int
)

class ImprovisationViewModel(
    private val repository: ImprovisationRepository,
    val instrument: ImprovInstrument
) : ViewModel() {

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<ImprovisationSettingsState> = _settings.asStateFlow()

    fun setBpm(value: Int) {
        update { it.copy(bpm = value.coerceIn(MIN_BPM, MAX_BPM)) }
    }

    fun setShowDegrees(value: Boolean) {
        update { it.copy(showDegrees = value) }
    }

    fun setShowScale(value: Boolean) {
        update { it.copy(showScale = value) }
    }

    fun setShowChord(value: Boolean) {
        update { it.copy(showChord = value) }
    }

    fun setShowColors(value: Boolean) {
        update { it.copy(showColors = value) }
    }

    fun setMetronome(value: Boolean) {
        update { it.copy(metronome = value) }
    }

    fun setScaleRoot(rootPc: Int) {
        update { it.copy(scaleRootPc = ((rootPc % 12) + 12) % 12) }
    }

    fun setScaleIndex(index: Int) {
        update { it.copy(scaleIndex = index) }
    }

    private fun update(
        transform: (ImprovisationSettingsState) -> ImprovisationSettingsState
    ) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        viewModelScope.launch(Dispatchers.IO) { persist(next) }
    }

    private fun load(): ImprovisationSettingsState = ImprovisationSettingsState(
        bpm = repository.loadBpm(instrument),
        showDegrees = repository.loadShowDegrees(),
        showScale = repository.loadShowScale(),
        showChord = repository.loadShowChord(),
        showColors = repository.loadShowColors(),
        metronome = repository.loadMetronome(),
        scaleRootPc = repository.loadScaleRoot(),
        scaleIndex = repository.loadScaleIndex()
    )

    private fun persist(state: ImprovisationSettingsState) {
        repository.saveBpm(instrument, state.bpm)
        repository.saveShowDegrees(state.showDegrees)
        repository.saveShowScale(state.showScale)
        repository.saveShowChord(state.showChord)
        repository.saveShowColors(state.showColors)
        repository.saveMetronome(state.metronome)
        repository.saveScaleRoot(state.scaleRootPc)
        repository.saveScaleIndex(state.scaleIndex)
    }

    companion object {
        private const val MIN_BPM = 40
        private const val MAX_BPM = 220

        fun factory(
            container: DependencyProvider,
            instrument: ImprovInstrument
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ImprovisationViewModel(
                    repository = container.improvisationRepository,
                    instrument = instrument
                ) as T
            }
        }
    }
}
