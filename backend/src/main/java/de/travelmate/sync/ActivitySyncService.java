package de.travelmate.sync;

import de.travelmate.activity.ActivityDto;
import de.travelmate.activity.ActivityImportService;
import de.travelmate.activity.ActivityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ActivitySyncService {
    private static final int FRESH_DAYS = 7;
    private static final int MINIMUM_CITY_ACTIVITIES = 8;

    @Inject
    ActivityRepository activities;

    @Inject
    ActivityImportService importer;

    public boolean cityNeedsRefresh(String city) {
        return !activities.hasFreshMinimumForCity(
            city,
            MINIMUM_CITY_ACTIVITIES,
            LocalDateTime.now().minusDays(FRESH_DAYS)
        );
    }

    public List<ActivityDto> syncCity(String city) {
        return importer.importCity(city).activities();
    }

    public List<ActivityDto> syncCity(String city, String lookupText) {
        return importer.importCity(city, lookupText).activities();
    }

    public List<ActivityDto> syncCity(
        String city,
        String lookupText,
        String placeId,
        Double latitude,
        Double longitude
    ) {
        return importer.importCity(city, lookupText, placeId, latitude, longitude).activities();
    }
}
