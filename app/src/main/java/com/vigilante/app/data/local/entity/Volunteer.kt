package com.vigilante.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

enum class VolunteerStatus { ACTIVE, ARCHIVED }

/**
 * SRS ch. 5 / ch. 22 — Volunteers sheet.
 *
 * [volunteerId] is the immutable technical ID (VOL-000001) used for every
 * relation, QR code, photo filename and merge decision.
 * [membershipNumber] is the human-facing administrative number (e.g. 2026-0015);
 * it can be edited or re-organized without touching any relation.
 */
@Entity(
    tableName = "volunteers",
    indices = [
        Index("membershipNumber"),
        Index("phone1"),
        Index("lastName", "firstName"),
        Index("municipality"),
        Index("status")
    ]
)
data class Volunteer(
    @PrimaryKey val volunteerId: String,
    val membershipNumber: String,
    val firstName: String,
    val lastName: String,
    val fatherName: String,
    val birthDate: LocalDate?,
    val joinDate: LocalDate,
    val municipality: String?,
    val district: String?,
    val bloodGroup: String?,
    val phone1: String,
    val phone2: String?,
    val photoPath: String?,
    val notes: String?,
    val status: VolunteerStatus = VolunteerStatus.ACTIVE,
    // Archive metadata (SRS ch. 17)
    val archiveDate: LocalDateTime? = null,
    val archivedBy: String? = null,
    val archiveReason: String? = null,
    // Audit / merge metadata (SRS ch. 27 — role outranks timestamp)
    val createdBy: String,
    val createdAt: LocalDateTime,
    val updatedBy: String? = null,
    val updatedByRole: String? = null,
    val updatedAt: LocalDateTime? = null
) {
    val fullName: String get() = "$firstName $lastName"
    val displayName: String get() = "$firstName $fatherName $lastName"
}
