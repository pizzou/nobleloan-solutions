package com.patrick.fintech.loan_backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void acceptsPasswordThatMeetsAllRules() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("StrongPassword1!"));
    }

    @Test
    void rejectsPasswordShorterThanMinimum() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordPolicy.validate("Short1!"));
    }

    @Test
    void rejectsPasswordWithoutUppercase() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordPolicy.validate("strongpassword1!"));
    }

    @Test
    void rejectsPasswordWithoutLowercase() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordPolicy.validate("STRONGPASSWORD1!"));
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordPolicy.validate("StrongPassword!!"));
    }

    @Test
    void rejectsPasswordWithoutSpecialCharacter() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordPolicy.validate("StrongPassword123"));
    }

    @Test
    void rejectsPasswordLongerThanDtoMaximum() {
        String tooLong = "A" + "a".repeat(126) + "1!";
        assertThrows(IllegalArgumentException.class,
                () -> PasswordPolicy.validate(tooLong));
    }
}
