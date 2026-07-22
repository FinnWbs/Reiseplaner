package de.travelmate.datasource;

import java.util.List;
import java.util.Locale;

public final class NearbyShopDensityCalculator {
    private static final double SHOP_DENSITY_RADIUS_KM = 0.25;

    private NearbyShopDensityCalculator() {}

    public static void populate(List<ExternalActivityCandidate> candidates) {
        for (ExternalActivityCandidate candidate : candidates) {
            if (candidate.latitude == null || candidate.longitude == null) {
                continue;
            }
            int density = 0;
            for (ExternalActivityCandidate neighbor : candidates) {
                if (candidate == neighbor || neighbor.latitude == null || neighbor.longitude == null) {
                    continue;
                }
                if (isRetailLike(neighbor)
                    && GeoDistance.distanceKm(
                        candidate.latitude,
                        candidate.longitude,
                        neighbor.latitude,
                        neighbor.longitude
                    ) <= SHOP_DENSITY_RADIUS_KM) {
                    density++;
                }
            }
            candidate.nearbyShopDensity = density;
        }
    }

    private static boolean isRetailLike(ExternalActivityCandidate candidate) {
        return candidate.rawCategories.stream().anyMatch(category -> {
            String normalized = category.toLowerCase(Locale.ROOT);
            return normalized.equals("commercial")
                || normalized.startsWith("commercial.")
                || normalized.equals("shop")
                || normalized.startsWith("shop.");
        }) || candidate.rawTags.containsKey("shop")
            || "marketplace".equalsIgnoreCase(candidate.rawTags.get("amenity"));
    }
}
