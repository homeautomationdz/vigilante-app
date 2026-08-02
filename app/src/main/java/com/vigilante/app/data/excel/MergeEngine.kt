package com.vigilante.app.data.excel

import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.AdminRole
import com.vigilante.app.data.local.entity.Volunteer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SRS ch. 27 — Data Merge Policy.
 *
 * Golden rule: صلاحية المستخدم أهم من وقت التعديل — the editor's ROLE outranks
 * the edit TIMESTAMP. Super Admin edits silently win over Admin edits in both
 * directions; equal-role concurrent edits become CONFLICTs that the Super
 * Admin resolves by hand. Timestamps only break ties when roles are equal and
 * a decision has already been delegated to "أحدث عملية" (archive-vs-edit case).
 */
@Singleton
class MergeEngine @Inject constructor(private val db: VigilanteDatabase) {

    suspend fun plan(imported: ImportedData): MergePlan {
        val items = mutableListOf<MergeItem>()

        for ((incoming, tags) in imported.volunteers) {
            val current = db.volunteerDao().byId(incoming.volunteerId)
            items += when {
                current == null ->
                    MergeItem(MergeDecisionKind.NEW, incoming, tags, null, "متطوع جديد")
                contentEquals(current, incoming) ->
                    MergeItem(MergeDecisionKind.UNCHANGED, incoming, tags, current, "لا تغيير")
                else -> resolve(current, incoming, tags)
            }
        }

        // Attendance: append-only — records with unknown IDs are added,
        // existing IDs ignored (attendance is never edited by merge).
        val existingAttIds = db.attendanceDao().all().mapTo(HashSet()) { it.attendanceId }
        val newAttendance = imported.attendance.filter { it.attendanceId !in existingAttIds }

        // Admins: only add unknown admins; existing accounts are never
        // overwritten by an import (local Super Admin stays in control).
        val existingUsernames = db.adminDao().allOnce().mapTo(HashSet()) { it.username }
        val newAdmins = imported.admins.filter { it.username !in existingUsernames }

        return MergePlan(
            items = items,
            newAttendance = newAttendance,
            newAdmins = newAdmins,
            ignoredAttendance = imported.attendance.size - newAttendance.size
        )
    }

    private fun resolve(current: Volunteer, incoming: Volunteer, tags: List<String>): MergeItem {
        val curRole = roleRank(current.updatedByRole)
        val incRole = roleRank(incoming.updatedByRole)
        return when {
            // Cases 1 & 2: role difference decides silently, and is audited.
            incRole > curRole -> MergeItem(
                MergeDecisionKind.AUTO_APPLY, incoming, tags, current,
                "اعتماد النسخة المستوردة — صلاحية أعلى (${incoming.updatedBy ?: "?"})"
            )
            incRole < curRole -> MergeItem(
                MergeDecisionKind.AUTO_IGNORE, incoming, tags, current,
                "تجاهل النسخة المستوردة — صلاحية أدنى (${incoming.updatedBy ?: "?"})"
            )
            // Case 5 (archive vs edit, same role): newest operation wins.
            current.status != incoming.status -> {
                val curTime = current.updatedAt ?: current.createdAt
                val incTime = incoming.updatedAt ?: incoming.createdAt
                if (incTime.isAfter(curTime)) MergeItem(
                    MergeDecisionKind.AUTO_APPLY, incoming, tags, current,
                    "اعتماد النسخة المستوردة — عملية أحدث بنفس الصلاحية"
                ) else MergeItem(
                    MergeDecisionKind.AUTO_IGNORE, incoming, tags, current,
                    "الاحتفاظ بالنسخة الحالية — عملية أحدث بنفس الصلاحية"
                )
            }
            // Case 3: same role, both edited → Super Admin decides.
            else -> MergeItem(
                MergeDecisionKind.CONFLICT, incoming, tags, current,
                "تعديل متزامن من مشرفين بنفس الصلاحية"
            )
        }
    }

    private fun roleRank(role: String?): Int = when (role) {
        AdminRole.SUPER_ADMIN.name -> 2
        AdminRole.ADMIN.name -> 1
        else -> 0
    }

    /** Field-level equality ignoring audit stamps. */
    private fun contentEquals(a: Volunteer, b: Volunteer): Boolean =
        a.membershipNumber == b.membershipNumber &&
            a.firstName == b.firstName && a.lastName == b.lastName &&
            a.fatherName == b.fatherName && a.birthDate == b.birthDate &&
            a.joinDate == b.joinDate && a.municipality == b.municipality &&
            a.district == b.district && a.bloodGroup == b.bloodGroup &&
            a.phone1 == b.phone1 && a.phone2 == b.phone2 &&
            a.notes == b.notes && a.status == b.status
}
