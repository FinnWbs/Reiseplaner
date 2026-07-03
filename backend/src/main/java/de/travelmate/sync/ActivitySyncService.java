package de.travelmate.sync;

import de.travelmate.activity.ActivityDto;
import de.travelmate.activity.ActivityImportService;
import de.travelmate.activity.ImportDemand;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.activity.ActivityPersistenceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import de.travelmate.interest.InterestType;

@ApplicationScoped
public class ActivitySyncService {
    private static final int FRESH_DAYS = 7;
    private static final int MINIMUM_CITY_ACTIVITIES = 8;

    @Inject
    ActivityRepository activities;

    @Inject
    ActivityImportService importer;

    @Inject
    ActivityImportStateRepository states;

    public boolean cityNeedsRefresh(String city) {
        return InterestType.primaryTypes().stream().anyMatch(interest -> needsRefresh(city, interest));
    }

    public List<ActivityDto> syncCity(String city) {
        return syncCity(city, city, null, null, null, InterestType.primaryTypes());
    }

    public List<ActivityDto> syncCity(String city, String lookupText) {
        return syncCity(city, lookupText, null, null, null, InterestType.primaryTypes());
    }

    @Transactional
    public List<ActivityDto> syncCity(
        String city,
        String lookupText,
        String placeId,
        Double latitude,
        Double longitude,
        Set<InterestType> interests
    ) {
        return syncCity(city, lookupText, placeId, latitude, longitude, interests, null);
    }

    @Transactional
    public List<ActivityDto> syncCity(
        String city,
        String lookupText,
        String placeId,
        Double latitude,
        Double longitude,
        Set<InterestType> interests,
        ImportDemand demand
    ) {
        Set<InterestType> selected = interests == null || interests.isEmpty() ? InterestType.primaryTypes() : interests;
        List<ActivityDto> latest = activities.findActiveByCity(city).stream().map(ActivityDto::from).toList();
        for (InterestType interest : selected) {
            if (!needsRefresh(city, interest, requiredEligibleCount(interest, demand))) {
                continue;
            }
            latest = importer.importInterest(city, lookupText, placeId, latitude, longitude, interest, demand).activities();
            recordSync(city, interest);
        }
        return latest;
    }

    public boolean needsRefresh(String city, Set<InterestType> interests) {
        Set<InterestType> selected = interests == null || interests.isEmpty() ? InterestType.primaryTypes() : interests;
        return selected.stream().anyMatch(interest -> needsRefresh(city, interest));
    }

    public boolean needsRefresh(String city, Set<InterestType> interests, ImportDemand demand) {
        Set<InterestType> selected = interests == null || interests.isEmpty() ? InterestType.primaryTypes() : interests;
        return selected.stream().anyMatch(interest -> needsRefresh(city, interest, requiredEligibleCount(interest, demand)));
    }

    private boolean needsRefresh(String city, InterestType interest) {
        return needsRefresh(city, interest, 0);
    }

    private boolean needsRefresh(String city, InterestType interest, int requiredEligibleCount) {
        if (activities != null && requiredEligibleCount > 0
            && activities.countActiveByCityAndInterest(city, interest) < requiredEligibleCount) {
            return true;
        }
        if (states == null) {
            return false;
        }
        return !states.isFresh(
            city,
            interest,
            ActivityPersistenceService.CURRENT_IMPORT_VERSION,
            LocalDateTime.now().minusDays(FRESH_DAYS)
        );
    }

    private static int requiredEligibleCount(InterestType interest, ImportDemand demand) {
        return demand == null ? 0 : demand.eligibleTargetFor(interest);
    }

    void recordSync(String city, InterestType interest) {
        ActivityImportStateEntity state = states.findForCityAndInterest(city, interest)
            .orElseGet(ActivityImportStateEntity::new);
        state.city = city.trim();
        state.interest = interest;
        state.importVersion = ActivityPersistenceService.CURRENT_IMPORT_VERSION;
        state.syncedAt = LocalDateTime.now();
        if (state.id == null) {
            states.persist(state);
        }
    }
}
