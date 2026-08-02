package com.vigilante.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vigilante.app.data.local.dao.AdminDao
import com.vigilante.app.data.local.dao.AttendanceDao
import com.vigilante.app.data.local.dao.AuditLogDao
import com.vigilante.app.data.local.dao.BackupDao
import com.vigilante.app.data.local.dao.IdCounterDao
import com.vigilante.app.data.local.dao.PlacesDao
import com.vigilante.app.data.local.dao.RecycleBinDao
import com.vigilante.app.data.local.dao.SettingsDao
import com.vigilante.app.data.local.dao.TagDao
import com.vigilante.app.data.local.dao.VolunteerDao
import com.vigilante.app.data.local.entity.Admin
import com.vigilante.app.data.local.entity.AppSetting
import com.vigilante.app.data.local.entity.Attendance
import com.vigilante.app.data.local.entity.AuditLog
import com.vigilante.app.data.local.entity.BackupRecord
import com.vigilante.app.data.local.entity.District
import com.vigilante.app.data.local.entity.IdCounter
import com.vigilante.app.data.local.entity.Municipality
import com.vigilante.app.data.local.entity.LoginAttemptState
import com.vigilante.app.data.local.entity.RecycleBinEntry
import com.vigilante.app.data.local.entity.Tag
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.local.entity.VolunteerTag

/**
 * Runtime store. Room/SQLite gives us real ACID transactions (SRS: Atomic
 * Operations) and instant search over 100k+ rows, while Volunteers_Master.xlsx
 * remains the official interchange & backup format written by the Excel layer.
 * The repository layer is the only code that touches either side, so the
 * storage engine can be swapped without touching business logic (SRS ch. 46).
 */
@Database(
    entities = [
        Volunteer::class, Attendance::class, Admin::class, AuditLog::class,
        AppSetting::class, Tag::class, VolunteerTag::class,
        RecycleBinEntry::class, BackupRecord::class, IdCounter::class,
        LoginAttemptState::class, Municipality::class, District::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VigilanteDatabase : RoomDatabase() {
    abstract fun volunteerDao(): VolunteerDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun adminDao(): AdminDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun settingsDao(): SettingsDao
    abstract fun tagDao(): TagDao
    abstract fun recycleBinDao(): RecycleBinDao
    abstract fun backupDao(): BackupDao
    abstract fun idCounterDao(): IdCounterDao
    abstract fun placesDao(): PlacesDao
}
