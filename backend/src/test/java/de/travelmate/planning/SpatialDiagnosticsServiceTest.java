package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ImportDemand;
import de.travelmate.activity.ActivityPersistenceService;
import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripPace;
import de.travelmate.trip.TripDayActivityEntity;
import de.travelmate.trip.TripDayEntity;
import de.travelmate.trip.TripEntity;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpatialDiagnosticsServiceTest {
    @Test
    void detectsCentralSpatialMonotony() {
        SpatialDiagnosticsService service = new SpatialDiagnosticsService();
        TripEntity trip = trip("Berlin", 52.5200, 13.4050, 6);
        long id = 1;
        for (int index = 0; index < 15; index++) {
            addActivity(trip, id++, "Center " + index, 52.5200 + index * 0.0006, 13.4050 + index * 0.0006);
        }
        addActivity(trip, id++, "Outer Park", 52.4700, 13.3000);
        addActivity(trip, id++, "Outer Museum", 52.5600, 13.2500);
        addActivity(trip, id, "Outer Market", 52.5000, 13.6200);

        SpatialDiagnostics diagnostics = service.analyze(trip);

        assertTrue(diagnostics.dominantClusterShare() > 0.55);
        assertTrue(diagnostics.hasWarning(SpatialWarningCode.TOO_MANY_ACTIVITIES_IN_ONE_CLUSTER));
        assertTrue(diagnostics.hasWarning(SpatialWarningCode.SPATIAL_DIVERSITY_LOW)
            || diagnostics.hasWarning(SpatialWarningCode.DOMINANT_CENTER_CLUSTER));
    }

    @Test
    void acceptsGoodClusterDistribution() {
        SpatialDiagnosticsService service = new SpatialDiagnosticsService();
        TripEntity trip = trip("Berlin", 52.5200, 13.4050, 6);
        long id = 1;
        double[][] clusters = {
            {52.5200, 13.4050},
            {52.4700, 13.3000},
            {52.5600, 13.2500},
            {52.5000, 13.6200}
        };
        for (int cluster = 0; cluster < clusters.length; cluster++) {
            for (int item = 0; item < 4; item++) {
                addActivity(
                    trip,
                    id++,
                    "Cluster " + cluster + " POI " + item,
                    clusters[cluster][0] + item * 0.001,
                    clusters[cluster][1] + item * 0.001
                );
            }
        }

        SpatialDiagnostics diagnostics = service.analyze(trip);

        assertTrue(diagnostics.uniqueSpatialClusters() >= 3);
        assertTrue(diagnostics.dominantClusterShare() <= 0.55);
        assertFalse(diagnostics.hasWarning(SpatialWarningCode.SPATIAL_DIVERSITY_LOW));
    }

    @Test
    void recommendsMultiAreaImportOnlyAsDiagnosticWhenLongTripHasTooFewClusters() {
        SpatialDiagnosticsService service = new SpatialDiagnosticsService();
        TripEntity trip = trip("Shanghai", 31.2304, 121.4737, 7);
        long id = 1;
        for (int index = 0; index < 12; index++) {
            addActivity(trip, id++, "Center " + index, 31.2304 + index * 0.0004, 121.4737 + index * 0.0004);
        }
        for (int index = 0; index < 9; index++) {
            addActivity(trip, id++, "Pudong " + index, 31.2397 + index * 0.0004, 121.4998 + index * 0.0004);
        }

        SpatialDiagnostics diagnostics = service.analyze(trip);

        assertTrue(diagnostics.uniqueSpatialClusters() < 3);
        assertTrue(diagnostics.hasWarning(SpatialWarningCode.MULTI_AREA_IMPORT_RECOMMENDED));
    }

    @Test
    void spatialCoverageDetectsCenterOnlyPoolDespiteEnoughCandidates() {
        SpatialCoverageService service = new SpatialCoverageService();
        ImportDemand demand = new ImportDemand(
            "Berlin",
            Set.of(InterestType.CULTURE),
            7,
            TripPace.BALANCED,
            3,
            21,
            53,
            120,
            Map.of(InterestType.CULTURE, 120),
            Map.of(InterestType.CULTURE, 18),
            3,
            0.55,
            4,
            true,
            false
        );
        java.util.List<ActivityEntity> activities = java.util.stream.IntStream.range(0, 20)
            .mapToObj(index -> activity(100 + index, "Center " + index, 52.5200 + index * 0.0003, 13.4050))
            .toList();

        SpatialCoverageReport report = service.analyze(
            "Berlin",
            InterestType.CULTURE,
            activities,
            demand,
            52.5200,
            13.4050
        );

        assertTrue(report.insufficient());
        assertTrue(report.warnings().contains(SpatialCoverageWarningCode.MULTI_AREA_IMPORT_RECOMMENDED));
    }

    static TripEntity trip(String city, double lat, double lon, int days) {
        TripEntity trip = new TripEntity();
        trip.city = city;
        trip.latitude = lat;
        trip.longitude = lon;
        trip.daysCount = days;
        for (int dayNumber = 1; dayNumber <= days; dayNumber++) {
            TripDayEntity day = new TripDayEntity();
            day.id = (long) dayNumber;
            day.dayNumber = dayNumber;
            day.trip = trip;
            trip.days.add(day);
        }
        return trip;
    }

    static ActivityEntity activity(long id, String name, double lat, double lon) {
        ActivityEntity activity = new ActivityEntity();
        activity.id = id;
        activity.name = name;
        activity.city = "Fixture";
        activity.latitude = lat;
        activity.longitude = lon;
        activity.importVersion = ActivityPersistenceService.CURRENT_IMPORT_VERSION;
        activity.primaryInterest = InterestType.CULTURE;
        activity.finalScore = 0.8;
        activity.categoryFitScore = 1;
        activity.itineraryFitScore = 1;
        activity.dataQualityScore = 1;
        return activity;
    }

    static void addActivity(TripEntity trip, long id, String name, double lat, double lon) {
        int index = (int) ((id - 1) % trip.days.size());
        TripDayEntity day = trip.days.get(index);
        TripDayActivityEntity item = new TripDayActivityEntity();
        item.id = id * 10;
        item.tripDay = day;
        item.position = day.activities.size() + 1;
        item.activity = activity(id, name, lat, lon);
        day.activities.add(item);
    }
}
