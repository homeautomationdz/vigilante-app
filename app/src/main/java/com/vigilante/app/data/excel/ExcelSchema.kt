package com.vigilante.app.data.excel

/**
 * SRS ch. 22 — Volunteers_Master.xlsx sheet & column names.
 * The app matches columns BY NAME, never by position (BR-010). Unknown
 * extra columns are ignored; a missing REQUIRED column aborts the import
 * with "ملف Excel غير صالح".
 */
object ExcelSchema {

    object Sheets {
        const val VOLUNTEERS = "Volunteers"
        const val ATTENDANCE = "Attendance"
        const val ADMINS = "Admins"
        const val AUDIT_LOG = "AuditLog"
        const val ARCHIVE = "Archive"
        const val SETTINGS = "Settings"
        const val BLOOD_GROUPS = "BloodGroups"
        const val METADATA = "Metadata"
    }

    object Volunteers {
        const val ID = "VolunteerID"
        const val MEMBERSHIP = "MembershipNumber"
        const val FIRST_NAME = "FirstName"
        const val LAST_NAME = "LastName"
        const val FATHER_NAME = "FatherName"
        const val BIRTH_DATE = "BirthDate"
        const val JOIN_DATE = "JoinDate"
        const val MUNICIPALITY = "Municipality"
        const val DISTRICT = "District"
        const val BLOOD_GROUP = "BloodGroup"
        const val PHONE1 = "Phone1"
        const val PHONE2 = "Phone2"
        const val PHOTO_PATH = "PhotoPath"
        const val NOTES = "Notes"
        const val STATUS = "Status"
        const val TAGS = "Tags"
        const val CREATED_BY = "CreatedBy"
        const val CREATED_AT = "CreatedAt"
        const val UPDATED_BY = "UpdatedBy"
        const val UPDATED_BY_ROLE = "UpdatedByRole"
        const val UPDATED_AT = "UpdatedAt"
        // Archive-only extras
        const val ARCHIVE_DATE = "ArchiveDate"
        const val ARCHIVED_BY = "ArchivedBy"
        const val ARCHIVE_REASON = "ArchiveReason"

        val REQUIRED = listOf(ID, FIRST_NAME, LAST_NAME, FATHER_NAME, PHONE1, JOIN_DATE)
        val ALL = listOf(
            ID, MEMBERSHIP, FIRST_NAME, LAST_NAME, FATHER_NAME, BIRTH_DATE, JOIN_DATE,
            MUNICIPALITY, DISTRICT, BLOOD_GROUP, PHONE1, PHONE2, PHOTO_PATH, NOTES,
            STATUS, TAGS, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_BY_ROLE, UPDATED_AT
        )
        val ARCHIVE_ALL = ALL + listOf(ARCHIVE_DATE, ARCHIVED_BY, ARCHIVE_REASON)
    }

    object Attendance {
        const val ID = "AttendanceID"
        const val VOLUNTEER_ID = "VolunteerID"
        const val DATE = "Date"
        const val TIME = "Time"
        const val ADMIN = "Admin"
        const val NOTES = "Notes"
        const val STATUS = "Status"
        val REQUIRED = listOf(ID, VOLUNTEER_ID, DATE)
        val ALL = listOf(ID, VOLUNTEER_ID, DATE, TIME, ADMIN, NOTES, STATUS)
    }

    object Admins {
        const val ID = "AdminID"
        const val FULL_NAME = "FullName"
        const val USERNAME = "Username"
        const val PASSWORD_HASH = "PasswordHash"
        const val ROLE = "Role"
        const val ACTIVE = "Active"
        const val CREATED_AT = "CreatedAt"
        const val LAST_LOGIN = "LastLogin"
        const val PERMISSIONS = "Permissions"
        val REQUIRED = listOf(ID, USERNAME, PASSWORD_HASH, ROLE)
        val ALL = listOf(ID, FULL_NAME, USERNAME, PASSWORD_HASH, ROLE, ACTIVE, CREATED_AT, LAST_LOGIN, PERMISSIONS)
    }

    object AuditLog {
        const val ID = "LogID"
        const val DATE = "Date"
        const val TIME = "Time"
        const val ADMIN = "Admin"
        const val ACTION = "Action"
        const val VOLUNTEER_ID = "VolunteerID"
        const val VOLUNTEER_NAME = "VolunteerName"
        const val DETAILS = "Details"
        const val RESULT = "Result"
        val ALL = listOf(ID, DATE, TIME, ADMIN, ACTION, VOLUNTEER_ID, VOLUNTEER_NAME, DETAILS, RESULT)
    }

    object Settings {
        const val KEY = "Key"
        const val VALUE = "Value"
        val ALL = listOf(KEY, VALUE)
    }

    object Metadata {
        const val KEY = "Key"
        const val VALUE = "Value"
        // Keys
        const val DB_VERSION = "DatabaseVersion"
        const val ORG_ID = "OrganizationID"
        const val CREATED_AT = "FileCreatedAt"
        const val UPDATED_AT = "FileUpdatedAt"
        const val APP_VERSION = "AppVersion"
    }
}
