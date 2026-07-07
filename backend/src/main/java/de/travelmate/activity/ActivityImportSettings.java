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

    @ConfigProperty(name = "travelmate.import.radius.default-m", defaultValue = "20000")
    int importRadiusDefaultMeters = 20000;

    @ConfigProperty(name = "travelmate.import.radius.nature-m", defaultValue = "30000")
    int importRadiusNatureMeters = 30000;

    @ConfigProperty(name = "travelmate.import.radius.large-city-default-m", defaultValue = "30000")
    int importRadiusLargeCityDefaultMeters = 30000;

    @ConfigProperty(name = "travelmate.import.radius.large-city-nature-m", defaultValue = "40000")
    int importRadiusLargeCityNatureMeters = 40000;

    @ConfigProperty(name = "travelmate.import.radius.long-trip-default-m", defaultValue = "30000")
    int importRadiusLongTripDefaultMeters = 30000;

    @ConfigProperty(name = "travelmate.import.radius.long-trip-nature-m", defaultValue = "40000")
    int importRadiusLongTripNatureMeters = 40000;

    @ConfigProperty(name = "travelmate.import.multi-area.enabled", defaultValue = "false")
    boolean multiAreaEnabled = false;

    @ConfigProperty(name = "travelmate.import.multi-area.long-trip-days", defaultValue = "5")
    int multiAreaLongTripDays = 5;

    @ConfigProperty(name = "travelmate.import.multi-area.max-areas", defaultValue = "6")
    int multiAreaMaxAreas = 6;

    @ConfigProperty(name = "travelmate.import.multi-area.min-raw-target-per-area", defaultValue = "12")
    int multiAreaMinRawTargetPerArea = 12;

    @ConfigProperty(name = "travelmate.import.multi-area.center-budget-share-short-trip", defaultValue = "0.65")
    double centerBudgetShareShortTrip = 0.65;

    @ConfigProperty(name = "travelmate.import.multi-area.center-budget-share-medium-trip", defaultValue = "0.50")
    double centerBudgetShareMediumTrip = 0.50;

    @ConfigProperty(name = "travelmate.import.multi-area.center-budget-share-long-trip", defaultValue = "0.40")
    double centerBudgetShareLongTrip = 0.40;

    @ConfigProperty(name = "travelmate.import.multi-area.center-budget-share-very-long-trip", defaultValue = "0.35")
    double centerBudgetShareVeryLongTrip = 0.35;

    @ConfigProperty(name = "travelmate.import.multi-area.area-radius-m", defaultValue = "6000")
    int multiAreaRadiusMeters = 6000;

    @ConfigProperty(name = "travelmate.import.multi-area.nature-area-radius-m", defaultValue = "8000")
    int multiAreaNatureRadiusMeters = 8000;

    @ConfigProperty(name = "travelmate.import.multi-area.large-city-ring-distance-km", defaultValue = "8")
    double largeCityRingDistanceKm = 8;

    @ConfigProperty(name = "travelmate.import.multi-area.metro-ring-distance-km", defaultValue = "12")
    double metroRingDistanceKm = 12;

    @ConfigProperty(name = "travelmate.import.multi-area.max-area-center-distance-km", defaultValue = "16")
    double maxAreaCenterDistanceKm = 16;

    @ConfigProperty(name = "travelmate.spatial.coverage.min-clusters-short-trip", defaultValue = "1")
    int minClustersShortTrip = 1;

    @ConfigProperty(name = "travelmate.spatial.coverage.min-clusters-medium-trip", defaultValue = "2")
    int minClustersMediumTrip = 2;

    @ConfigProperty(name = "travelmate.spatial.coverage.min-clusters-long-trip", defaultValue = "3")
    int minClustersLongTrip = 3;

    @ConfigProperty(name = "travelmate.spatial.coverage.min-clusters-very-long-trip", defaultValue = "4")
    int minClustersVeryLongTrip = 4;

    @ConfigProperty(name = "travelmate.spatial.coverage.max-dominant-short-trip", defaultValue = "0.70")
    double maxDominantShortTrip = 0.70;

    @ConfigProperty(name = "travelmate.spatial.coverage.max-dominant-medium-trip", defaultValue = "0.60")
    double maxDominantMediumTrip = 0.60;

    @ConfigProperty(name = "travelmate.spatial.coverage.max-dominant-long-trip", defaultValue = "0.55")
    double maxDominantLongTrip = 0.55;

    @ConfigProperty(name = "travelmate.spatial.coverage.max-dominant-very-long-trip", defaultValue = "0.50")
    double maxDominantVeryLongTrip = 0.50;

    @ConfigProperty(name = "travelmate.reachability.relaxed.max-area-distance-km", defaultValue = "10")
    double relaxedMaxAreaDistanceKm = 10;

    @ConfigProperty(name = "travelmate.reachability.balanced.max-area-distance-km", defaultValue = "14")
    double balancedMaxAreaDistanceKm = 14;

    @ConfigProperty(name = "travelmate.reachability.active.max-area-distance-km", defaultValue = "18")
    double activeMaxAreaDistanceKm = 18;

    @ConfigProperty(name = "travelmate.reachability.nature-extra-distance-km", defaultValue = "8")
    double natureExtraDistanceKm = 8;

    @ConfigProperty(name = "travelmate.reachability.exceptional-anchor-extra-distance-km", defaultValue = "6")
    double exceptionalAnchorExtraDistanceKm = 6;

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

    public int importRadiusDefaultMeters() {
        return Math.max(1000, importRadiusDefaultMeters);
    }

    public int importRadiusNatureMeters() {
        return Math.max(importRadiusDefaultMeters(), importRadiusNatureMeters);
    }

    public int importRadiusLargeCityDefaultMeters() {
        return Math.max(importRadiusDefaultMeters(), importRadiusLargeCityDefaultMeters);
    }

    public int importRadiusLargeCityNatureMeters() {
        return Math.max(importRadiusNatureMeters(), importRadiusLargeCityNatureMeters);
    }

    public int importRadiusLongTripDefaultMeters() {
        return Math.max(importRadiusDefaultMeters(), importRadiusLongTripDefaultMeters);
    }

    public int importRadiusLongTripNatureMeters() {
        return Math.max(importRadiusNatureMeters(), importRadiusLongTripNatureMeters);
    }

    public boolean multiAreaEnabled() {
        return multiAreaEnabled;
    }

    public int multiAreaLongTripDays() {
        return Math.max(2, multiAreaLongTripDays);
    }

    public int multiAreaMaxAreas() {
        return Math.max(1, multiAreaMaxAreas);
    }

    public int multiAreaMinRawTargetPerArea() {
        return Math.max(1, multiAreaMinRawTargetPerArea);
    }

    public double centerBudgetShareForTrip(int tripDays) {
        if (tripDays >= 11) return clampShare(centerBudgetShareVeryLongTrip);
        if (tripDays >= 7) return clampShare(centerBudgetShareLongTrip);
        if (tripDays >= 4) return clampShare(centerBudgetShareMediumTrip);
        return clampShare(centerBudgetShareShortTrip);
    }

    public int multiAreaRadiusMeters() {
        return Math.max(1000, multiAreaRadiusMeters);
    }

    public int multiAreaNatureRadiusMeters() {
        return Math.max(multiAreaRadiusMeters(), multiAreaNatureRadiusMeters);
    }

    public double largeCityRingDistanceKm() {
        return Math.max(1, largeCityRingDistanceKm);
    }

    public double metroRingDistanceKm() {
        return Math.max(largeCityRingDistanceKm(), metroRingDistanceKm);
    }

    public double maxAreaCenterDistanceKm() {
        return Math.max(1, maxAreaCenterDistanceKm);
    }

    public int minSpatialClustersForTrip(int tripDays) {
        if (tripDays >= 11) return Math.max(1, minClustersVeryLongTrip);
        if (tripDays >= 7) return Math.max(1, minClustersLongTrip);
        if (tripDays >= 4) return Math.max(1, minClustersMediumTrip);
        return Math.max(1, minClustersShortTrip);
    }

    public double maxDominantClusterShareForTrip(int tripDays) {
        if (tripDays >= 11) return clampShare(maxDominantVeryLongTrip);
        if (tripDays >= 7) return clampShare(maxDominantLongTrip);
        if (tripDays >= 4) return clampShare(maxDominantMediumTrip);
        return clampShare(maxDominantShortTrip);
    }

    public double maxAreaDistanceKm(de.travelmate.trip.TripPace pace) {
        return switch (pace == null ? de.travelmate.trip.TripPace.BALANCED : pace) {
            case RELAXED -> Math.max(1, relaxedMaxAreaDistanceKm);
            case BALANCED -> Math.max(1, balancedMaxAreaDistanceKm);
            case ACTIVE -> Math.max(1, activeMaxAreaDistanceKm);
        };
    }

    public double natureExtraDistanceKm() {
        return Math.max(0, natureExtraDistanceKm);
    }

    public double exceptionalAnchorExtraDistanceKm() {
        return Math.max(0, exceptionalAnchorExtraDistanceKm);
    }

    public int clampRawPerInterest(int value) {
        return Math.max(minRawPerInterest, Math.min(maxRawPerInterest, value));
    }

    private static double clampShare(double value) {
        return Math.max(0.05, Math.min(0.95, value));
    }
}
