package com.vigilante.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
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
import com.vigilante.app.ui.components.VCard
import com.vigilante.app.ui.components.VigilanteTopBar
import com.vigilante.app.ui.theme.AppColors

/**
 * Categorical series colours, derived from the theme so both light and dark
 * stay legible. Five base hues plus five softened tints of the same hues —
 * enough separation for up to ten slices without inventing raw hex values.
 */
@Composable
private fun chartPalette(): List<Color> {
    val c = AppColors.current
    val scheme = MaterialTheme.colorScheme
    val base = listOf(scheme.secondary, scheme.tertiary, c.statValue, c.warning, c.success)
    return base + base.map { it.copy(alpha = 0.55f) }
}

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
                .padding(horizontal = 16.dp)
        ) {
            SectionHeader("المتطوعون")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    "الإجمالي",
                    (stats.totalActive + stats.totalArchived).toString(),
                    icon = Icons.Filled.Groups,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    "نشط",
                    stats.totalActive.toString(),
                    icon = Icons.Filled.HowToReg,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    "مؤرشف",
                    stats.totalArchived.toString(),
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                StatCard(
                    "منضمو السنة",
                    stats.joinedThisYear.toString(),
                    icon = Icons.Filled.PersonAdd,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    "منضمو الشهر",
                    stats.joinedThisMonth.toString(),
                    icon = Icons.Filled.CalendarMonth,
                    modifier = Modifier.weight(1f)
                )
            }

            if (BuildConfig.ATTENDANCE_ENABLED) {
                SectionHeader("الحضور", modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        "اليوم",
                        stats.attendanceToday.toString(),
                        icon = Icons.Filled.Today,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        "الأسبوع",
                        stats.attendanceThisWeek.toString(),
                        icon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        "الشهر",
                        stats.attendanceThisMonth.toString(),
                        icon = Icons.Filled.CalendarMonth,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    StatCard(
                        "السنة",
                        stats.attendanceThisYear.toString(),
                        icon = Icons.Filled.DateRange,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        "الإجمالي",
                        stats.attendanceTotal.toString(),
                        icon = Icons.Filled.HowToReg,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SectionHeader(stringResource(R.string.blood_group), modifier = Modifier.padding(top = 10.dp))
            if (stats.byBloodGroup.isEmpty()) {
                NoDataCard()
            } else {
                BloodGroupPie(stats.byBloodGroup)
            }

            SectionHeader(stringResource(R.string.municipality), modifier = Modifier.padding(top = 10.dp))
            if (stats.byMunicipality.isEmpty()) {
                NoDataCard()
            } else {
                MunicipalityBars(stats.byMunicipality.take(10))
            }

            if (BuildConfig.ATTENDANCE_ENABLED) {
                SectionHeader("الأكثر حضورًا", modifier = Modifier.padding(top = 10.dp))
                AttendeeList(state.topAttendees)

                SectionHeader("الأقل حضورًا", modifier = Modifier.padding(top = 10.dp))
                AttendeeList(state.lowestAttendees)
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun NoDataCard() {
    VCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "لا توجد بيانات",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun BloodGroupPie(groups: List<GroupCount>) {
    val total = groups.sumOf { it.count }.coerceAtLeast(1)
    val palette = chartPalette()
    val holeColor = MaterialTheme.colorScheme.surface
    VCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(150.dp)) {
                var startAngle = -90f
                groups.forEachIndexed { index, group ->
                    val sweep = 360f * group.count / total
                    drawArc(
                        color = palette[index % palette.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height)
                    )
                    startAngle += sweep
                }
                // Donut hole keeps the slices readable and the centre calm.
                drawCircle(color = holeColor, radius = size.minDimension / 4f)
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                groups.forEachIndexed { index, group ->
                    val percent = group.count * 100 / total
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(palette[index % palette.size])
                        )
                        Text(
                            group.bloodGroup ?: "غير محدد",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Text(
                            "${group.count} ($percent٪)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val palette = chartPalette()
    val track = MaterialTheme.colorScheme.surfaceVariant
    VCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            groups.forEachIndexed { index, group ->
                val fraction = group.count.toFloat() / max
                val color = palette[index % palette.size]
                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                    Text(
                        group.bloodGroup ?: "غير محدد",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height(14.dp)
                        ) {
                            val radius = CornerRadius(size.height / 2f, size.height / 2f)
                            drawRoundRect(color = track, size = size, cornerRadius = radius)
                            // RTL: bars grow from the right edge towards the left.
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(size.width * (1f - fraction), 0f),
                                size = Size(size.width * fraction, size.height),
                                cornerRadius = radius
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            group.count.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = AppColors.current.statValue
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
        NoDataCard()
        return
    }
    VCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        item.count.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.current.statValue
                    )
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
