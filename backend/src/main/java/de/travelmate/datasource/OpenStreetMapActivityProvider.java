package de.travelmate.datasource;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OpenStreetMapActivityProvider implements ActivityProvider {
    @ConfigProperty(name = "travelmate.providers.osm.enabled", defaultValue = "false")
    boolean enabled;

    @Override
    public List<ExternalActivityCandidate> fetch(String city) {
        // Der Provider ist bewusst vorbereitet, aber ohne konfigurierten OSM-Endpunkt deaktiviert.
        return List.of();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
