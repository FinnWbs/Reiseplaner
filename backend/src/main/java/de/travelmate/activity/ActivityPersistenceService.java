package de.travelmate.activity;

import de.travelmate.datasource.ExternalActivityCandidate;
import de.travelmate.interest.InterestEntity;
import de.travelmate.interest.InterestRepository;
import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class ActivityPersistenceService {
    public static final int CURRENT_IMPORT_VERSION = 2;
    @Inject
    ActivityRepository activities;

    @Inject
    ActivityExternalRefRepository externalRefs;

    @Inject
    InterestRepository interests;

    @Inject
    ActivityCategoryMapper categoryMapper;

    @Transactional
    public ActivityImportResponse persist(
        String city,
        List<ExternalActivityCandidate> candidates,
        List<String> warnings
    ) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        Map<String, InterestEntity> interestByCode = new HashMap<>();
        interests.listAll().forEach(interest -> interestByCode.put(interest.code, interest));

        for (ExternalActivityCandidate candidate : candidates) {
            if (candidate.name == null || candidate.name.isBlank() || candidate.externalId == null) {
                skipped++;
                continue;
            }

            Optional<ActivityEntity> existing = findExisting(candidate, city);
            ActivityEntity activity = existing.orElseGet(ActivityEntity::new);
            boolean isNew = activity.id == null;

            activity.source = candidate.source;
            activity.externalId = candidate.externalId;
            activity.name = candidate.name.trim();
            activity.city = city;
            activity.description = blankToNull(candidate.description);
            activity.subcategory = blankToNull(candidate.rawCategory);
            activity.latitude = candidate.latitude;
            activity.longitude = candidate.longitude;
            activity.address = blankToNull(candidate.address);
            activity.rating = candidate.rating;
            activity.primaryInterest = candidate.primaryInterest;
            activity.active = true;
            activity.importVersion = CURRENT_IMPORT_VERSION;
            activity.dataQualityScore = qualityScore(candidate);
            activity.lastSyncedAt = LocalDateTime.now();

            ActivityCategoryMapper.CategoryMapping mapping =
                categoryMapper.map(candidate.primaryInterest, candidate.rawCategories);
            activity.category = mapping.dominantCategory();
            if (isNew) {
                activities.persist(activity);
                activities.flush();
                created++;
            } else {
                updated++;
            }
            replaceInterestScores(activity, mapping.interestScores(), interestByCode, isNew);
            mergeExternalRefs(activity, candidate.externalRefs, warnings);
        }

        activities.flush();
        List<ActivityDto> saved = activities.findByCity(city).stream().map(ActivityDto::from).toList();
        return new ActivityImportResponse(city, created, updated, skipped, saved, List.copyOf(warnings));
    }

    @Transactional
    public void deactivateGeoapifyActivities(String city, InterestType interest) {
        activities.deactivateForCityAndInterest(city, interest);
    }

    private Optional<ActivityEntity> findExisting(ExternalActivityCandidate candidate, String city) {
        for (Map.Entry<ActivitySource, String> ref : candidate.externalRefs.entrySet()) {
            Optional<ActivityExternalRefEntity> found =
                externalRefs.findBySourceAndExternalId(ref.getKey(), ref.getValue());
            if (found.isPresent()) {
                return Optional.of(found.get().activity);
            }
        }
        Optional<ActivityEntity> primary =
            activities.findBySourceAndExternalId(candidate.source, candidate.externalId);
        if (primary.isPresent()) {
            return primary;
        }
        String normalizedName = normalize(candidate.name);
        String normalizedCity = normalize(city);
        return activities.findByCity(city).stream()
            .filter(activity ->
                normalize(activity.name).equals(normalizedName) && normalize(activity.city).equals(normalizedCity)
            )
            .findFirst();
    }

    private void mergeExternalRefs(
        ActivityEntity activity,
        Map<ActivitySource, String> candidateRefs,
        List<String> warnings
    ) {
        for (Map.Entry<ActivitySource, String> entry : candidateRefs.entrySet()) {
            Optional<ActivityExternalRefEntity> global =
                externalRefs.findBySourceAndExternalId(entry.getKey(), entry.getValue());
            if (global.isPresent() && !global.get().activity.id.equals(activity.id)) {
                warnings.add("Externe Referenz fuer " + activity.name + " war bereits vergeben.");
                continue;
            }
            boolean alreadyPresent = activity.externalRefs.stream().anyMatch(ref ->
                ref.source == entry.getKey() && ref.externalId.equals(entry.getValue())
            );
            if (!alreadyPresent) {
                ActivityExternalRefEntity ref = new ActivityExternalRefEntity();
                ref.activity = activity;
                ref.source = entry.getKey();
                ref.externalId = entry.getValue();
                activity.externalRefs.add(ref);
                externalRefs.persist(ref);
            }
        }
    }

    private void replaceInterestScores(
        ActivityEntity activity,
        Map<InterestType, Integer> scores,
        Map<String, InterestEntity> interestByCode,
        boolean isNew
    ) {
        if (!isNew) {
            activity.interestScores.clear();
            activities.flush();
        } else {
            activity.interestScores.clear();
        }
        for (Map.Entry<InterestType, Integer> score : scores.entrySet()) {
            InterestEntity interest = interestByCode.get(score.getKey().name());
            if (interest == null) {
                continue;
            }
            ActivityInterestEntity link = new ActivityInterestEntity();
            link.activity = activity;
            link.interest = interest;
            link.score = score.getValue();
            activity.interestScores.add(link);
        }
    }

    private static double qualityScore(ExternalActivityCandidate candidate) {
        int available = 2;
        if (candidate.description != null && !candidate.description.isBlank()) available++;
        if (candidate.address != null && !candidate.address.isBlank()) available++;
        if (candidate.latitude != null && candidate.longitude != null) available++;
        if (candidate.rawCategory != null && !candidate.rawCategory.isBlank()) available++;
        return available / 6.0;
    }

    private static String normalize(String value) {
        String ascii = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return ascii.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
