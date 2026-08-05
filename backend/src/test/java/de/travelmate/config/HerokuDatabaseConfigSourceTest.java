package de.travelmate.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HerokuDatabaseConfigSourceTest {
    @Test
    void mapsHerokuDatabaseUrlToQuarkusJdbcProperties() {
        HerokuDatabaseConfigSource source = new HerokuDatabaseConfigSource(
                "postgres://travel%40mate:p%40ss%3Aword@db.example.com:5432/travelmate");

        assertEquals("jdbc:postgresql://db.example.com:5432/travelmate?sslmode=require",
                source.getValue("quarkus.datasource.jdbc.url"));
        assertEquals("travel@mate", source.getValue("quarkus.datasource.username"));
        assertEquals("p@ss:word", source.getValue("quarkus.datasource.password"));
    }

    @Test
    void leavesLocalConfigurationUntouchedWithoutDatabaseUrl() {
        HerokuDatabaseConfigSource source = new HerokuDatabaseConfigSource(null);

        assertNull(source.getValue("quarkus.datasource.jdbc.url"));
        assertEquals(0, source.getProperties().size());
    }
}
