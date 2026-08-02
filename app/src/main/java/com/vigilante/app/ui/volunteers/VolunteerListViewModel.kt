package com.vigilante.app.ui.volunteers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.core.AppFolders
import com.vigilante.app.data.local.VolunteerFilter
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.repository.VolunteerRepository
import com.vigilante.app.security.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VolunteerListViewModel @Inject constructor(
    private val repository: VolunteerRepository,
    private val folders: AppFolders,
    private val session: Session
) : ViewModel() {

    private val _filter = MutableStateFlow(VolunteerFilter())
    val filter: StateFlow<VolunteerFilter> = _filter

    val volunteers: StateFlow<List<Volunteer>> = _filter
        .flatMapLatest { repository.filtered(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _municipalities = MutableStateFlow<List<String>>(emptyList())
    val municipalities: StateFlow<List<String>> = _municipalities

    private val _districts = MutableStateFlow<List<String>>(emptyList())
    val districts: StateFlow<List<String>> = _districts

    init {
        refreshChoices()
    }

    fun refreshChoices() {
        viewModelScope.launch {
            _municipalities.value = repository.municipalities()
            _districts.value = repository.districts()
        }
    }

    fun setSearch(text: String) {
        _filter.value = _filter.value.copy(searchText = text)
    }

    fun applyFilter(newFilter: VolunteerFilter) {
        _filter.value = newFilter.copy(searchText = _filter.value.searchText)
    }

    fun photoFile(volunteerId: String): File? =
        folders.photoFile(volunteerId).takeIf { it.exists() }

    fun canAdd(): Boolean = session.has(Permission.ADD_VOLUNTEER)
}
