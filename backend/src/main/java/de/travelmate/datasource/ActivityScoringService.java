package de.travelmate.datasource;

import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
public class ActivityScoringService {
    public double score(
        ExternalActivityCandidate candidate,
        Set<InterestType> requestedInterests,
        double originLatitude,
        double originLongitude
    ) {
        double score = 20;
        if (candidate.primaryInterest != null && requestedInterests.contains(candidate.primaryInterest)) {
            score += 60;
        }
        if (candidate.website != null && !candidate.website.isBlank()) score += 8;
        if (candidate.openingHours != null && !candidate.openingHours.isBlank()) score += 6;
        if (candidate.hasWikidata) score += 6;
        if (candidate.address != null && !candidate.address.isBlank()) {
            score += 5;
        }
        if (candidate.primaryInterest == InterestType.NATURE) {
            score += natureQualityBonus(candidate);
        }
        double distance = distanceInKilometers(originLatitude, originLongitude, candidate.latitude, candidate.longitude);
        return score + Math.max(0, 20 - distance);
    }

    private static int natureQualityBonus(ExternalActivityCandidate candidate) {
        if (candidate.rawCategories.contains("national_park")) return 16;
        if (candidate.rawCategories.contains("leisure.park.nature_reserve")) return 14;
        if (candidate.rawCategories.contains("leisure.park.garden")) return 12;
        if (candidate.rawCategories.contains("leisure.park")) return 8;
        return 0;
    }

    private static double distanceInKilometers(double firstLatitude, double firstLongitude, double secondLatitude, double secondLongitude) {
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
            + Math.cos(Math.toRadians(firstLatitude)) * Math.cos(Math.toRadians(secondLatitude))
            * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
