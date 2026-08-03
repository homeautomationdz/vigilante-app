package com.vigilante.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.R
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.RecycleBinEntry
import com.vigilante.app.data.repository.VolunteerRepository
import com.vigilante.app.ui.components.ChipTone
import com.vigilante.app.ui.components.ConfirmDialog
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.StatusChip
import com.vigilante.app.ui.components.VCard
import com.vigilante.app.ui.components.VigilanteTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val db: VigilanteDatabase,
    private val volunteerRepository: VolunteerRepository
) : ViewModel() {

    val entries: StateFlow<List<RecycleBinEntry>> = db.recycleBinDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun restore(entryId: Long) {
        viewModelScope.launch {
            volunteerRepository.restoreFromRecycleBin(entryId)
                .onSuccess { _message.value = "تمت استعادة المتطوع بكامل بياناته" }
                .onFailure { _message.value = it.message ?: "تعذرت الاستعادة" }
        }
    }

    fun deleteForever(entryId: Long) {
        viewModelScope.launch {
            runCatching { db.recycleBinDao().remove(entryId) }
                .onSuccess { _message.value = "تم الحذف النهائي" }
                .onFailure { _message.value = "تعذر الحذف" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var restoreTarget by remember { mutableStateOf<RecycleBinEntry?>(null) }
    var deleteFirstTarget by remember { mutableStateOf<RecycleBinEntry?>(null) }
    var deleteSecondTarget by remember { mutableStateOf<RecycleBinEntry?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { VigilanteTopBar(title = stringResource(R.string.recycle_bin), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(
                text = "سلة المحذوفات فارغة",
                icon = Icons.Filled.Delete,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    VCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                entry.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                entry.entityId,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "حُذف في ${entry.deletedAt.format(dateTimeFmt)} — بواسطة ${entry.deletedBy}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            StatusChip(
                                text = "يُحذف نهائيًا بعد ${entry.purgeAfter.format(dateTimeFmt)}",
                                tone = ChipTone.WARNING,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { restoreTarget = entry },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Filled.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.restore_action))
                                }
                                OutlinedButton(
                                    onClick = { deleteFirstTarget = entry },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.delete_permanently),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        restoreTarget?.let { target ->
            ConfirmDialog(
                title = stringResource(R.string.restore_action),
                text = "هل تريد استعادة ${target.displayName} بكامل بياناته وسجل حضوره؟",
                onConfirm = {
                    viewModel.restore(target.id)
                    restoreTarget = null
                },
                onDismiss = { restoreTarget = null }
            )
        }
        deleteFirstTarget?.let { target ->
            ConfirmDialog(
                title = stringResource(R.string.delete_permanently),
                text = "سيتم حذف ${target.displayName} نهائيًا ولا يمكن التراجع. هل تريد المتابعة؟",
                destructive = true,
                onConfirm = {
                    deleteFirstTarget = null
                    deleteSecondTarget = target
                },
                onDismiss = { deleteFirstTarget = null }
            )
        }
        deleteSecondTarget?.let { target ->
            ConfirmDialog(
                title = "تأكيد نهائي",
                text = "هذه العملية لا رجوع عنها إطلاقًا. تأكيد الحذف النهائي؟",
                confirmLabel = "حذف نهائي",
                destructive = true,
                onConfirm = {
                    viewModel.deleteForever(target.id)
                    deleteSecondTarget = null
                },
                onDismiss = { deleteSecondTarget = null }
            )
        }
    }
}
