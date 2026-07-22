package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.activity.ActivityImportSettings;
import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripPace;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReachabilityPolicyTest {
    @Test
    void areaAndPoiReachabilityAreCheckedSeparately() {
        ReachabilityPolicy policy = new ReachabilityPolicy();
        policy.settings = new ActivityImportSettings();
        CitySpatialContext context = new CitySpatialContext(
            "Berlin",
            52.52,
            13.405,
            null,
            12,
            true,
            true,
            7,
            TripPace.BALANCED,
            Set.of(InterestType.CULTURE)
        );
        ImportArea area = new ImportArea("east", "East", 52.52, 13.55, 3000, 0.2, 20, ImportAreaType.EAST, 9.8, true);
        ExternalActivityCandidate nearArea = candidate(52.52, 13.56);
        ExternalActivityCandidate outsideAreaRadius = candidate(52.52, 13.65);

        assertTrue(policy.isAreaReachableForTrip(area, context, InterestType.CULTURE));
        assertTrue(policy.isPoiReachableForDay(nearArea, area, context, false));
        assertFalse(policy.isPoiReachableForDay(outsideAreaRadius, area, context, false));
    }

    @Test
    void farNormalAreaIsNotReachableForBalancedTrip() {
        ReachabilityPolicy policy = new ReachabilityPolicy();
        policy.settings = new ActivityImportSettings();
        CitySpatialContext context = new CitySpatialContext(
            "Berlin",
            52.52,
            13.405,
            null,
            12,
            true,
            true,
            7,
            TripPace.BALANCED,
            Set.of(InterestType.FOOD)
        );
        ImportArea far = new ImportArea("far", "Far", 52.52, 13.75, 3000, 0.2, 20, ImportAreaType.OUTER_RING, 23, true);

        assertFalse(policy.isAreaReachableForTrip(far, context, InterestType.FOOD));
    }

    private static ExternalActivityCandidate candidate(double lat, double lon) {
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.name = "Candidate";
        candidate.latitude = lat;
        candidate.longitude = lon;
        candidate.primaryInterest = InterestType.CULTURE;
        return candidate;
    }
}
