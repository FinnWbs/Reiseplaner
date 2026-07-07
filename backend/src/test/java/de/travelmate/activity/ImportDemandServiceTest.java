package de.travelmate.activity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripPace;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImportDemandServiceTest {
    @Test
    void demandScalesWithTripLength() {
        ImportDemandService service = new ImportDemandService();
        service.settings = new ActivityImportSettings();
        Set<InterestType> interests = Set.of(InterestType.NATURE, InterestType.CULTURE, InterestType.FOOD);

        ImportDemand shortTrip = service.forTrip("Berlin", interests, 2, TripPace.BALANCED);
        ImportDemand longTrip = service.forTrip("Berlin", interests, 7, TripPace.BALANCED);
        ImportDemand singleDayTrip = service.forTrip("Berlin", interests, 1, TripPace.BALANCED);

        assertTrue(longTrip.rawPoolTargetTotal() > shortTrip.rawPoolTargetTotal());
        assertTrue(longTrip.rawTargetFor(InterestType.NATURE) >= service.settings.minRawPerInterest());
        assertTrue(longTrip.rawTargetFor(InterestType.NATURE) > 20);
        assertTrue(longTrip.minSpatialClusters() >= shortTrip.minSpatialClusters());
        assertTrue(longTrip.maxDominantClusterShare() <= shortTrip.maxDominantClusterShare());
        assertTrue(shortTrip.requireOuterCoverageForLongTrip());
        assertTrue(longTrip.requireOuterCoverageForLongTrip());
        assertFalse(singleDayTrip.requireOuterCoverageForLongTrip());
    }
}
