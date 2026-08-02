package com.vigilante.app.ui.volunteers

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.core.Validation
import com.vigilante.app.data.files.PhotoStore
import com.vigilante.app.data.local.entity.District
import com.vigilante.app.data.local.entity.Municipality
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.repository.PlacesRepository
import com.vigilante.app.data.repository.VolunteerDraft
import com.vigilante.app.data.repository.VolunteerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import javax.inject.Inject

data class EditFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val membershipNumber: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val fatherName: String = "",
    /** Birth date typed as digits only (ddMMyyyy); slashes are added visually. */
    val birthDateInput: String = "",
    val joinDate: LocalDate = LocalDate.now(),
    val municipality: String = "",
    val district: String = "",
    val bloodGroup: String = "",
    val phone1: String = "",
    val phone2: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val photoUri: Uri? = null,
    val photoRemoved: Boolean = false,
    val existingPhoto: File? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val duplicateOwner: Volunteer? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VolunteerEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VolunteerRepository,
    private val photoStore: PhotoStore,
    placesRepository: PlacesRepository
) : ViewModel() {

    private val editId: String? = savedStateHandle.get<String>("id")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(EditFormState(isEdit = editId != null))
    val state: StateFlow<EditFormState> = _state

    /** Admin-managed بلديات list for the optional dropdown. */
    val municipalities: StateFlow<List<Municipality>> = placesRepository.municipalities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Districts of the currently selected municipality (empty when none matches). */
    val districts: StateFlow<List<District>> =
        combine(municipalities, _state) { list, s ->
            list.firstOrNull { it.name == s.municipality }?.id
        }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else placesRepository.districtsOf(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var original: Volunteer? = null

    init {
        viewModelScope.launch {
            if (editId != null) {
                val v = repository.byId(editId)
                if (v == null) {
                    _state.value = _state.value.copy(loading = false, message = "المتطوع غير موجود")
                } else {
                    original = v
                    val tagNames = repository.tagsFor(editId).map { it.name }
                    _state.value = EditFormState(
                        loading = false,
                        isEdit = true,
                        membershipNumber = v.membershipNumber,
                        firstName = v.firstName,
                        lastName = v.lastName,
                        fatherName = v.fatherName,
                        birthDateInput = v.birthDate?.format(birthDigitsFmt).orEmpty(),
                        joinDate = v.joinDate,
                        municipality = v.municipality.orEmpty(),
                        district = v.district.orEmpty(),
                        bloodGroup = v.bloodGroup.orEmpty(),
                        phone1 = v.phone1,
                        phone2 = v.phone2.orEmpty(),
                        notes = v.notes.orEmpty(),
                        tags = tagNames,
                        existingPhoto = photoStore.photoFile(v.volunteerId)
                            .takeIf { it.exists() }
                    )
                }
            } else {
                _state.value = EditFormState(loading = false, isEdit = false)
            }
        }
    }

    fun update(transform: (EditFormState) -> EditFormState) {
        _state.value = transform(_state.value)
    }

    fun addTag(name: String) {
        val clean = Validation.normalizeName(name)
        if (clean.isBlank() || _state.value.tags.contains(clean)) return
        _state.value = _state.value.copy(tags = _state.value.tags + clean)
    }

    fun removeTag(name: String) {
        _state.value = _state.value.copy(tags = _state.value.tags - name)
    }

    fun pickPhoto(uri: Uri) {
        _state.value = _state.value.copy(photoUri = uri, photoRemoved = false)
    }

    fun removePhoto() {
        _state.value = _state.value.copy(photoUri = null, photoRemoved = true)
    }

    /** Parses the typed ddMMyyyy digits into a date; null when empty or invalid. */
    private fun parsedBirthDate(s: EditFormState): LocalDate? {
        val digits = s.birthDateInput
        if (digits.length != 8) return null
        val text = "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4)}"
        return runCatching { LocalDate.parse(text, birthParseFmt) }.getOrNull()
    }

    private fun validate(s: EditFormState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        val today = LocalDate.now()
        if (s.firstName.isBlank()) errors["firstName"] = "هذا الحقل إلزامي"
        if (s.lastName.isBlank()) errors["lastName"] = "هذا الحقل إلزامي"
        if (s.fatherName.isBlank()) errors["fatherName"] = "هذا الحقل إلزامي"
        if (s.phone1.isBlank()) {
            errors["phone1"] = "هذا الحقل إلزامي"
        } else if (!Validation.isValidPhone(s.phone1)) {
            errors["phone1"] = "رقم الهاتف غير صالح"
        }
        if (s.phone2.isNotBlank() && !Validation.isValidPhone(s.phone2)) {
            errors["phone2"] = "رقم الهاتف غير صالح"
        }
        if (s.bloodGroup.isNotBlank() && !Validation.isValidBloodGroup(s.bloodGroup)) {
            errors["bloodGroup"] = "زمرة دم غير صالحة"
        }
        val birth = parsedBirthDate(s)
        if (s.birthDateInput.isNotBlank() && birth == null) {
            errors["birthDate"] = "تاريخ غير صالح"
        } else when (Validation.validateBirthDate(birth, today)) {
            is Validation.DateError.InFuture -> errors["birthDate"] = "لا يسمح بتاريخ في المستقبل"
            else -> {}
        }
        when (Validation.validateJoinDate(s.joinDate, birth, today)) {
            is Validation.DateError.InFuture -> errors["joinDate"] = "لا يسمح بتاريخ في المستقبل"
            is Validation.DateError.JoinBeforeBirth ->
                errors["joinDate"] = "تاريخ الانضمام لا يمكن أن يكون قبل تاريخ الميلاد"
            else -> {}
        }
        return errors
    }

    fun save(force: Boolean = false) {
        val s = _state.value
        val errors = validate(s)
        if (errors.isNotEmpty()) {
            _state.value = s.copy(fieldErrors = errors)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, fieldErrors = emptyMap())
            if (!s.isEdit && !force) {
                val owner = repository.duplicatePhoneOwner(s.phone1)
                if (owner != null) {
                    _state.value = _state.value.copy(saving = false, duplicateOwner = owner)
                    return@launch
                }
            }
            if (s.isEdit) doUpdate(s) else doAdd(s)
        }
    }

    fun confirmDuplicate() {
        _state.value = _state.value.copy(duplicateOwner = null)
        save(force = true)
    }

    fun dismissDuplicate() {
        _state.value = _state.value.copy(duplicateOwner = null)
    }

    private suspend fun doAdd(s: EditFormState) {
        val draft = VolunteerDraft(
            membershipNumber = s.membershipNumber.takeIf { it.isNotBlank() },
            firstName = s.firstName,
            lastName = s.lastName,
            fatherName = s.fatherName,
            birthDate = parsedBirthDate(s),
            joinDate = s.joinDate,
            municipality = s.municipality,
            district = s.district,
            bloodGroup = s.bloodGroup.takeIf { it.isNotBlank() },
            phone1 = s.phone1,
            phone2 = s.phone2,
            photoPath = null,
            notes = s.notes,
            tagNames = s.tags
        )
        repository.add(draft)
            .onSuccess { created ->
                val uri = s.photoUri
                if (uri != null) {
                    val saved = withContext(Dispatchers.IO) {
                        runCatching { photoStore.saveFromUri(created.volunteerId, uri) }
                    }
                    saved.onSuccess { path ->
                        repository.update(created.copy(photoPath = path), s.tags)
                    }
                }
                _state.value = _state.value.copy(saving = false, saved = true)
            }
            .onFailure {
                _state.value = _state.value.copy(
                    saving = false,
                    message = it.message ?: "تعذر حفظ المتطوع"
                )
            }
    }

    private suspend fun doUpdate(s: EditFormState) {
        val base = original ?: return
        var photoPath = base.photoPath
        val uri = s.photoUri
        if (uri != null) {
            val saved = withContext(Dispatchers.IO) {
                runCatching { photoStore.saveFromUri(base.volunteerId, uri) }
            }
            saved.onSuccess { photoPath = it }
                .onFailure {
                    _state.value = _state.value.copy(
                        saving = false,
                        message = "تعذر حفظ الصورة"
                    )
                    return
                }
        } else if (s.photoRemoved) {
            withContext(Dispatchers.IO) { photoStore.delete(base.volunteerId) }
            photoPath = null
        }
        val updated = base.copy(
            membershipNumber = s.membershipNumber.trim().ifBlank { base.membershipNumber },
            firstName = Validation.normalizeName(s.firstName),
            lastName = Validation.normalizeName(s.lastName),
            fatherName = Validation.normalizeName(s.fatherName),
            birthDate = parsedBirthDate(s),
            joinDate = s.joinDate,
            municipality = s.municipality.trim().takeIf { it.isNotBlank() },
            district = s.district.trim().takeIf { it.isNotBlank() },
            bloodGroup = s.bloodGroup.trim().takeIf { it.isNotBlank() },
            phone1 = s.phone1.trim(),
            phone2 = s.phone2.trim().takeIf { it.isNotBlank() },
            notes = s.notes.trim().takeIf { it.isNotBlank() },
            photoPath = photoPath
        )
        repository.update(updated, s.tags)
            .onSuccess { _state.value = _state.value.copy(saving = false, saved = true) }
            .onFailure {
                _state.value = _state.value.copy(
                    saving = false,
                    message = it.message ?: "تعذر حفظ التعديلات"
                )
            }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private companion object {
        /** Formats a stored date back into the digits-only input (ddMMyyyy). */
        val birthDigitsFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMuuuu")

        /** Strict d/M/uuuu parsing so 31/02/2000 or year 10000 never slips through. */
        val birthParseFmt: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)
    }
}
