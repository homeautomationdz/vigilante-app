package com.vigilante.app.core

import java.time.LocalDate

/** SRS ch. 38 — data validation rules. */
object Validation {

    /** Blood groups are a fixed list; free-text values are rejected. */
    val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    fun isValidBloodGroup(value: String?): Boolean =
        value.isNullOrBlank() || value.trim() in BLOOD_GROUPS

    /** Digits only, 8–15 digits (local formats can be layered on later). */
    fun isValidPhone(value: String): Boolean =
        value.trim().matches(Regex("^\\d{8,15}$"))

    fun normalizeName(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    sealed class DateError {
        data object InFuture : DateError()
        data object JoinBeforeBirth : DateError()
    }

    fun validateBirthDate(birth: LocalDate?, today: LocalDate): DateError? =
        if (birth != null && birth.isAfter(today)) DateError.InFuture else null

    fun validateJoinDate(join: LocalDate, birth: LocalDate?, today: LocalDate): DateError? = when {
        join.isAfter(today) -> DateError.InFuture
        birth != null && join.isBefore(birth) -> DateError.JoinBeforeBirth
        else -> null
    }

    /** Password policy: minimum 8 chars, at least one letter and one digit. */
    fun isValidPassword(password: String): Boolean =
        password.length >= 8 &&
            password.any { it.isDigit() } &&
            password.any { it.isLetter() }

    fun isValidUsername(username: String): Boolean =
        username.trim().matches(Regex("^[\\p{L}\\p{N}_.-]{3,32}$"))
}
