package com.vigilante.app.data.files

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies an export file into the phone's public Downloads folder (user
 * decision: exported files land in التنزيلات, not only inside app storage).
 * Returns the user-visible location, e.g. "Download/Export_2026-08-02.xlsx".
 */
@Singleton
class DownloadsWriter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun write(source: File, displayName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(source, displayName)
        } else {
            writeLegacy(source, displayName)
        }
    }

    private fun writeViaMediaStore(source: File, displayName: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: error("تعذر إنشاء الملف في مجلد التنزيلات")
        resolver.openOutputStream(uri).use { out ->
            checkNotNull(out) { "تعذر فتح ملف التنزيلات للكتابة" }
            FileInputStream(source).use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "Download/$displayName"
    }

    @Suppress("DEPRECATION")
    private fun writeLegacy(source: File, displayName: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, displayName)
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { input.copyTo(it) }
        }
        return "Download/$displayName"
    }
}
