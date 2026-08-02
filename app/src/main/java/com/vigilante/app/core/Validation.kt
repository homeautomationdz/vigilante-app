package com.vigilante.app.core

import java.time.LocalDate

/** SRS ch. 38 — data validation rules. */
object Validation {

    /** Blood groups are a fixed list; free-text values are rejected. */
    val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    fun isValidBloodGroup(value: String?): Boolean =
        value.isNullOrBlank() || value.trim() in BLOOD_GROUPS

    /** User rule: exactly 10 digits, starting with 0 — e.g. 0550223366. */
    fun isValidPhone(value: String): Boolean =
        value.trim().matches(Regex("^0\\d{9}$"))

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
