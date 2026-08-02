package com.vigilante.app.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/** SRS ch. 10/11/18 — composable filters + sorting, built as one SQL query. */
data class VolunteerFilter(
    val searchText: String = "",
    val municipality: String? = null,
    val district: String? = null,
    val bloodGroup: String? = null,
    val status: String? = "ACTIVE",          // null = both
    val joinedFrom: String? = null,          // ISO date
    val joinedTo: String? = null,
    val minAttendance: Int? = null,
    val hasPhoto: Boolean? = null,
    val tagName: String? = null,
    val sort: VolunteerSort = VolunteerSort.NAME
)

enum class VolunteerSort(val sql: String) {
    NAME("v.firstName COLLATE NOCASE, v.lastName COLLATE NOCASE"),
    LAST_NAME("v.lastName COLLATE NOCASE, v.firstName COLLATE NOCASE"),
    BIRTH_DATE("v.birthDate"),
    JOIN_DATE("v.joinDate DESC"),
    MOST_ATTENDANCE("attCount DESC"),
    LEAST_ATTENDANCE("attCount ASC"),
    NEWEST("v.createdAt DESC"),
    OLDEST("v.createdAt ASC")
}

object VolunteerQueryBuilder {

    fun build(f: VolunteerFilter): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val where = StringBuilder("1=1")

        if (f.searchText.isNotBlank()) {
            where.append(
                """ AND (v.volunteerId LIKE '%'||?||'%' OR v.membershipNumber LIKE '%'||?||'%'
                    OR v.firstName LIKE '%'||?||'%' OR v.lastName LIKE '%'||?||'%'
                    OR v.fatherName LIKE '%'||?||'%' OR v.phone1 LIKE '%'||?||'%'
                    OR v.phone2 LIKE '%'||?||'%' OR v.municipality LIKE '%'||?||'%'
                    OR v.district LIKE '%'||?||'%')"""
            )
            repeat(9) { args.add(f.searchText.trim()) }
        }
        f.municipality?.let { where.append(" AND v.municipality = ?"); args.add(it) }
        f.district?.let { where.append(" AND v.district = ?"); args.add(it) }
        f.bloodGroup?.let { where.append(" AND v.bloodGroup = ?"); args.add(it) }
        f.status?.let { where.append(" AND v.status = ?"); args.add(it) }
        f.joinedFrom?.let { where.append(" AND v.joinDate >= ?"); args.add(it) }
        f.joinedTo?.let { where.append(" AND v.joinDate <= ?"); args.add(it) }
        f.hasPhoto?.let {
            where.append(if (it) " AND v.photoPath IS NOT NULL" else " AND v.photoPath IS NULL")
        }
        f.tagName?.let {
            where.append(
                """ AND v.volunteerId IN (
                    SELECT vt.volunteerId FROM volunteer_tags vt
                    JOIN tags t ON t.tagId = vt.tagId WHERE t.name = ?)"""
            )
            args.add(it)
        }
        val having = f.minAttendance?.let { min ->
            args.add(min); " HAVING attCount >= ?"
        } ?: ""

        val sql = """
            SELECT v.*, COUNT(a.attendanceId) AS attCount
            FROM volunteers v
            LEFT JOIN attendance a ON a.volunteerId = v.volunteerId AND a.status = 'VALID'
            WHERE $where
            GROUP BY v.volunteerId
            $having
            ORDER BY ${f.sort.sql}
        """.trimIndent()

        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
