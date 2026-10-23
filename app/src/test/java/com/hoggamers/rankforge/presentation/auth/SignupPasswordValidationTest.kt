package com.hoggamers.rankforge.presentation.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupPasswordValidationTest {
    @Test
    fun emptyPasswordFailsEveryRequirement() {
        val result = validateSignupPassword("")

        assertFalse(result.hasMinimumLength)
        assertFalse(result.hasUppercase)
        assertFalse(result.hasLowercase)
        assertFalse(result.hasDigit)
        assertFalse(result.isValid)
    }

    @Test
    fun exactMinimumWithUppercaseLowercaseAndDigitPasses() {
        val result = validateSignupPassword("Abcdefghi1")

        assertTrue(result.hasMinimumLength)
        assertTrue(result.hasUppercase)
        assertTrue(result.hasLowercase)
        assertTrue(result.hasDigit)
        assertTrue(result.isValid)
    }

    @Test
    fun eachRequirementIsIndependent() {
        assertFalse(validateSignupPassword("Abcdefgh1").hasMinimumLength)
        assertFalse(validateSignupPassword("abcdefghij1").hasUppercase)
        assertFalse(validateSignupPassword("ABCDEFGHIJ1").hasLowercase)
        assertFalse(validateSignupPassword("Abcdefghij").hasDigit)
    }

    @Test
    fun symbolIsNotRequired() {
        assertTrue(validateSignupPassword("Abcdefghi1").isValid)
    }
}
