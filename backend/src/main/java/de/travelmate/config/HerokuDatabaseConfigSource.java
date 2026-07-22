package de.travelmate.config;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Converts Heroku's postgres:// DATABASE_URL into Quarkus JDBC settings. */
public final class HerokuDatabaseConfigSource implements ConfigSource {
    private final Map<String, String> properties;

    public HerokuDatabaseConfigSource() {
        this(System.getenv("DATABASE_URL"));
    }

    HerokuDatabaseConfigSource(String databaseUrl) {
        properties = databaseUrl == null || databaseUrl.isBlank()
                ? Collections.emptyMap()
                : parse(databaseUrl);
    }

    private static Map<String, String> parse(String databaseUrl) {
        URI uri = URI.create(databaseUrl);
        String rawUserInfo = uri.getRawUserInfo();
        if (uri.getHost() == null || rawUserInfo == null) {
            throw new IllegalArgumentException("DATABASE_URL is missing host or credentials");
        }
        String[] credentials = rawUserInfo.split(":", 2);
        if (credentials.length != 2) {
            throw new IllegalArgumentException("DATABASE_URL is missing a password");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        String query = uri.getRawQuery();
        String jdbcQuery = query == null || query.isBlank()
                ? "?sslmode=require"
                : "?" + query + (query.contains("sslmode=") ? "" : "&sslmode=require");

        Map<String, String> result = new HashMap<>();
        result.put("quarkus.datasource.jdbc.url",
                "jdbc:postgresql://" + uri.getHost() + port + path + jdbcQuery);
        result.put("quarkus.datasource.username", decode(credentials[0]));
        result.put("quarkus.datasource.password", decode(credentials[1]));
        return Map.copyOf(result);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override public Map<String, String> getProperties() { return properties; }
    @Override public Set<String> getPropertyNames() { return properties.keySet(); }
    @Override public String getValue(String propertyName) { return properties.get(propertyName); }
    @Override public String getName() { return "Heroku DATABASE_URL"; }
    @Override public int getOrdinal() { return 275; }
}
