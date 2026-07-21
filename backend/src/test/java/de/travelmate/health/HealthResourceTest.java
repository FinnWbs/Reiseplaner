package de.travelmate.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthResourceTest {
    @Test
    void reportsHealthyStatus() {
        assertEquals("ok", new HealthResource().health().get("status"));
    }
}
