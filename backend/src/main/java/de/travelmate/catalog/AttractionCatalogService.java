package de.travelmate.catalog;

import de.travelmate.activity.ActivityEntity;
import de.travelmate.activity.ActivityExternalRefEntity;
import de.travelmate.activity.ActivityRepository;
import de.travelmate.activity.ActivitySource;
import de.travelmate.interest.InterestType;
import de.travelmate.planning.ActivityTimeRules;
import de.travelmate.quality.CanonicalCategory;
import de.travelmate.trip.TripDayActivityEntity;
import de.travelmate.trip.TripDayActivityRepository;
import de.travelmate.trip.TripDayEntity;
import de.travelmate.trip.TripEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class AttractionCatalogService {
    private static final int CATALOG_IMPORT_VERSION = 0;
    private static final int STOP_GAP_MINUTES = 30;

    @Inject
    AttractionCatalogSeedRepository seeds;

    @Inject
    AttractionCatalogGenerationService generatedCatalog;

    @Inject
    ActivityRepository activities;

    @Inject
    TripDayActivityRepository tripActivities;

    public AttractionCatalogResponse listForTrip(TripEntity trip) {
        List<AttractionCatalogEntry> entries = entriesForTrip(trip);
        if (entries.isEmpty()) {
            return new AttractionCatalogResponse(
                false,
                "Fuer diese Stadt konnten noch keine Highlights erzeugt werden.",
                List.of()
            );
        }
        List<AttractionCatalogItemDto> items = entries.stream()
            .map(entry -> toDto(entry, plannedDayNumbers(trip, entry)))
            .toList();
        return new AttractionCatalogResponse(true, "Highlights fuer " + trip.city, items);
    }

    public void addToDay(TripEntity trip, Long dayId, String catalogId) {
        AttractionCatalogEntry entry = entriesForTrip(trip).stream()
            .filter(candidate -> candidate.catalogId().equals(catalogId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Highlight nicht gefunden."));
        if (!plannedDayNumbers(trip, entry).isEmpty()) {
            throw new BadRequestException("Dieses Highlight ist bereits im Reiseplan.");
        }

        TripDayEntity day = requireDay(trip, dayId);
        ActivityEntity activity = findOrCreateActivity(entry);
        TripDayActivityEntity item = new TripDayActivityEntity();
        item.tripDay = day;
        item.activity = activity;
        item.position = day.activities.size() + 1;
        item.locked = true;
        ActivityTimeRules.TimeProfile profile = ActivityTimeRules.profile(activity);
        item.durationMinutes = profile.durationMinutes();
        item.scheduledStart = nextStart(day, profile);
        item.notes = "Aus dem Highlight-Katalog hinzugefuegt.";
        day.activities.add(item);
        tripActivities.persist(item);
    }

    private AttractionCatalogItemDto toDto(AttractionCatalogEntry entry, List<Integer> plannedDayNumbers) {
        return new AttractionCatalogItemDto(
            entry.catalogId(),
            entry.name(),
            entry.city(),
            entry.wikidataId(),
            entry.wikipediaProject(),
            entry.wikipediaTitle(),
            entry.primaryInterest(),
            entry.category(),
            entry.latitude(),
            entry.longitude(),
            entry.rank(),
            entry.description(),
            entry.publicAttractionScore(),
            entry.pageviews(),
            entry.sitelinkCount(),
            entry.source(),
            !plannedDayNumbers.isEmpty(),
            plannedDayNumbers
        );
    }

    private List<AttractionCatalogEntry> entriesForTrip(TripEntity trip) {
        List<AttractionCatalogEntry> seedEntries = seeds.findByCity(trip.city);
        if (!seedEntries.isEmpty()) {
            return seedEntries;
        }
        return generatedCatalog == null ? List.of() : generatedCatalog.findOrGenerate(trip);
    }

    private List<Integer> plannedDayNumbers(TripEntity trip, AttractionCatalogEntry entry) {
        List<Integer> dayNumbers = new ArrayList<>();
        for (TripDayEntity day : trip.days) {
            boolean planned = day.activities.stream().anyMatch(item -> matches(entry, item.activity));
            if (planned) {
                dayNumbers.add(day.dayNumber);
            }
        }
        return dayNumbers;
    }

    private boolean matches(AttractionCatalogEntry entry, ActivityEntity activity) {
        if (entry.wikidataId() != null && !entry.wikidataId().isBlank()) {
            if (activity.source == ActivitySource.WIKIDATA && entry.wikidataId().equals(activity.externalId)) {
                return true;
            }
            boolean externalRefMatch = activity.externalRefs.stream().anyMatch(ref ->
                ref.source == ActivitySource.WIKIDATA && entry.wikidataId().equals(ref.externalId)
            );
            if (externalRefMatch) {
                return true;
            }
        }
        return normalized(entry.name()).equals(normalized(activity.name))
            && normalized(entry.city()).equals(normalized(activity.city));
    }

    private ActivityEntity findOrCreateActivity(AttractionCatalogEntry entry) {
        Optional<ActivityEntity> existing = Optional.empty();
        if (entry.wikidataId() != null && !entry.wikidataId().isBlank()) {
            existing = activities.findByExternalReference(ActivitySource.WIKIDATA, entry.wikidataId());
            if (existing.isEmpty()) {
                existing = activities.findBySourceAndExternalId(ActivitySource.WIKIDATA, entry.wikidataId());
            }
        }
        if (existing.isEmpty()) {
            existing = activities.findByNormalizedNameAndCity(normalized(entry.name()), normalized(entry.city()));
        }
        ActivityEntity activity = existing.orElseGet(ActivityEntity::new);
        boolean newActivity = activity.id == null;
        boolean catalogOwned = newActivity || (
            activity.source == ActivitySource.WIKIDATA
                && entry.wikidataId() != null
                && entry.wikidataId().equals(activity.externalId)
        );
        updateActivity(activity, entry, catalogOwned);
        if (activity.id == null) {
            activities.persist(activity);
        }
        return activity;
    }

    private void updateActivity(ActivityEntity activity, AttractionCatalogEntry entry, boolean catalogOwned) {
        if (!catalogOwned) {
            ensureWikidataRef(activity, entry);
            return;
        }
        activity.source = ActivitySource.WIKIDATA;
        activity.externalId = entry.wikidataId() == null || entry.wikidataId().isBlank()
            ? entry.catalogId()
            : entry.wikidataId();
        activity.name = entry.name();
        activity.description = entry.description();
        activity.city = entry.city();
        activity.category = entry.category();
        activity.subcategory = "catalog.highlight";
        activity.latitude = entry.latitude();
        activity.longitude = entry.longitude();
        activity.primaryInterest = entry.primaryInterest();
        activity.canonicalCategory = canonicalCategory(entry.primaryInterest());
        activity.active = false;
        activity.importVersion = CATALOG_IMPORT_VERSION;
        activity.dataQualityScore = 0.9;
        activity.notabilityScore = Math.max(activity.notabilityScore, scoreFromRank(entry.rank()));
        activity.popularityScore = Math.max(activity.popularityScore, scoreFromEntry(entry));
        activity.qualityScore = Math.max(activity.qualityScore, scoreFromEntry(entry));
        activity.categoryFitScore = Math.max(activity.categoryFitScore, 0.85);
        activity.itineraryFitScore = Math.max(activity.itineraryFitScore, 0.5);
        activity.finalScore = Math.max(activity.finalScore, scoreFromEntry(entry));
        activity.qualityReasonCodes = "CATALOG_HIGHLIGHT";
        activity.lastSyncedAt = LocalDateTime.now();
        ensureWikidataRef(activity, entry);
    }

    private void ensureWikidataRef(ActivityEntity activity, AttractionCatalogEntry entry) {
        if (entry.wikidataId() == null || entry.wikidataId().isBlank()) {
            return;
        }
        boolean exists = activity.externalRefs.stream().anyMatch(ref ->
            ref.source == ActivitySource.WIKIDATA && entry.wikidataId().equals(ref.externalId)
        );
        if (exists) {
            return;
        }
        ActivityExternalRefEntity ref = new ActivityExternalRefEntity();
        ref.activity = activity;
        ref.source = ActivitySource.WIKIDATA;
        ref.externalId = entry.wikidataId();
        activity.externalRefs.add(ref);
    }

    private int nextStart(TripDayEntity day, ActivityTimeRules.TimeProfile profile) {
        int cursor = day.activities.stream()
            .max(Comparator.comparingInt(item -> item.position))
            .map(item -> item.scheduledStart + item.durationMinutes + STOP_GAP_MINUTES)
            .orElse(day.availableFrom);
        int start = Math.max(cursor, Math.max(day.availableFrom, profile.earliestStart()));
        if (start < profile.preferredStart()
            && profile.preferredStart() + profile.durationMinutes() <= day.availableUntil) {
            start = profile.preferredStart();
        }
        return Math.min(start, 1440 - profile.durationMinutes());
    }

    private TripDayEntity requireDay(TripEntity trip, Long dayId) {
        return trip.days.stream()
            .filter(day -> day.id.equals(dayId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Reisetag nicht gefunden."));
    }

    private static CanonicalCategory canonicalCategory(InterestType interestType) {
        return switch (interestType) {
            case CULTURE -> CanonicalCategory.CULTURE;
            case NATURE -> CanonicalCategory.NATURE;
            case FOOD -> CanonicalCategory.FOOD;
            case SHOPPING -> CanonicalCategory.SHOPPING;
            case NIGHTLIFE -> CanonicalCategory.NIGHTLIFE;
            default -> CanonicalCategory.LANDMARK;
        };
    }

    private static double scoreFromRank(int rank) {
        return Math.max(0.65, 1.0 - Math.max(0, rank - 1) * 0.025);
    }

    private static double scoreFromEntry(AttractionCatalogEntry entry) {
        if (entry.publicAttractionScore() != null && entry.publicAttractionScore() > 0) {
            return Math.max(0.65, Math.min(1.0, entry.publicAttractionScore() / 100.0));
        }
        return scoreFromRank(entry.rank());
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
