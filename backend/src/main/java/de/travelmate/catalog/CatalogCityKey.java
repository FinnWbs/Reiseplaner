package de.travelmate.catalog;

import de.travelmate.activity.ActivityImportService;
import java.text.Normalizer;
import java.util.Locale;

final class CatalogCityKey {
    private CatalogCityKey() {}

    static String from(String city) {
        String normalizedCity = ActivityImportService.normalizeCity(city);
        String ascii = Normalizer.normalize(normalizedCity, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
        return ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    static String countryCode(String countryCode) {
        return countryCode == null || countryCode.isBlank()
            ? ""
            : countryCode.trim().toUpperCase(Locale.ROOT);
    }
}
