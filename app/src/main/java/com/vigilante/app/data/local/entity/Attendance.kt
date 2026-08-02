package com.vigilante.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

enum class AttendanceStatus { VALID, CANCELLED }

/**
 * SRS ch. 7 / ch. 22 — Attendance sheet.
 * BR-007: attendance records are never deleted; a mistaken record is
 * logically cancelled ([AttendanceStatus.CANCELLED]) with an explanatory note.
 */
@Entity(
    tableName = "attendance",
    indices = [Index("volunteerId"), Index("recordedAt")],
    foreignKeys = [
        ForeignKey(
            entity = Volunteer::class,
            parentColumns = ["volunteerId"],
            childColumns = ["volunteerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class Attendance(
    @PrimaryKey val attendanceId: String,   // ATT-000001
    val volunteerId: String,                // VOL-000001
    val recordedAt: LocalDateTime,
    val adminUsername: String,
    val notes: String? = null,
    val status: AttendanceStatus = AttendanceStatus.VALID
)
