package com.vigilante.app.data.repository

import com.vigilante.app.core.EntityType
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.AuditAction
import com.vigilante.app.data.local.entity.AuditLog
import com.vigilante.app.data.local.entity.AuditResult
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central audit writer (SRS ch. 15). Call [log] INSIDE the same Room
 * transaction as the operation being recorded so the operation and its audit
 * entry commit or roll back together (Atomic Operations).
 */
@Singleton
class AuditLogger @Inject constructor(private val db: VigilanteDatabase) {

    suspend fun log(
        adminUsername: String,
        action: AuditAction,
        details: String,
        volunteerId: String? = null,
        volunteerName: String? = null,
        result: AuditResult = AuditResult.SUCCESS
    ) {
        val seq = db.idCounterDao().next(EntityType.AUDIT_LOG.name)
        db.auditLogDao().insert(
            AuditLog(
                logId = EntityType.AUDIT_LOG.format(seq),
                timestamp = LocalDateTime.now(),
                adminUsername = adminUsername,
                action = action,
                volunteerId = volunteerId,
                volunteerName = volunteerName,
                details = details,
                result = result
            )
        )
    }
}
