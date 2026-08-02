package com.vigilante.app

import com.vigilante.app.core.Validation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun phoneMustBeTenDigitsStartingWithZero() {
        assertTrue(Validation.isValidPhone("0550223366"))
        assertFalse(Validation.isValidPhone("550223366"))     // no leading 0
        assertFalse(Validation.isValidPhone("05502233667"))   // 11 digits
        assertFalse(Validation.isValidPhone("055022336"))     // 9 digits
        assertFalse(Validation.isValidPhone("05502A3366"))    // letter
    }

    @Test
    fun usernameAllowsFullArabicNamesWithSpaces() {
        assertTrue(Validation.isValidUsername("محمد أحمد"))
        assertTrue(Validation.isValidUsername("Mohamed Ali"))
        assertFalse(Validation.isValidUsername("مح"))          // too short
    }

    @Test
    fun bloodGroupsAreClosedList() {
        assertTrue(Validation.isValidBloodGroup("O+"))
        assertTrue(Validation.isValidBloodGroup(null))
        assertFalse(Validation.isValidBloodGroup("o positive"))
    }
}
