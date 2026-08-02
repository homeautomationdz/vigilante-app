package com.vigilante.app.ui.settings

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
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.R
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.AuditLog
import com.vigilante.app.data.local.entity.AuditResult
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.VigilanteTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AuditLogViewModel @Inject constructor(
    db: VigilanteDatabase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val logs: StateFlow<List<AuditLog>> = _query
        .flatMapLatest { db.auditLogDao().search(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }
}

private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")

@Composable
fun AuditLogScreen(
    onBack: () -> Unit,
    viewModel: AuditLogViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val logs by viewModel.logs.collectAsState()

    Scaffold(
        topBar = { VigilanteTopBar(title = stringResource(R.string.audit_log), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("ابحث بالمشرف أو العملية أو المتطوع…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (logs.isEmpty()) {
                EmptyState(
                    text = stringResource(R.string.no_results),
                    icon = Icons.AutoMirrored.Filled.ListAlt
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(logs, key = { it.logId }) { log ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        log.adminUsername,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        log.timestamp.format(dateTimeFmt),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    log.action.name +
                                        (log.volunteerName?.let { " — $it" } ?: ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (log.result == AuditResult.FAILURE)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                                Text(log.details, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
