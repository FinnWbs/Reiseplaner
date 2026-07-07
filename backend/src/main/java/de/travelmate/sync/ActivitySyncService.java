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
import de.travelmate.planning.SpatialCoverageReport;
import de.travelmate.planning.SpatialCoverageService;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ActivitySyncService {
    private static final Logger LOG = Logger.getLogger(ActivitySyncService.class);
    private static final int FRESH_DAYS = 7;
    private static final int MINIMUM_CITY_ACTIVITIES = 8;

    @Inject
    ActivityRepository activities;

    @Inject
    ActivityImportService importer;

    @Inject
    ActivityImportStateRepository states;

    @Inject
    SpatialCoverageService coverage;

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
            RefreshDecision decision = refreshDecision(city, interest, demand, latitude, longitude);
            if (!decision.importRequired()) {
                logDecision(city, interest, decision);
                continue;
            }
            latest = importer.importInterest(city, lookupText, placeId, latitude, longitude, interest, demand).activities();
            recordSync(city, interest);
            logDecision(city, interest, decision);
        }
        return latest;
    }

    public boolean needsRefresh(String city, Set<InterestType> interests) {
        Set<InterestType> selected = interests == null || interests.isEmpty() ? InterestType.primaryTypes() : interests;
        return selected.stream().anyMatch(interest -> needsRefresh(city, interest));
    }

    public boolean needsRefresh(String city, Set<InterestType> interests, ImportDemand demand) {
        return refreshDecision(city, interests, demand, null, null).importRequired();
    }

    public RefreshDecision refreshDecision(
        String city,
        Set<InterestType> interests,
        ImportDemand demand,
        Double latitude,
        Double longitude
    ) {
        Set<InterestType> selected = interests == null || interests.isEmpty() ? InterestType.primaryTypes() : interests;
        RefreshDecision result = RefreshDecision.NO_REFRESH_NEEDED;
        for (InterestType interest : selected) {
            result = result.max(refreshDecision(city, interest, demand, latitude, longitude));
        }
        return result;
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

    private RefreshDecision refreshDecision(
        String city,
        InterestType interest,
        ImportDemand demand,
        Double latitude,
        Double longitude
    ) {
        int requiredEligibleCount = requiredEligibleCount(interest, demand);
        if (activities != null && requiredEligibleCount > 0
            && activities.countActiveByCityAndInterest(city, interest) < requiredEligibleCount) {
            return RefreshDecision.REFRESH_FOR_COUNT;
        }
        if (states == null) {
            return RefreshDecision.NO_REFRESH_NEEDED;
        }
        boolean fresh = states.isFresh(
            city,
            interest,
            ActivityPersistenceService.CURRENT_IMPORT_VERSION,
            LocalDateTime.now().minusDays(FRESH_DAYS)
        );
        if (!fresh) {
            return RefreshDecision.REFRESH_FOR_VERSION;
        }
        SpatialCoverageReport report = coverageReport(city, interest, demand, latitude, longitude);
        if (report != null && report.insufficient()) {
            return demand != null && demand.multiAreaAllowed()
                ? RefreshDecision.MULTI_AREA_REFRESH_REQUIRED
                : RefreshDecision.MULTI_AREA_RECOMMENDED_ONLY;
        }
        return RefreshDecision.NO_REFRESH_NEEDED;
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

    private SpatialCoverageReport coverageReport(
        String city,
        InterestType interest,
        ImportDemand demand,
        Double latitude,
        Double longitude
    ) {
        if (coverage == null || activities == null || demand == null || !demand.requireOuterCoverageForLongTrip()) {
            return null;
        }
        return coverage.analyze(
            city,
            interest,
            activities.findActiveByCity(city),
            demand,
            latitude,
            longitude
        );
    }

    private void logDecision(String city, InterestType interest, RefreshDecision decision) {
        if (decision == RefreshDecision.MULTI_AREA_RECOMMENDED_ONLY) {
            LOG.warnf(
                "Spatial coverage for %s/%s is insufficient; multi-area import is disabled, keeping current cache.",
                city,
                interest
            );
        } else if (decision == RefreshDecision.MULTI_AREA_REFRESH_REQUIRED) {
            LOG.infof("Spatial coverage for %s/%s is insufficient; multi-area import will run.", city, interest);
        }
    }
}
