package com.vigilante.app.security

import at.favre.lib.crypto.bcrypt.BCrypt

/** Passwords are stored only as BCrypt hashes — never plain text (NFR-003). */
object PasswordHasher {
    private const val COST = 12

    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(COST, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}
