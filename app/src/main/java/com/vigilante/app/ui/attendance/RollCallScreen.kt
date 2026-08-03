package com.vigilante.app.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.data.local.dao.RollCallRow
import com.vigilante.app.ui.volunteers.Avatar
import com.vigilante.app.ui.components.ChipTone
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.HeaderPanel
import com.vigilante.app.ui.components.LoadingBox
import com.vigilante.app.ui.components.StatusChip
import com.vigilante.app.ui.components.VCard
import com.vigilante.app.ui.theme.AppColors
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * نداء الحضور — the supervisor scrolls one list and taps حاضر for whoever is
 * there. Nobody needs to hold up a QR code. Regulars sort to the top, and the
 * order is frozen on load so rows never move while the list is being tapped.
 */
@Composable
fun RollCallScreen(
    onBack: () -> Unit,
    onOpenVolunteer: (String) -> Unit,
    viewModel: RollCallViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val header = AppColors.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(header.headerStart)
                    .statusBarsPadding()
            )
            HeaderPanel(
                title = "نداء الحضور",
                subtitle = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                onBack = onBack
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TallyPill(
                        label = "حاضر",
                        value = state.presentCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    TallyPill(
                        label = "غائب",
                        value = state.absentCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    TallyPill(
                        label = "الإجمالي",
                        value = state.rows.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (state.loading) {
                LoadingBox()
                return@Column
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQuery,
                placeholder = { Text("ابحث عن عضو…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.onQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            val rows = state.visibleRows
            if (rows.isEmpty()) {
                EmptyState(
                    text = if (state.rows.isEmpty()) "لا يوجد متطوعون نشطون"
                    else "لا توجد نتائج مطابقة",
                    icon = Icons.Filled.Groups
                )
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rows, key = { it.volunteerId }) { row ->
                    RollCallItem(
                        row = row,
                        photoFile = viewModel.photoFile(row.volunteerId),
                        present = row.volunteerId in state.presentIds,
                        busy = state.busyId == row.volunteerId,
                        onToggle = { viewModel.toggle(row.volunteerId) },
                        onOpen = { onOpenVolunteer(row.volunteerId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TallyPill(label: String, value: String, modifier: Modifier = Modifier) {
    val c = AppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.headerEnd)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = c.onHeader)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.onHeaderMuted)
    }
}

@Composable
private fun RollCallItem(
    row: RollCallRow,
    photoFile: File?,
    present: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    val c = AppColors.current
    VCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(photoFile = photoFile, name = row.displayName, size = 44)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    lastSeenLabel(row),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.recentCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    StatusChip(
                        text = "حضر ${row.recentCount} مرة مؤخرًا",
                        tone = ChipTone.INFO
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Button(
                onClick = onToggle,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                colors = if (present) {
                    ButtonDefaults.buttonColors(
                        containerColor = c.success,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.height(46.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        if (present) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (present) "حاضر" else "غائب",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/** "آخر حضور: منذ ٣ أيام" — the number the supervisor actually scans for. */
private fun lastSeenLabel(row: RollCallRow): String {
    val last = row.lastAt ?: return "لم يحضر من قبل"
    val parsed = runCatching { LocalDateTime.parse(last) }.getOrNull()
        ?: return "آخر حضور: $last"
    val days = java.time.temporal.ChronoUnit.DAYS.between(parsed.toLocalDate(), LocalDate.now())
    return when {
        days <= 0L -> "حضر اليوم"
        days == 1L -> "آخر حضور: أمس"
        days < 30L -> "آخر حضور: منذ $days يومًا"
        else -> "آخر حضور: ${parsed.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
    }
}
