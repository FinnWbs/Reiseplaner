package de.travelmate.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

class RegistrationValidatorTest {
    private final RegistrationValidator validator = new RegistrationValidator();

    @Test
    void trimsAnExplicitDisplayName() {
        assertEquals("Finn", validator.normalizeDisplayName("  Finn  ", "secret123"));
    }

    @Test
    void rejectsMissingOrTooShortDisplayNames() {
        assertThrows(BadRequestException.class, () -> validator.normalizeDisplayName(" ", "secret123"));
        assertThrows(BadRequestException.class, () -> validator.normalizeDisplayName("F", "secret123"));
    }

    @Test
    void rejectsPasswordAsDisplayName() {
        assertThrows(BadRequestException.class, () -> validator.normalizeDisplayName("secret123", "secret123"));
    }
}
