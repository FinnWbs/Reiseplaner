package de.travelmate.catalog;

import de.travelmate.datasource.CityBoundingBox;
import de.travelmate.datasource.GeoDistance;

record CityBoundary(
    String cityName,
    String wikidataId,
    double centerLat,
    double centerLon,
    CityBoundingBox boundingBox,
    double effectiveRadiusKm,
    double bufferKm,
    double enclaveExtraBufferKm,
    boolean geoapifyBoundary
) {
    static CityBoundary fromBoundingBox(
        String cityName,
        String wikidataId,
        double centerLat,
        double centerLon,
        CityBoundingBox boundingBox,
        double bufferKm,
        double enclaveExtraBufferKm
    ) {
        return new CityBoundary(
            cityName,
            wikidataId,
            centerLat,
            centerLon,
            boundingBox,
            Math.max(2.0, radiusFromBoundingBox(centerLat, centerLon, boundingBox)),
            bufferKm,
            enclaveExtraBufferKm,
            true
        );
    }

    static CityBoundary fallback(
        String cityName,
        String wikidataId,
        double centerLat,
        double centerLon,
        double fallbackRadiusKm,
        double bufferKm,
        double enclaveExtraBufferKm
    ) {
        return new CityBoundary(
            cityName,
            wikidataId,
            centerLat,
            centerLon,
            null,
            Math.max(2.0, fallbackRadiusKm),
            bufferKm,
            enclaveExtraBufferKm,
            false
        );
    }

    double queryRadiusKm(double maxRadiusKm) {
        double dynamicRadius = effectiveRadiusKm + bufferKm + enclaveExtraBufferKm;
        return Math.min(Math.max(dynamicRadius, 3.0), Math.max(3.0, maxRadiusKm));
    }

    boolean acceptsCityMember(double latitude, double longitude) {
        return withinExpandedEnvelope(latitude, longitude, bufferKm);
    }

    boolean acceptsNearbyEnclave(double latitude, double longitude, int sitelinks, double categoryFitScore) {
        boolean strongTouristSignal = sitelinks >= 35 || categoryFitScore >= 0.90;
        if (!strongTouristSignal || !withinExpandedEnvelope(latitude, longitude, bufferKm + enclaveExtraBufferKm)) {
            return false;
        }
        double innerEnclaveRadiusKm = Math.max(2.0, Math.min(effectiveRadiusKm * 0.55, bufferKm + enclaveExtraBufferKm));
        return distanceKm(latitude, longitude) <= innerEnclaveRadiusKm;
    }

    boolean rejectsHard(double latitude, double longitude) {
        return !withinExpandedEnvelope(latitude, longitude, bufferKm + enclaveExtraBufferKm);
    }

    double distanceKm(double latitude, double longitude) {
        return GeoDistance.distanceKm(centerLat, centerLon, latitude, longitude);
    }

    private boolean withinExpandedEnvelope(double latitude, double longitude, double extraKm) {
        if (boundingBox != null && !expandedContains(boundingBox, latitude, longitude, extraKm)) {
            return false;
        }
        return distanceKm(latitude, longitude) <= effectiveRadiusKm + extraKm;
    }

    private static boolean expandedContains(CityBoundingBox box, double latitude, double longitude, double extraKm) {
        double latPadding = extraKm / 111.32;
        double cos = Math.max(0.2, Math.cos(Math.toRadians((box.minLat() + box.maxLat()) / 2.0)));
        double lonPadding = extraKm / (111.32 * cos);
        return latitude >= box.minLat() - latPadding
            && latitude <= box.maxLat() + latPadding
            && longitude >= box.minLon() - lonPadding
            && longitude <= box.maxLon() + lonPadding;
    }

    private static double radiusFromBoundingBox(double centerLat, double centerLon, CityBoundingBox box) {
        double first = GeoDistance.distanceKm(centerLat, centerLon, box.minLat(), box.minLon());
        double second = GeoDistance.distanceKm(centerLat, centerLon, box.minLat(), box.maxLon());
        double third = GeoDistance.distanceKm(centerLat, centerLon, box.maxLat(), box.minLon());
        double fourth = GeoDistance.distanceKm(centerLat, centerLon, box.maxLat(), box.maxLon());
        return Math.max(Math.max(first, second), Math.max(third, fourth));
    }
}
