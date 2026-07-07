package de.travelmate.datasource;

import de.travelmate.activity.ImportDemand;
import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripPace;
import java.util.Set;

public record CitySpatialContext(
    String cityName,
    double centerLat,
    double centerLon,
    CityBoundingBox bbox,
    double approximateCityRadiusKm,
    boolean largeCity,
    boolean metroAreaCandidate,
    int tripDays,
    TripPace pace,
    Set<InterestType> selectedInterests
) {
    public static CitySpatialContext from(
        String cityName,
        double centerLat,
        double centerLon,
        CityBoundingBox bbox,
        ImportDemand demand
    ) {
        int days = demand == null ? 1 : Math.max(1, demand.tripDays());
        Set<InterestType> interests = demand == null ? InterestType.primaryTypes() : demand.selectedInterests();
        double approximateRadius = bbox == null ? approximateRadiusFor(days) : radiusFromBbox(centerLat, centerLon, bbox);
        boolean large = approximateRadius >= 10 || days >= 5;
        boolean metro = approximateRadius >= 14 || days >= 7;
        return new CitySpatialContext(
            cityName,
            centerLat,
            centerLon,
            bbox,
            approximateRadius,
            large,
            metro,
            days,
            demand == null ? TripPace.BALANCED : demand.pace(),
            interests
        );
    }

    private static double approximateRadiusFor(int tripDays) {
        if (tripDays >= 11) return 14;
        if (tripDays >= 7) return 12;
        if (tripDays >= 5) return 10;
        return 7;
    }

    private static double radiusFromBbox(double centerLat, double centerLon, CityBoundingBox bbox) {
        double first = GeoDistance.distanceKm(centerLat, centerLon, bbox.minLat(), bbox.minLon());
        double second = GeoDistance.distanceKm(centerLat, centerLon, bbox.minLat(), bbox.maxLon());
        double third = GeoDistance.distanceKm(centerLat, centerLon, bbox.maxLat(), bbox.minLon());
        double fourth = GeoDistance.distanceKm(centerLat, centerLon, bbox.maxLat(), bbox.maxLon());
        return Math.max(Math.max(first, second), Math.max(third, fourth));
    }
}
