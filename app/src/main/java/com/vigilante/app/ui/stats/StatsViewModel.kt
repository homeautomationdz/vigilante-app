package com.vigilante.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.data.repository.DashboardStats
import com.vigilante.app.data.repository.StatsRepository
import com.vigilante.app.data.repository.VolunteerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NamedCount(val name: String, val count: Int)

data class StatsUiState(
    val loading: Boolean = true,
    val stats: DashboardStats? = null,
    val topAttendees: List<NamedCount> = emptyList(),
    val lowestAttendees: List<NamedCount> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val volunteerRepository: VolunteerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = StatsUiState(loading = true)
            runCatching { statsRepository.dashboard() }
                .onSuccess { stats ->
                    val top = stats.topAttendees.map {
                        NamedCount(
                            volunteerRepository.byId(it.volunteerId)?.displayName ?: it.volunteerId,
                            it.count
                        )
                    }
                    val lowest = stats.lowestAttendees.map {
                        NamedCount(
                            volunteerRepository.byId(it.volunteerId)?.displayName ?: it.volunteerId,
                            it.count
                        )
                    }
                    _state.value = StatsUiState(
                        loading = false,
                        stats = stats,
                        topAttendees = top,
                        lowestAttendees = lowest
                    )
                }
                .onFailure {
                    _state.value = StatsUiState(loading = false, error = "تعذر تحميل الإحصائيات")
                }
        }
    }
}
