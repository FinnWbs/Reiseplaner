package de.travelmate.sync;

import de.travelmate.activity.*;
import de.travelmate.interest.InterestEntity;
import de.travelmate.interest.InterestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class ActivitySyncService {
    private static final int FRESH_DAYS = 7;
    private static final int MINIMUM_CITY_ACTIVITIES = 8;

    @Inject
    ActivityRepository activities;

    @Inject
    InterestRepository interests;

    public boolean cityNeedsRefresh(String city) {
        return !activities.hasFreshMinimumForCity(city, MINIMUM_CITY_ACTIVITIES, LocalDateTime.now().minusDays(FRESH_DAYS));
    }

    @Transactional
    public List<ActivityDto> syncCity(String city) {
        if (city == null || city.isBlank()) {
            throw new BadRequestException("Stadt darf nicht leer sein.");
        }

        Map<String, InterestEntity> interestByName = new HashMap<>();
        for (InterestEntity interest : interests.listAll()) {
            interestByName.put(interest.name, interest);
        }

        List<SeedActivity> sourceRows = demoRowsFor(city.trim());
        List<ActivityEntity> saved = new ArrayList<>();
        for (SeedActivity row : sourceRows) {
            ActivityEntity activity = activities.findBySourceAndExternalId(ActivitySource.DEMO, row.externalId)
                .orElseGet(ActivityEntity::new);

            activity.externalId = row.externalId;
            activity.source = ActivitySource.DEMO;
            activity.name = row.name;
            activity.description = row.description;
            activity.city = row.city;
            activity.category = row.category;
            activity.subcategory = row.subcategory;
            activity.latitude = row.latitude();
            activity.longitude = row.longitude();
            activity.address = row.address();
            activity.rating = row.rating();
            activity.dataQualityScore = row.quality();
            activity.lastSyncedAt = LocalDateTime.now();

            activity.interestScores.clear();
            for (Map.Entry<String, Integer> score : row.scores.entrySet()) {
                InterestEntity interest = interestByName.get(score.getKey());
                if (interest == null || score.getValue() <= 0) {
                    continue;
                }
                ActivityInterestEntity mapping = new ActivityInterestEntity();
                mapping.activity = activity;
                mapping.interest = interest;
                mapping.score = score.getValue();
                activity.interestScores.add(mapping);
            }

            if (activity.id == null) {
                activities.persist(activity);
            }
            saved.add(activity);
        }

        return saved.stream().map(ActivityDto::from).toList();
    }

    private List<SeedActivity> demoRowsFor(String requestedCity) {
        String city = normalizeCity(requestedCity);
        return List.of(
            new SeedActivity(city + "-museum", city, "Stadtmuseum " + city, "Lokale Kultur, Geschichte und wechselnde Ausstellungen.", "museum", "culture", 4.5, 0.86, Map.of("Kultur", 10, "Geschichte", 8)),
            new SeedActivity(city + "-old-town", city, "Altstadt " + city, "Historisches Zentrum mit Architektur, Plaetzen und Stadtgeschichte.", "landmark", "old-town", 4.7, 0.9, Map.of("Geschichte", 10, "Kultur", 7, "Shopping", 2)),
            new SeedActivity(city + "-park", city, "Stadtpark " + city, "Gruene Flaeche fuer Spaziergaenge und ruhige Pausen.", "park", "nature", 4.3, 0.82, Map.of("Natur", 10, "Sport", 4)),
            new SeedActivity(city + "-market", city, "Markthalle " + city, "Regionale Spezialitaeten, kleine Staende und Food-Angebote.", "food", "market", 4.4, 0.8, Map.of("Food", 10, "Kultur", 4)),
            new SeedActivity(city + "-shopping", city, "Einkaufsviertel " + city, "Laeden, Boutiquen und zentrale Einkaufsstrassen.", "shopping", "district", 4.0, 0.72, Map.of("Shopping", 10)),
            new SeedActivity(city + "-nightlife", city, "Ausgehviertel " + city, "Bars, Musik und Nachtleben im Stadtzentrum.", "nightlife", "district", 4.1, 0.7, Map.of("Nightlife", 10, "Food", 3)),
            new SeedActivity(city + "-stadium", city, "Sportareal " + city, "Stadion, Sportevents und aktive Freizeitangebote.", "sport", "stadium", 4.0, 0.68, Map.of("Sport", 10)),
            new SeedActivity(city + "-gallery", city, "Kunstgalerie " + city, "Moderne Kunst und kuratierte Ausstellungen.", "gallery", "art", 4.2, 0.77, Map.of("Kultur", 9, "Geschichte", 3)),
            new SeedActivity(city + "-river", city, "Uferpromenade " + city, "Spaziergang am Wasser mit Aussichtspunkten.", "nature", "promenade", 4.4, 0.76, Map.of("Natur", 8, "Sport", 3)),
            new SeedActivity(city + "-castle", city, "Schloss " + city, "Historisches Bauwerk mit kultureller Bedeutung.", "castle", "heritage", 4.6, 0.85, Map.of("Geschichte", 10, "Kultur", 8))
        );
    }

    private String normalizeCity(String city) {
        return city.substring(0, 1).toUpperCase() + city.substring(1).toLowerCase();
    }

    private record SeedActivity(
        String externalId,
        String city,
        String name,
        String description,
        String category,
        String subcategory,
        double rating,
        double quality,
        Map<String, Integer> scores
    ) {
        Double latitude() {
            return null;
        }

        Double longitude() {
            return null;
        }

        String address() {
            return city + " Zentrum";
        }
    }
}
