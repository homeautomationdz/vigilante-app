package com.vigilante.app.data.local

import androidx.room.TypeConverter
import com.vigilante.app.data.local.entity.AdminRole
import com.vigilante.app.data.local.entity.AttendanceStatus
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.data.local.entity.AuditResult
import com.vigilante.app.data.local.entity.VolunteerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter fun fromDate(value: LocalDate?): String? = value?.format(dateFormat)
    @TypeConverter fun toDate(value: String?): LocalDate? =
        value?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it, dateFormat) }

    @TypeConverter fun fromDateTime(value: LocalDateTime?): String? = value?.format(dateTimeFormat)
    @TypeConverter fun toDateTime(value: String?): LocalDateTime? =
        value?.takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it, dateTimeFormat) }

    @TypeConverter fun fromVolunteerStatus(v: VolunteerStatus): String = v.name
    @TypeConverter fun toVolunteerStatus(v: String): VolunteerStatus = VolunteerStatus.valueOf(v)

    @TypeConverter fun fromAttendanceStatus(v: AttendanceStatus): String = v.name
    @TypeConverter fun toAttendanceStatus(v: String): AttendanceStatus = AttendanceStatus.valueOf(v)

    @TypeConverter fun fromRole(v: AdminRole): String = v.name
    @TypeConverter fun toRole(v: String): AdminRole = AdminRole.valueOf(v)

    @TypeConverter fun fromAction(v: AuditAction): String = v.name
    @TypeConverter fun toAction(v: String): AuditAction = AuditAction.valueOf(v)

    @TypeConverter fun fromResult(v: AuditResult): String = v.name
    @TypeConverter fun toResult(v: String): AuditResult = AuditResult.valueOf(v)
}
