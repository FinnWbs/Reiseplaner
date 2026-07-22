package de.travelmate.datasource;

import de.travelmate.activity.ActivityImportSettings;
import de.travelmate.activity.ImportDemand;
import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ImportRadiusResolver {
    @Inject
    ActivityImportSettings settings;

    public int resolveImportRadiusMeters(CitySpatialContext context, InterestType interest, ImportDemand demand) {
        boolean nature = interest == InterestType.NATURE;
        int tripDays = demand == null ? (context == null ? 1 : context.tripDays()) : demand.tripDays();
        if (context != null && (context.metroAreaCandidate() || context.largeCity())) {
            return nature ? settings().importRadiusLargeCityNatureMeters() : settings().importRadiusLargeCityDefaultMeters();
        }
        if (tripDays >= settings().multiAreaLongTripDays()) {
            return nature ? settings().importRadiusLongTripNatureMeters() : settings().importRadiusLongTripDefaultMeters();
        }
        return nature ? settings().importRadiusNatureMeters() : settings().importRadiusDefaultMeters();
    }

    private ActivityImportSettings settings() {
        return settings == null ? new ActivityImportSettings() : settings;
    }
}
