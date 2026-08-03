package com.vigilante.app.data.excel

import com.vigilante.app.data.local.entity.Admin
import com.vigilante.app.data.local.entity.Attendance
import com.vigilante.app.data.local.entity.Volunteer

/** Row-level validation problem reported before any import (SRS ch. 13). */
data class RowError(
    val sheet: String,
    val rowNumber: Int,          // 1-based, as the user sees it in Excel
    val message: String
)

/** Parsed content of a Volunteers_Import.xlsx file. */
data class ImportedData(
    val volunteers: List<Pair<Volunteer, List<String>>>,   // volunteer + tag names
    val attendance: List<Attendance>,
    val admins: List<Admin>,
    val metadata: Map<String, String>
)

sealed class ImportReadResult {
    data class Success(val data: ImportedData) : ImportReadResult()
    data class InvalidFile(val reason: String) : ImportReadResult()
    data class ValidationFailed(val errors: List<RowError>) : ImportReadResult()
    /** The file is encrypted and no (or no correct) password was supplied yet. */
    data class NeedsPassword(val wrongAttempt: Boolean) : ImportReadResult()
    /** Organization mismatch warning (SRS ch. 34) — user may still proceed. */
    data class WrongOrganization(val fileOrgId: String, val localOrgId: String, val data: ImportedData) : ImportReadResult()
}

/** SRS ch. 27 — what the merge engine decided per volunteer. */
enum class MergeDecisionKind {
    NEW,                 // exists only in the import → add
    UNCHANGED,           // identical → ignore
    AUTO_APPLY,          // higher-role (or newer same-role) import wins silently
    AUTO_IGNORE,         // lower-role import loses silently (logged)
    CONFLICT             // same-role concurrent edits → Super Admin decides
}

data class MergeItem(
    val kind: MergeDecisionKind,
    val incoming: Volunteer,
    val incomingTags: List<String>,
    val current: Volunteer?,
    val reason: String
)

data class MergePlan(
    val items: List<MergeItem>,
    val newAttendance: List<Attendance>,
    val newAdmins: List<Admin>,
    val ignoredAttendance: Int
) {
    val newCount get() = items.count { it.kind == MergeDecisionKind.NEW }
    val autoApplyCount get() = items.count { it.kind == MergeDecisionKind.AUTO_APPLY }
    val autoIgnoreCount get() = items.count { it.kind == MergeDecisionKind.AUTO_IGNORE }
    val conflictCount get() = items.count { it.kind == MergeDecisionKind.CONFLICT }
    val unchangedCount get() = items.count { it.kind == MergeDecisionKind.UNCHANGED }
}

/** Super Admin's answer for each conflict. */
enum class ConflictResolution { KEEP_CURRENT, TAKE_IMPORTED }

data class ImportReport(
    val added: Int,
    val updated: Int,
    val ignored: Int,
    val attendanceAdded: Int,
    val adminsAdded: Int,
    val backupFileName: String
)
