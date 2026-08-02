package com.vigilante.app.ui.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.R
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.ui.components.ConfirmDialog
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.VigilanteTopBar
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd")

@Composable
fun ArchiveScreen(
    onBack: () -> Unit,
    onOpenVolunteer: (String) -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel()
) {
    val archived by viewModel.archived.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var restoreTarget by remember { mutableStateOf<Volunteer?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { VigilanteTopBar(title = stringResource(R.string.menu_archive), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (archived.isEmpty()) {
            EmptyState(
                text = "الأرشيف فارغ",
                icon = Icons.Filled.Inventory2,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(archived, key = { it.volunteerId }) { volunteer ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenVolunteer(volunteer.volunteerId) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        volunteer.displayName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        volunteer.volunteerId + " • " + volunteer.membershipNumber,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (viewModel.canRestore()) {
                                    OutlinedButton(onClick = { restoreTarget = volunteer }) {
                                        Icon(
                                            Icons.Filled.Restore,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(stringResource(R.string.restore_action))
                                    }
                                }
                            }
                            Text(
                                buildString {
                                    volunteer.archiveDate?.let { append("أُرشف في ${it.format(dateFmt)}") }
                                    volunteer.archivedBy?.let { append(" — بواسطة $it") }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            volunteer.archiveReason?.let {
                                Text(
                                    "السبب: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        restoreTarget?.let { target ->
            ConfirmDialog(
                title = stringResource(R.string.restore_action),
                text = "هل تريد استعادة ${target.displayName} من الأرشيف؟",
                onConfirm = {
                    viewModel.restore(target.volunteerId)
                    restoreTarget = null
                },
                onDismiss = { restoreTarget = null }
            )
        }
    }
}
