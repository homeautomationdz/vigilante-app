package com.vigilante.app.security

import java.security.SecureRandom

/**
 * The way back in when the only Super Admin forgets their password.
 *
 * An offline app has no "email me a reset link", so a code is generated once at
 * setup, shown to the owner to write down, and stored only as a BCrypt hash —
 * exactly like a password. Losing both the password and this code means the
 * app can no longer be opened, which is why the setup screen insists it be
 * saved somewhere safe.
 *
 * Format: 4 groups of 5 characters, e.g. K7M2Q-XR4TF-9PLNB-3HWDS.
 * The alphabet drops I, O, 0 and 1 so a handwritten code cannot be misread.
 */
object RecoveryCode {

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val GROUPS = 4
    private const val GROUP_SIZE = 5

    fun generate(): String {
        val random = SecureRandom()
        return (0 until GROUPS).joinToString("-") {
            buildString {
                repeat(GROUP_SIZE) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
            }
        }
    }

    /** Accepts the code however the user types it: spaces, dashes, lower case. */
    fun normalize(input: String): String =
        input.uppercase()
            .filter { it in ALPHABET }

    fun hash(code: String): String = PasswordHasher.hash(normalize(code))

    fun verify(input: String, hash: String): Boolean {
        val normalized = normalize(input)
        if (normalized.length != GROUPS * GROUP_SIZE) return false
        return PasswordHasher.verify(normalized, hash)
    }
}
