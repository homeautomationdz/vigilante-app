package com.vigilante.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.data.local.entity.District
import com.vigilante.app.data.local.entity.Municipality
import com.vigilante.app.data.repository.PlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val repository: PlacesRepository
) : ViewModel() {

    val municipalities: StateFlow<List<Municipality>> = repository.municipalities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun districtsOf(municipalityId: Long): Flow<List<District>> =
        repository.districtsOf(municipalityId)

    fun addMunicipality(name: String) {
        viewModelScope.launch {
            repository.addMunicipality(name)
                .onSuccess { _message.value = "تمت إضافة البلدية" }
                .onFailure { _message.value = it.message ?: "تعذرت إضافة البلدية" }
        }
    }

    fun addDistrict(municipalityId: Long, name: String) {
        viewModelScope.launch {
            repository.addDistrict(municipalityId, name)
                .onSuccess { _message.value = "تمت إضافة الحي" }
                .onFailure { _message.value = it.message ?: "تعذرت إضافة الحي" }
        }
    }

    fun deleteMunicipality(id: Long) {
        viewModelScope.launch {
            repository.deleteMunicipality(id)
                .onSuccess { _message.value = "تم حذف البلدية وأحياؤها" }
                .onFailure { _message.value = it.message ?: "تعذر حذف البلدية" }
        }
    }

    fun deleteDistrict(id: Long) {
        viewModelScope.launch {
            repository.deleteDistrict(id)
                .onSuccess { _message.value = "تم حذف الحي" }
                .onFailure { _message.value = it.message ?: "تعذر حذف الحي" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
