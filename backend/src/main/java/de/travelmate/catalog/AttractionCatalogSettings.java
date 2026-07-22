package de.travelmate.catalog;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AttractionCatalogSettings {
    public static final int SOURCE_VERSION = 4;

    @ConfigProperty(name = "travelmate.catalog.wikimedia.enabled", defaultValue = "true")
    boolean wikimediaEnabled;

    @ConfigProperty(name = "travelmate.catalog.cache-ttl-days", defaultValue = "30")
    int cacheTtlDays;

    @ConfigProperty(name = "travelmate.catalog.max-items", defaultValue = "15")
    int maxItems;

    @ConfigProperty(name = "travelmate.catalog.candidate-limit", defaultValue = "60")
    int candidateLimit;

    @ConfigProperty(name = "travelmate.catalog.max-search-radius-km", defaultValue = "25")
    double maxSearchRadiusKm;

    @ConfigProperty(name = "travelmate.catalog.city-boundary-buffer-km", defaultValue = "1.5")
    double cityBoundaryBufferKm;

    @ConfigProperty(name = "travelmate.catalog.enclave-extra-buffer-km", defaultValue = "3.0")
    double enclaveExtraBufferKm;

    @ConfigProperty(name = "travelmate.catalog.fallback-boundary-radius-km", defaultValue = "12")
    double fallbackBoundaryRadiusKm;

    @ConfigProperty(name = "travelmate.catalog.pageview-months", defaultValue = "12")
    int pageviewMonths;

    @ConfigProperty(name = "travelmate.catalog.pageview-candidate-limit", defaultValue = "20")
    int pageviewCandidateLimit;

    @ConfigProperty(name = "travelmate.catalog.pageview-parallelism", defaultValue = "4")
    int pageviewParallelism;

    @ConfigProperty(name = "travelmate.catalog.user-agent", defaultValue = "TravelMate/1.0 (travelmate-local@example.invalid)")
    String userAgent;

    public boolean wikimediaEnabled() {
        return wikimediaEnabled;
    }

    public int cacheTtlDays() {
        return cacheTtlDays;
    }

    public int maxItems() {
        return maxItems;
    }

    public int candidateLimit() {
        return candidateLimit;
    }

    public double maxSearchRadiusKm() {
        return maxSearchRadiusKm;
    }

    public double cityBoundaryBufferKm() {
        return cityBoundaryBufferKm;
    }

    public double enclaveExtraBufferKm() {
        return enclaveExtraBufferKm;
    }

    public double fallbackBoundaryRadiusKm() {
        return fallbackBoundaryRadiusKm;
    }

    public int pageviewMonths() {
        return pageviewMonths;
    }

    public int pageviewCandidateLimit() {
        return pageviewCandidateLimit;
    }

    public int pageviewParallelism() {
        return pageviewParallelism;
    }

    public String userAgent() {
        return userAgent;
    }
}
