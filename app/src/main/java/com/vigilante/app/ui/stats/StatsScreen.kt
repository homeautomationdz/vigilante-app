package com.vigilante.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.BuildConfig
import com.vigilante.app.R
import com.vigilante.app.data.local.dao.GroupCount
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.LoadingBox
import com.vigilante.app.ui.components.SectionHeader
import com.vigilante.app.ui.components.StatCard
import com.vigilante.app.ui.components.VigilanteTopBar

private val chartColors = listOf(
    Color(0xFF00695C), Color(0xFF26A69A), Color(0xFF80CBC4), Color(0xFF004D40),
    Color(0xFF00897B), Color(0xFF4DB6AC), Color(0xFFB2DFDB), Color(0xFF00796B),
    Color(0xFF009688), Color(0xFF52C7B8)
)

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { VigilanteTopBar(title = stringResource(R.string.menu_stats), onBack = onBack) }
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val stats = state.stats
        if (stats == null) {
            EmptyState(text = state.error ?: "لا توجد بيانات", modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionHeader("المتطوعون")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("الإجمالي", (stats.totalActive + stats.totalArchived).toString(), modifier = Modifier.weight(1f))
                StatCard("نشط", stats.totalActive.toString(), modifier = Modifier.weight(1f))
                StatCard("مؤرشف", stats.totalArchived.toString(), modifier = Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                StatCard("منضمو السنة", stats.joinedThisYear.toString(), modifier = Modifier.weight(1f))
                StatCard("منضمو الشهر", stats.joinedThisMonth.toString(), modifier = Modifier.weight(1f))
            }

            if (BuildConfig.ATTENDANCE_ENABLED) {
                SectionHeader("الحضور", modifier = Modifier.padding(top = 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("اليوم", stats.attendanceToday.toString(), modifier = Modifier.weight(1f))
                    StatCard("الأسبوع", stats.attendanceThisWeek.toString(), modifier = Modifier.weight(1f))
                    StatCard("الشهر", stats.attendanceThisMonth.toString(), modifier = Modifier.weight(1f))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    StatCard("السنة", stats.attendanceThisYear.toString(), modifier = Modifier.weight(1f))
                    StatCard("الإجمالي", stats.attendanceTotal.toString(), modifier = Modifier.weight(1f))
                }
            }

            SectionHeader(stringResource(R.string.blood_group), modifier = Modifier.padding(top = 16.dp))
            if (stats.byBloodGroup.isEmpty()) {
                Text("لا توجد بيانات", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                BloodGroupPie(stats.byBloodGroup)
            }

            SectionHeader(stringResource(R.string.municipality), modifier = Modifier.padding(top = 16.dp))
            if (stats.byMunicipality.isEmpty()) {
                Text("لا توجد بيانات", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MunicipalityBars(stats.byMunicipality.take(10))
            }

            if (BuildConfig.ATTENDANCE_ENABLED) {
                SectionHeader("الأكثر حضورًا", modifier = Modifier.padding(top = 16.dp))
                AttendeeList(state.topAttendees)

                SectionHeader("الأقل حضورًا", modifier = Modifier.padding(top = 16.dp))
                AttendeeList(state.lowestAttendees)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BloodGroupPie(groups: List<GroupCount>) {
    val total = groups.sumOf { it.count }.coerceAtLeast(1)
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                var startAngle = -90f
                groups.forEachIndexed { index, group ->
                    val sweep = 360f * group.count / total
                    drawArc(
                        color = chartColors[index % chartColors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height)
                    )
                    startAngle += sweep
                }
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                groups.forEachIndexed { index, group ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(chartColors[index % chartColors.size])
                            }
                        }
                        Text(
                            "${group.bloodGroup ?: "غير محدد"}: ${group.count}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MunicipalityBars(groups: List<GroupCount>) {
    val max = groups.maxOf { it.count }.coerceAtLeast(1)
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            groups.forEachIndexed { index, group ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            group.bloodGroup ?: "غير محدد",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            group.count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val fraction = group.count.toFloat() / max
                    val color = chartColors[index % chartColors.size]
                    val track = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .padding(top = 4.dp)
                    ) {
                        drawRect(color = track, size = size)
                        drawRect(
                            color = color,
                            topLeft = Offset(size.width * (1f - fraction), 0f),
                            size = Size(size.width * fraction, size.height)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendeeList(items: List<NamedCount>) {
    if (items.isEmpty()) {
        Text("لا توجد بيانات", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        item.count.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (index < items.lastIndex) HorizontalDivider()
            }
        }
    }
}
