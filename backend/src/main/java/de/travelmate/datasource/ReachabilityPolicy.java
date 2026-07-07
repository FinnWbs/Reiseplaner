package de.travelmate.datasource;

import de.travelmate.activity.ActivityImportSettings;
import de.travelmate.interest.InterestType;
import de.travelmate.quality.CanonicalCategory;
import de.travelmate.trip.TripPace;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ReachabilityPolicy {
    @Inject
    ActivityImportSettings settings;

    public boolean isAreaReachableForTrip(ImportArea area, CitySpatialContext context, InterestType interest) {
        double limit = maxDistance(context == null ? TripPace.BALANCED : context.pace(), interest, false);
        return area.distanceFromCityCenterKm() <= limit;
    }

    public boolean isPoiReachableForDay(
        ExternalActivityCandidate candidate,
        ImportArea area,
        CitySpatialContext context,
        boolean exceptionalAnchor
    ) {
        if (candidate == null || candidate.latitude == null || candidate.longitude == null) {
            return false;
        }
        double distanceFromCenter = GeoDistance.distanceKm(
            context.centerLat(),
            context.centerLon(),
            candidate.latitude,
            candidate.longitude
        );
        InterestType interest = candidate.primaryInterest;
        double limit = maxDistance(context.pace(), interest, exceptionalAnchor);
        double distanceFromAreaCenter = GeoDistance.distanceKm(
            area.centerLat(),
            area.centerLon(),
            candidate.latitude,
            candidate.longitude
        );
        return distanceFromCenter <= limit && distanceFromAreaCenter <= area.radiusMeters() / 1000.0;
    }

    private double maxDistance(TripPace pace, InterestType interest, boolean exceptionalAnchor) {
        double limit = settings().maxAreaDistanceKm(pace);
        if (interest == InterestType.NATURE) {
            limit += settings().natureExtraDistanceKm();
        }
        if (exceptionalAnchor) {
            limit += settings().exceptionalAnchorExtraDistanceKm();
        }
        return limit;
    }

    private ActivityImportSettings settings() {
        return settings == null ? new ActivityImportSettings() : settings;
    }
}
