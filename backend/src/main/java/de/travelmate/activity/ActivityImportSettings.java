package de.travelmate.activity;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ActivityImportSettings {
    @ConfigProperty(name = "travelmate.import-demand.min-raw-per-interest", defaultValue = "50")
    int minRawPerInterest = 50;

    @ConfigProperty(name = "travelmate.import-demand.max-raw-per-interest", defaultValue = "180")
    int maxRawPerInterest = 180;

    @ConfigProperty(name = "travelmate.import-demand.max-raw-total-per-trip", defaultValue = "500")
    int maxRawTotalPerTrip = 500;

    @ConfigProperty(name = "travelmate.import-demand.expected-yield", defaultValue = "0.35")
    double expectedYield = 0.35;

    @ConfigProperty(name = "travelmate.import-demand.eligible-pool-multiplier", defaultValue = "2.5")
    double eligiblePoolMultiplier = 2.5;

    @ConfigProperty(name = "travelmate.import-demand.raw-candidates-per-needed-slot", defaultValue = "7")
    int rawCandidatesPerNeededSlot = 7;

    @ConfigProperty(name = "travelmate.geoapify.page-size", defaultValue = "100")
    int geoapifyPageSize = 100;

    @ConfigProperty(name = "travelmate.geoapify.max-pages-per-interest", defaultValue = "2")
    int maxPagesPerInterest = 2;

    public int minRawPerInterest() {
        return minRawPerInterest;
    }

    public int maxRawPerInterest() {
        return maxRawPerInterest;
    }

    public int maxRawTotalPerTrip() {
        return maxRawTotalPerTrip;
    }

    public double expectedYield() {
        return expectedYield;
    }

    public double eligiblePoolMultiplier() {
        return eligiblePoolMultiplier;
    }

    public int rawCandidatesPerNeededSlot() {
        return rawCandidatesPerNeededSlot;
    }

    public int geoapifyPageSize() {
        return Math.max(1, geoapifyPageSize);
    }

    public int maxPagesPerInterest() {
        return Math.max(1, maxPagesPerInterest);
    }

    public int clampRawPerInterest(int value) {
        return Math.max(minRawPerInterest, Math.min(maxRawPerInterest, value));
    }
}
