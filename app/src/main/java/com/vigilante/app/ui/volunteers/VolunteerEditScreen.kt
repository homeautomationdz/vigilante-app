package com.vigilante.app.ui.volunteers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.vigilante.app.R
import com.vigilante.app.core.Validation
import com.vigilante.app.ui.components.ConfirmDialog
import com.vigilante.app.ui.components.LoadingBox
import com.vigilante.app.ui.components.SectionHeader
import com.vigilante.app.ui.components.VigilanteTopBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VolunteerEditScreen(
    onDone: () -> Unit,
    viewModel: VolunteerEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showBirthPicker by remember { mutableStateOf(false) }
    var showJoinPicker by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.pickPhoto(uri)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            VigilanteTopBar(
                title = if (state.isEdit) stringResource(R.string.edit)
                else stringResource(R.string.add_volunteer),
                onBack = onDone
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Photo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val model: Any? = state.photoUri
                    ?: state.existingPhoto?.takeIf { !state.photoRemoved }
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = stringResource(R.string.photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Column {
                    OutlinedButton(onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("اختيار صورة")
                    }
                    if (model != null) {
                        TextButton(onClick = viewModel::removePhoto) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("حذف الصورة", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            SectionHeader("البيانات الأساسية")

            OutlinedTextField(
                value = state.membershipNumber,
                onValueChange = { value -> viewModel.update { it.copy(membershipNumber = value) } },
                label = { Text(stringResource(R.string.membership_number) + " (اختياري — يُنشأ تلقائيًا)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FieldWithError(
                value = state.firstName,
                onValue = { value -> viewModel.update { it.copy(firstName = value) } },
                label = stringResource(R.string.first_name),
                error = state.fieldErrors["firstName"]
            )
            FieldWithError(
                value = state.lastName,
                onValue = { value -> viewModel.update { it.copy(lastName = value) } },
                label = stringResource(R.string.last_name),
                error = state.fieldErrors["lastName"]
            )
            FieldWithError(
                value = state.fatherName,
                onValue = { value -> viewModel.update { it.copy(fatherName = value) } },
                label = stringResource(R.string.father_name),
                error = state.fieldErrors["fatherName"]
            )

            DateField(
                label = stringResource(R.string.birth_date),
                value = state.birthDate?.toString() ?: "",
                error = state.fieldErrors["birthDate"],
                onClick = { showBirthPicker = true }
            )
            DateField(
                label = stringResource(R.string.join_date),
                value = state.joinDate.toString(),
                error = state.fieldErrors["joinDate"],
                onClick = { showJoinPicker = true }
            )

            FieldWithError(
                value = state.municipality,
                onValue = { value -> viewModel.update { it.copy(municipality = value) } },
                label = stringResource(R.string.municipality),
                error = null
            )
            FieldWithError(
                value = state.district,
                onValue = { value -> viewModel.update { it.copy(district = value) } },
                label = stringResource(R.string.district),
                error = null
            )

            DropdownField(
                label = stringResource(R.string.blood_group),
                selected = state.bloodGroup.ifBlank { "—" },
                options = listOf("—") + Validation.BLOOD_GROUPS,
                onSelected = { picked ->
                    viewModel.update { it.copy(bloodGroup = if (picked == "—") "" else picked) }
                }
            )

            FieldWithError(
                value = state.phone1,
                onValue = { value -> viewModel.update { it.copy(phone1 = value.filter(Char::isDigit)) } },
                label = stringResource(R.string.phone1),
                error = state.fieldErrors["phone1"],
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            FieldWithError(
                value = state.phone2,
                onValue = { value -> viewModel.update { it.copy(phone2 = value.filter(Char::isDigit)) } },
                label = stringResource(R.string.phone2),
                error = state.fieldErrors["phone2"],
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = { value -> viewModel.update { it.copy(notes = value) } },
                label = { Text(stringResource(R.string.notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader(stringResource(R.string.tags))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("وسم جديد") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    viewModel.addTag(tagInput)
                    tagInput = ""
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "إضافة وسم")
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.save() },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    stringResource(R.string.save),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showBirthPicker) {
            AppDatePicker(
                initial = state.birthDate,
                onPicked = { picked ->
                    viewModel.update { it.copy(birthDate = picked) }
                    showBirthPicker = false
                },
                onDismiss = { showBirthPicker = false }
            )
        }
        if (showJoinPicker) {
            AppDatePicker(
                initial = state.joinDate,
                onPicked = { picked ->
                    if (picked != null) viewModel.update { it.copy(joinDate = picked) }
                    showJoinPicker = false
                },
                onDismiss = { showJoinPicker = false }
            )
        }

        state.duplicateOwner?.let { owner ->
            ConfirmDialog(
                title = "تنبيه",
                text = stringResource(R.string.duplicate_phone_warning) +
                    "\n(${owner.displayName} — ${owner.volunteerId})",
                confirmLabel = stringResource(R.string.continue_anyway),
                onConfirm = { viewModel.confirmDuplicate() },
                onDismiss = { viewModel.dismissDuplicate() }
            )
        }
    }
}

@Composable
private fun FieldWithError(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    error: String?,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DateField(
    label: String,
    value: String,
    error: String?,
    onClick: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        trailingIcon = {
            IconButton(onClick = onClick) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = label)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDatePicker(
    initial: LocalDate?,
    onPicked: (LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                onPicked(
                    millis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                )
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    ) {
        DatePicker(state = pickerState)
    }
}
