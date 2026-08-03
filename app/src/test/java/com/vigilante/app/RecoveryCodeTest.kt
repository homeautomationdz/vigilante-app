package com.vigilante.app

import com.vigilante.app.security.RecoveryCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeTest {

    @Test
    fun generatedCodeHasTheAdvertisedShape() {
        val code = RecoveryCode.generate()
        val groups = code.split("-")
        assertEquals(4, groups.size)
        groups.forEach { assertEquals(5, it.length) }
        // Ambiguous glyphs must never appear on a handwritten code.
        assertFalse(code.any { it in "IO01" })
    }

    @Test
    fun codesAreNotRepeated() {
        val codes = (1..50).map { RecoveryCode.generate() }.toSet()
        assertEquals(50, codes.size)
    }

    @Test
    fun verifyAcceptsAnyReasonableTyping() {
        val code = RecoveryCode.generate()
        val hash = RecoveryCode.hash(code)

        assertTrue(RecoveryCode.verify(code, hash))
        assertTrue(RecoveryCode.verify(code.lowercase(), hash))
        assertTrue(RecoveryCode.verify(code.replace("-", ""), hash))
        assertTrue(RecoveryCode.verify(code.replace("-", " "), hash))
        assertTrue(RecoveryCode.verify("  $code  ", hash))
    }

    @Test
    fun verifyRejectsWrongOrTruncatedCodes() {
        val hash = RecoveryCode.hash(RecoveryCode.generate())
        assertFalse(RecoveryCode.verify(RecoveryCode.generate(), hash))
        assertFalse(RecoveryCode.verify("", hash))
        assertFalse(RecoveryCode.verify("ABC", hash))
    }

    @Test
    fun hashIsNotTheCodeItself() {
        val code = RecoveryCode.generate()
        val hash = RecoveryCode.hash(code)
        assertNotEquals(code, hash)
        assertFalse(hash.contains(RecoveryCode.normalize(code)))
    }
}
