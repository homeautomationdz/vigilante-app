package com.vigilante.app.ui.volunteers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Surface
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
import com.vigilante.app.R
import com.vigilante.app.core.Validation
import com.vigilante.app.data.local.VolunteerFilter
import com.vigilante.app.data.local.VolunteerSort
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.local.entity.VolunteerStatus
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.VigilanteTopBar

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

@OptIn(ExperimentalMaterial3Api::class)
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
                        Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.filters))
                    }
                }
            )
        },
        floatingActionButton = {
            if (viewModel.canAdd()) {
                ExtendedFloatingActionButton(
                    onClick = onAddVolunteer,
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
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (volunteers.isEmpty()) {
                EmptyState(
                    text = stringResource(R.string.no_results),
                    icon = Icons.Filled.SearchOff
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp
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

@Composable
private fun VolunteerCard(
    volunteer: Volunteer,
    photoFile: java.io.File?,
    onClick: () -> Unit
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (photoFile != null) {
                AsyncImage(
                    model = photoFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    modifier = Modifier.size(56.dp),
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(volunteer.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${volunteer.volunteerId} • ${volunteer.membershipNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    listOfNotNull(volunteer.municipality, volunteer.phone1).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        if (volunteer.status == VolunteerStatus.ACTIVE)
                            stringResource(R.string.status_active)
                        else stringResource(R.string.status_archived)
                    )
                }
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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
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
            Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge)

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
                Text("مع صورة فقط", style = MaterialTheme.typography.bodyLarge)
            }
            DropdownField(
                label = stringResource(R.string.sort),
                selected = sortLabels[sort] ?: "",
                options = VolunteerSort.entries.map { sortLabels[it] ?: it.name },
                onSelected = { label ->
                    sort = sortLabels.entries.firstOrNull { it.value == label }?.key
                        ?: VolunteerSort.NAME
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onApply(VolunteerFilter()) },
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
                    modifier = Modifier.weight(1f)
                ) { Text("تطبيق") }
            }
        }
    }
}
