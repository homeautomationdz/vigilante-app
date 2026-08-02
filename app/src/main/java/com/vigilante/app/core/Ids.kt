package com.vigilante.app.core

/**
 * Entity ID prefixes — BR: all entities carry immutable, prefixed identifiers.
 *
 *  VOL-000001  volunteer (technical ID, never changes, never reused)
 *  ADM-000001  admin
 *  ATT-000001  attendance record
 *  LOG-000001  audit log entry
 *  BKP-000001  backup
 *
 * The visible Membership Number (e.g. 2026-0015) is a separate, editable
 * administrative field on the volunteer and is NEVER used as a foreign key.
 */
enum class EntityType(val prefix: String) {
    VOLUNTEER("VOL"),
    ADMIN("ADM"),
    ATTENDANCE("ATT"),
    AUDIT_LOG("LOG"),
    BACKUP("BKP");

    fun format(sequence: Long): String = "%s-%06d".format(prefix, sequence)
}

object IdParser {
    private val regex = Regex("^(VOL|ADM|ATT|LOG|BKP)-(\\d{6,})$")

    fun isValid(id: String): Boolean = regex.matches(id.trim())

    fun sequenceOf(id: String): Long? =
        regex.matchEntire(id.trim())?.groupValues?.get(2)?.toLongOrNull()

    /** QR codes contain ONLY the volunteer ID — no personal data. */
    fun isVolunteerId(id: String): Boolean =
        id.trim().startsWith("VOL-") && isValid(id)
}
