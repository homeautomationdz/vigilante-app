package com.vigilante.app.core

import android.content.Context
import java.io.File

/**
 * Official on-disk layout (SRS ch. 24 / final revision):
 *
 * Vigilante/
 *  ├── Database/   Volunteers_Master.xlsx
 *  ├── Photos/     VOL-000001.jpg
 *  ├── QR/         VOL-000001.png
 *  ├── Backup/     Backup_YYYY-MM-DD_HH-MM.xlsx
 *  ├── Export/     Export_YYYY-MM-DD.xlsx
 *  ├── Import/     Volunteers_Import.xlsx
 *  └── Logs/       system error logs
 *
 * Stored under the app's external files dir so the supervisor can copy the
 * whole folder from the device without root access.
 */
class AppFolders(context: Context) {

    val root: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "Vigilante")

    val database: File get() = sub("Database")
    val photos: File get() = sub("Photos")
    val qr: File get() = sub("QR")
    val backup: File get() = sub("Backup")
    val export: File get() = sub("Export")
    val import: File get() = sub("Import")
    val logs: File get() = sub("Logs")

    val masterFile: File get() = File(database, MASTER_FILE_NAME)

    fun photoFile(volunteerId: String): File = File(photos, "$volunteerId.jpg")
    fun qrFile(volunteerId: String): File = File(qr, "$volunteerId.png")

    fun ensureAll() {
        listOf(root, database, photos, qr, backup, export, import, logs)
            .forEach { if (!it.exists()) it.mkdirs() }
    }

    private fun sub(name: String) = File(root, name)

    companion object {
        const val MASTER_FILE_NAME = "Volunteers_Master.xlsx"
        const val IMPORT_FILE_NAME = "Volunteers_Import.xlsx"
        const val TEMPLATE_FILE_NAME = "Template.xlsx"
    }
}
