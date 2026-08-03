package com.vigilante.app.ui.volunteers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.vigilante.app.R
import com.vigilante.app.core.Validation
import com.vigilante.app.ui.components.ConfirmDialog
import com.vigilante.app.ui.components.LoadingBox
import com.vigilante.app.ui.components.SectionHeader
import com.vigilante.app.ui.components.VCard
import com.vigilante.app.ui.components.VigilanteTopBar
import com.vigilante.app.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** "No selection" entry shown first in the municipality/district dropdowns. */
private const val NO_PLACE = "— بدون —"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VolunteerEditScreen(
    onDone: () -> Unit,
    viewModel: VolunteerEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val municipalities by viewModel.municipalities.collectAsState()
    val districts by viewModel.districts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
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
                .padding(horizontal = 16.dp)
        ) {
            // ---- الصورة ----
            SectionHeader(stringResource(R.string.photo))
            VCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val model: Any? = state.photoUri
                        ?: state.existingPhoto?.takeIf { !state.photoRemoved }
                    if (model != null) {
                        AsyncImage(
                            model = model,
                            contentDescription = stringResource(R.string.photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(AppColors.current.tileIconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = AppColors.current.tileIcon
                            )
                        }
                    }
                    Column {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("اختيار صورة")
                        }
                        if (model != null) {
                            TextButton(onClick = viewModel::removePhoto) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("حذف الصورة", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // ---- بيانات أساسية ----
            FormSection("بيانات أساسية") {
                OutlinedTextField(
                    value = state.membershipNumber,
                    onValueChange = { value ->
                        viewModel.update { it.copy(membershipNumber = value) }
                    },
                    label = {
                        Text(
                            stringResource(R.string.membership_number) +
                                " (اختياري — يُنشأ تلقائيًا)"
                        )
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
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
                BirthDateField(
                    value = state.birthDateInput,
                    onValue = { digits -> viewModel.update { it.copy(birthDateInput = digits) } },
                    error = state.fieldErrors["birthDate"]
                )
                DateField(
                    label = stringResource(R.string.join_date),
                    value = state.joinDate.toString(),
                    error = state.fieldErrors["joinDate"],
                    onClick = { showJoinPicker = true }
                )
                DropdownField(
                    label = stringResource(R.string.blood_group),
                    selected = state.bloodGroup.ifBlank { "—" },
                    options = listOf("—") + Validation.BLOOD_GROUPS,
                    onSelected = { picked ->
                        viewModel.update {
                            it.copy(bloodGroup = if (picked == "—") "" else picked)
                        }
                    }
                )
            }

            // ---- المكان ----
            FormSection("المكان") {
                DropdownField(
                    label = stringResource(R.string.municipality),
                    selected = state.municipality.ifBlank { NO_PLACE },
                    options = listOf(NO_PLACE) + municipalities.map { it.name },
                    onSelected = { picked ->
                        val name = if (picked == NO_PLACE) "" else picked
                        viewModel.update {
                            if (name == it.municipality) it
                            else it.copy(municipality = name, district = "")
                        }
                    }
                )
                Text(
                    "قائمة البلديات تُدار من الإعدادات ← إدارة الأماكن",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // الحي: typed directly like the name fields (user request). Known
                // districts appear as tappable suggestions, and whatever is typed
                // is remembered for next time — no trip to the settings screen.
                FieldWithError(
                    value = state.district,
                    onValue = { typed -> viewModel.update { it.copy(district = typed) } },
                    label = stringResource(R.string.district),
                    error = null
                )
                val suggestions = remember(districts, state.district) {
                    val query = state.district.trim()
                    districts.map { it.name }
                        .distinct()
                        .filter {
                            query.isBlank() ||
                                (it.contains(query, true) && !it.equals(query, true))
                        }
                        .take(6)
                }
                if (suggestions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        suggestions.forEach { name ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.update { it.copy(district = name) } },
                                shape = MaterialTheme.shapes.small,
                                label = { Text(name) }
                            )
                        }
                    }
                }
            }

            // ---- الاتصال ----
            FormSection("الاتصال") {
                FieldWithError(
                    value = state.phone1,
                    onValue = { value ->
                        if (value.length <= 10 && value.all(Char::isDigit)) {
                            viewModel.update { it.copy(phone1 = value) }
                        }
                    },
                    label = stringResource(R.string.phone1),
                    error = state.fieldErrors["phone1"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                FieldWithError(
                    value = state.phone2,
                    onValue = { value ->
                        if (value.length <= 10 && value.all(Char::isDigit)) {
                            viewModel.update { it.copy(phone2 = value) }
                        }
                    },
                    label = stringResource(R.string.phone2),
                    error = state.fieldErrors["phone2"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // ---- إضافي ----
            FormSection("إضافي") {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { value -> viewModel.update { it.copy(notes = value) } },
                    label = { Text(stringResource(R.string.notes)) },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.tags),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.removeTag(tag) },
                                shape = MaterialTheme.shapes.small,
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
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            viewModel.addTag(tagInput)
                            tagInput = ""
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "إضافة وسم")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.save() },
                enabled = !state.saving,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    stringResource(R.string.save),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(28.dp))
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

/** SectionHeader + a VCard holding the fields of that group. */
@Composable
private fun FormSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionHeader(title)
    VCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
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
        shape = MaterialTheme.shapes.small,
        supportingText = { if (error != null) Text(error) },
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Birth date typed directly (user request: no calendar). The state keeps
 * digits only (ddMMyyyy, max 8); slashes are drawn by [dateSlashesTransformation].
 */
@Composable
private fun BirthDateField(
    value: String,
    onValue: (String) -> Unit,
    error: String?
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            onValue(new.filter(Char::isDigit).take(8))
        },
        label = { Text(stringResource(R.string.birth_date)) },
        placeholder = { Text("يوم/شهر/سنة — مثال: 25/03/1990") },
        singleLine = true,
        isError = error != null,
        shape = MaterialTheme.shapes.small,
        supportingText = { if (error != null) Text(error) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = dateSlashesTransformation,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Shows ddMMyyyy digits as dd/mm/yyyy while the state stays digits-only. */
private val dateSlashesTransformation = VisualTransformation { text ->
    val digits = text.text
    val formatted = buildString {
        digits.forEachIndexed { index, c ->
            append(c)
            if (index == 1 || index == 3) append('/')
        }
    }
    TransformedText(
        AnnotatedString(formatted),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                (offset + (if (offset >= 2) 1 else 0) + (if (offset >= 4) 1 else 0))
                    .coerceIn(0, formatted.length)

            override fun transformedToOriginal(offset: Int): Int =
                (offset - (if (offset > 2) 1 else 0) - (if (offset > 5) 1 else 0))
                    .coerceIn(0, digits.length)
        }
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
        shape = MaterialTheme.shapes.small,
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
        shape = MaterialTheme.shapes.large,
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
