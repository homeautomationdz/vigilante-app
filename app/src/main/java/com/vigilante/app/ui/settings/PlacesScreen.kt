package com.vigilante.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.data.local.entity.District
import com.vigilante.app.data.local.entity.Municipality
import com.vigilante.app.ui.components.ConfirmDialog
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.SectionHeader
import com.vigilante.app.ui.components.VCard
import com.vigilante.app.ui.components.VigilanteTopBar

/**
 * Admin-managed بلديات/أحياء lists (user request): what is added here feeds the
 * optional dropdowns on the add/edit volunteer form.
 */
@Composable
fun PlacesScreen(
    onBack: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val municipalities by viewModel.municipalities.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var newMunicipality by remember { mutableStateOf("") }
    var pendingDeleteMunicipality by remember { mutableStateOf<Municipality?>(null) }
    var pendingDeleteDistrict by remember { mutableStateOf<District?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { VigilanteTopBar(title = "إدارة الأماكن", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            SectionHeader("البلديات")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newMunicipality,
                    onValueChange = { newMunicipality = it },
                    label = { Text("إضافة بلدية") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        viewModel.addMunicipality(newMunicipality)
                        newMunicipality = ""
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "إضافة بلدية",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (municipalities.isEmpty()) {
                EmptyState(
                    text = "لا توجد بلديات بعد — أضف الأولى من الحقل أعلاه",
                    icon = Icons.Filled.Place
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
                ) {
                    items(municipalities, key = { it.id }) { municipality ->
                        MunicipalityCard(
                            municipality = municipality,
                            viewModel = viewModel,
                            onDeleteMunicipality = { pendingDeleteMunicipality = municipality },
                            onDeleteDistrict = { pendingDeleteDistrict = it }
                        )
                    }
                }
            }
        }

        pendingDeleteMunicipality?.let { municipality ->
            ConfirmDialog(
                title = "حذف البلدية",
                text = "هل تريد حذف بلدية \"${municipality.name}\"؟\n" +
                    "سيؤدي ذلك أيضًا إلى حذف جميع الأحياء التابعة لها من القائمة.",
                confirmLabel = "حذف",
                destructive = true,
                onConfirm = {
                    viewModel.deleteMunicipality(municipality.id)
                    pendingDeleteMunicipality = null
                },
                onDismiss = { pendingDeleteMunicipality = null }
            )
        }
        pendingDeleteDistrict?.let { district ->
            ConfirmDialog(
                title = "حذف الحي",
                text = "هل تريد حذف حي \"${district.name}\" من القائمة؟",
                confirmLabel = "حذف",
                destructive = true,
                onConfirm = {
                    viewModel.deleteDistrict(district.id)
                    pendingDeleteDistrict = null
                },
                onDismiss = { pendingDeleteDistrict = null }
            )
        }
    }
}

@Composable
private fun MunicipalityCard(
    municipality: Municipality,
    viewModel: PlacesViewModel,
    onDeleteMunicipality: () -> Unit,
    onDeleteDistrict: (District) -> Unit
) {
    var expanded by remember(municipality.id) { mutableStateOf(false) }
    var newDistrict by remember(municipality.id) { mutableStateOf("") }
    val districtsFlow = remember(municipality.id) { viewModel.districtsOf(municipality.id) }
    val districts by districtsFlow.collectAsState(initial = emptyList())

    VCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    municipality.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                IconButton(onClick = onDeleteMunicipality) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "حذف البلدية",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    if (districts.isEmpty()) {
                        Text(
                            "لا توجد أحياء بعد",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    districts.forEach { district ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                district.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeleteDistrict(district) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "حذف الحي",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newDistrict,
                            onValueChange = { newDistrict = it },
                            label = { Text("إضافة حي") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                viewModel.addDistrict(municipality.id, newDistrict)
                                newDistrict = ""
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "إضافة حي",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
