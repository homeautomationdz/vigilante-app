package com.vigilante.app.core

import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SRS ch. 31 — internal technical error log at Vigilante/Logs/system.log.
 * Nothing may fail silently: every export/import/backup failure lands here
 * with a full stack trace so problems can be diagnosed from the device.
 */
@Singleton
class SystemLogger @Inject constructor(private val folders: AppFolders) {

    @Synchronized
    fun log(tag: String, message: String, error: Throwable? = null) {
        runCatching {
            folders.ensureAll()
            val file = File(folders.logs, "system.log")
            if (file.exists() && file.length() > MAX_BYTES) {
                val rotated = File(folders.logs, "system.1.log")
                if (rotated.exists()) rotated.delete()
                file.renameTo(rotated)
            }
            val entry = buildString {
                append(LocalDateTime.now()).append("  [").append(tag).append("]  ").append(message)
                error?.let { append('\n').append(it.stackTraceToString()) }
                append('\n')
            }
            File(folders.logs, "system.log").appendText(entry)
        }
    }

    private companion object { const val MAX_BYTES = 512 * 1024L }
}
