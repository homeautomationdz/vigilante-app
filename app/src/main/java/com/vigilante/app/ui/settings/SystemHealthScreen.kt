package com.vigilante.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.R
import com.vigilante.app.data.repository.IntegrityChecker
import com.vigilante.app.data.repository.IntegrityReport
import com.vigilante.app.ui.components.LoadingBox
import com.vigilante.app.ui.components.SectionHeader
import com.vigilante.app.ui.components.VigilanteTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SystemHealthUiState(
    val loading: Boolean = true,
    val report: IntegrityReport? = null,
    val fixing: Boolean = false
)

@HiltViewModel
class SystemHealthViewModel @Inject constructor(
    private val checker: IntegrityChecker
) : ViewModel() {

    private val _state = MutableStateFlow(SystemHealthUiState())
    val state: StateFlow<SystemHealthUiState> = _state

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        run()
    }

    fun run() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            runCatching { withContext(Dispatchers.IO) { checker.run() } }
                .onSuccess { _state.value = SystemHealthUiState(loading = false, report = it) }
                .onFailure {
                    _state.value = SystemHealthUiState(loading = false)
                    _message.value = "تعذر إجراء الفحص"
                }
        }
    }

    fun autoFix() {
        val report = _state.value.report ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(fixing = true)
            runCatching { withContext(Dispatchers.IO) { checker.autoFix(report) } }
                .onSuccess { fixed ->
                    _message.value = if (fixed > 0) "تم إصلاح $fixed عنصرًا"
                    else "لا يوجد ما يمكن إصلاحه تلقائيًا"
                }
                .onFailure { _message.value = "تعذر الإصلاح التلقائي" }
            run()
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
fun SystemHealthScreen(
    onBack: () -> Unit,
    viewModel: SystemHealthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { VigilanteTopBar(title = stringResource(R.string.system_health), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val report = state.report ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                if (report.isClean) "✅ النظام سليم" else "⚠️ توجد ملاحظات",
                style = MaterialTheme.typography.headlineMedium,
                color = if (report.isClean) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Text(
                "آخر فحص: ${report.checkedAt.format(dateTimeFmt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            SectionHeader("نتائج الفحص")
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    HealthRow("صور مفقودة", report.missingPhotos.size)
                    HorizontalDivider()
                    HealthRow("رموز QR مفقودة (تُصلح تلقائيًا)", report.missingQr.size)
                    HorizontalDivider()
                    HealthRow("صور بلا متطوع", report.orphanPhotos.size)
                    HorizontalDivider()
                    HealthRow("رموز QR بلا متطوع", report.orphanQr.size)
                    HorizontalDivider()
                    HealthRow("سجلات حضور معلقة", report.orphanAttendance.size)
                    HorizontalDivider()
                    HealthRow("معرفات غير صالحة", report.invalidIds.size)
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("آخر نسخة احتياطية", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            report.lastBackup ?: "لا توجد",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                    HealthBoolRow("الملف الرئيسي موجود", report.masterFileExists)
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::autoFix,
                enabled = !state.fixing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Build, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إصلاح تلقائي", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            if (count == 0) "✅ 0" else "⚠️ $count",
            style = MaterialTheme.typography.titleMedium,
            color = if (count == 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun HealthBoolRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            if (ok) "✅" else "⚠️",
            style = MaterialTheme.typography.titleMedium,
            color = if (ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
    }
}
