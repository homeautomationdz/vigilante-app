package com.vigilante.app.ui.volunteers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.vigilante.app.BuildConfig
import com.vigilante.app.R
import com.vigilante.app.core.Validation
import com.vigilante.app.data.local.VolunteerFilter
import com.vigilante.app.data.local.VolunteerSort
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.local.entity.VolunteerStatus
import com.vigilante.app.ui.components.ChipTone
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.StatusChip
import com.vigilante.app.ui.components.VCard
import com.vigilante.app.ui.components.VigilanteTopBar
import com.vigilante.app.ui.theme.AppColors

internal val sortLabels: Map<VolunteerSort, String> = mapOf(
    VolunteerSort.NAME to "الاسم",
    VolunteerSort.LAST_NAME to "اللقب",
    VolunteerSort.BIRTH_DATE to "تاريخ الميلاد",
    VolunteerSort.JOIN_DATE to "تاريخ الانضمام",
    VolunteerSort.MOST_ATTENDANCE to "الأكثر حضورًا",
    VolunteerSort.LEAST_ATTENDANCE to "الأقل حضورًا",
    VolunteerSort.NEWEST to "الأحدث إضافة",
    VolunteerSort.OLDEST to "الأقدم إضافة"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VolunteerListScreen(
    onBack: () -> Unit,
    onOpenVolunteer: (String) -> Unit,
    onAddVolunteer: () -> Unit,
    viewModel: VolunteerListViewModel = hiltViewModel()
) {
    val volunteers by viewModel.volunteers.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val municipalities by viewModel.municipalities.collectAsState()
    val districts by viewModel.districts.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            VigilanteTopBar(
                title = stringResource(R.string.menu_volunteers),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.filters)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (viewModel.canAdd()) {
                ExtendedFloatingActionButton(
                    onClick = onAddVolunteer,
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_volunteer)) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = filter.searchText,
                onValueChange = viewModel::setSearch,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.searchText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearch("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            )

            FilterChipsRow(
                filter = filter,
                resultCount = volunteers.size,
                onOpenSheet = { showFilterSheet = true },
                onReset = { viewModel.applyFilter(VolunteerFilter()) }
            )

            if (volunteers.isEmpty()) {
                EmptyState(
                    text = stringResource(R.string.no_results),
                    icon = Icons.Filled.SearchOff
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp
                    )
                ) {
                    items(volunteers, key = { it.volunteerId }) { volunteer ->
                        VolunteerCard(
                            volunteer = volunteer,
                            photoFile = viewModel.photoFile(volunteer.volunteerId),
                            onClick = { onOpenVolunteer(volunteer.volunteerId) }
                        )
                    }
                }
            }
        }

        if (showFilterSheet) {
            FilterSheet(
                current = filter,
                municipalities = municipalities,
                districts = districts,
                onApply = {
                    viewModel.applyFilter(it)
                    showFilterSheet = false
                },
                onDismiss = { showFilterSheet = false }
            )
        }
    }
}

/** Compact entry point to the filter sheet + a readout of what is applied. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipsRow(
    filter: VolunteerFilter,
    resultCount: Int,
    onOpenSheet: () -> Unit,
    onReset: () -> Unit
) {
    val applied = listOfNotNull(
        filter.municipality,
        filter.district,
        filter.bloodGroup,
        when (filter.status) {
            "ACTIVE" -> stringResource(R.string.status_active)
            "ARCHIVED" -> stringResource(R.string.status_archived)
            else -> null
        },
        if (filter.hasPhoto == true) "مع صورة" else null
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AssistChip(
            onClick = onOpenSheet,
            shape = MaterialTheme.shapes.small,
            leadingIcon = {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            },
            label = { Text(stringResource(R.string.filters)) }
        )
        AssistChip(
            onClick = onOpenSheet,
            shape = MaterialTheme.shapes.small,
            leadingIcon = {
                Icon(
                    Icons.Filled.SwapVert,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            },
            label = { Text(sortLabels[filter.sort] ?: stringResource(R.string.sort)) }
        )
        applied.forEach { value ->
            AssistChip(
                onClick = onOpenSheet,
                shape = MaterialTheme.shapes.small,
                label = { Text(value) }
            )
        }
        if (applied.isNotEmpty()) {
            AssistChip(
                onClick = onReset,
                shape = MaterialTheme.shapes.small,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                },
                label = { Text("إعادة تعيين") }
            )
        }
        Box(
            modifier = Modifier.height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$resultCount نتيجة",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun VolunteerCard(
    volunteer: Volunteer,
    photoFile: java.io.File?,
    onClick: () -> Unit
) {
    VCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(photoFile = photoFile, name = volunteer.displayName, size = 52)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    volunteer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    listOfNotNull(volunteer.membershipNumber, volunteer.municipality)
                        .joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    volunteer.phone1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(
                text = if (volunteer.status == VolunteerStatus.ACTIVE)
                    stringResource(R.string.status_active)
                else stringResource(R.string.status_archived),
                tone = if (volunteer.status == VolunteerStatus.ACTIVE)
                    ChipTone.SUCCESS else ChipTone.NEUTRAL
            )
        }
    }
}

/** Photo when there is one, otherwise the first letter on a tinted circle. */
@Composable
internal fun Avatar(photoFile: java.io.File?, name: String, size: Int) {
    val c = AppColors.current
    if (photoFile != null) {
        AsyncImage(
            model = photoFile,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(c.tileIconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.trim().firstOrNull()?.toString() ?: "؟",
                style = MaterialTheme.typography.headlineSmall,
                color = c.tileIcon
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropdownField(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            shape = MaterialTheme.shapes.small,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private const val ALL = "الكل"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    current: VolunteerFilter,
    municipalities: List<String>,
    districts: List<String>,
    onApply: (VolunteerFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var municipality by remember { mutableStateOf(current.municipality ?: ALL) }
    var district by remember { mutableStateOf(current.district ?: ALL) }
    var bloodGroup by remember { mutableStateOf(current.bloodGroup ?: ALL) }
    var status by remember {
        mutableStateOf(
            when (current.status) {
                "ACTIVE" -> "نشط"
                "ARCHIVED" -> "مؤرشف"
                else -> ALL
            }
        )
    }
    var hasPhotoOnly by remember { mutableStateOf(current.hasPhoto == true) }
    var sort by remember { mutableStateOf(current.sort) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.filters),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            DropdownField(
                label = stringResource(R.string.municipality),
                selected = municipality,
                options = listOf(ALL) + municipalities,
                onSelected = { municipality = it }
            )
            DropdownField(
                label = stringResource(R.string.district),
                selected = district,
                options = listOf(ALL) + districts,
                onSelected = { district = it }
            )
            DropdownField(
                label = stringResource(R.string.blood_group),
                selected = bloodGroup,
                options = listOf(ALL) + Validation.BLOOD_GROUPS,
                onSelected = { bloodGroup = it }
            )
            DropdownField(
                label = "حالة العضوية",
                selected = status,
                options = listOf(ALL, "نشط", "مؤرشف"),
                onSelected = { status = it }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { hasPhotoOnly = !hasPhotoOnly }
            ) {
                Checkbox(checked = hasPhotoOnly, onCheckedChange = { hasPhotoOnly = it })
                Text(
                    "مع صورة فقط",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            val sortOptions = VolunteerSort.entries.filter {
                BuildConfig.ATTENDANCE_ENABLED ||
                    (it != VolunteerSort.MOST_ATTENDANCE && it != VolunteerSort.LEAST_ATTENDANCE)
            }
            DropdownField(
                label = stringResource(R.string.sort),
                selected = sortLabels[sort] ?: "",
                options = sortOptions.map { sortLabels[it] ?: it.name },
                onSelected = { label ->
                    sort = sortLabels.entries.firstOrNull { it.value == label }?.key
                        ?: VolunteerSort.NAME
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onApply(VolunteerFilter()) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) { Text("إعادة تعيين") }
                Button(
                    onClick = {
                        onApply(
                            VolunteerFilter(
                                municipality = municipality.takeIf { it != ALL },
                                district = district.takeIf { it != ALL },
                                bloodGroup = bloodGroup.takeIf { it != ALL },
                                status = when (status) {
                                    "نشط" -> "ACTIVE"
                                    "مؤرشف" -> "ARCHIVED"
                                    else -> null
                                },
                                hasPhoto = if (hasPhotoOnly) true else null,
                                sort = sort
                            )
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) { Text("تطبيق") }
            }
        }
    }
}
