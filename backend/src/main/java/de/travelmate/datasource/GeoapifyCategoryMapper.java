package de.travelmate.datasource;

import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class GeoapifyCategoryMapper {
    private static final Map<InterestType, List<String>> CATEGORIES = Map.of(
        InterestType.SIGHTSEEING, List.of(
            "tourism.sights",
            "tourism.sights.castle",
            "tourism.sights.place_of_worship.cathedral", "tourism.sights.city_gate",
            "tourism.attraction.viewpoint", "tourism.attraction.fountain", "tourism.attraction.artwork"
        ),
        InterestType.CULTURE, List.of(
            "entertainment.museum", "entertainment.culture.gallery",
            "entertainment.culture.theatre", "entertainment.culture.arts_centre"
        ),
        InterestType.NATURE, List.of(
            "leisure.park", "leisure.park.garden", "leisure.park.nature_reserve",
            "natural.protected_area", "natural.forest", "beach",
            "national_park"
        ),
        InterestType.FOOD, List.of(
            "catering.restaurant", "catering.cafe", "catering.biergarten", "catering.food_court"
        ),
        InterestType.SHOPPING, List.of(
            "commercial.shopping_mall", "commercial.department_store"
        ),
        InterestType.NIGHTLIFE, List.of(
            "catering.bar", "catering.pub"
        )
    );
//Filterstufen nach Geoapify Abfrage:
//Zuerst gibt es einfache Relevanzfilter:
//Hat der Ort einen Namen?
//Hat er Koordinaten?
//Ist er keine Gedenktafel, kein unwichtiger Memorial-Eintrag, keine reine Infrastruktur?
//Passt er wirklich zum gewünschten Interesse?
//
//
//
//Wichtige Faktoren sind:
//categoryFitScore: Passt der Ort wirklich zum Interesse?
//popularityScore: Ist der Ort bekannt oder relevant?
//notabilityScore: Gibt es Wikidata, Wikipedia, viele Sitelinks oder UNESCO-/Heritage-Hinweise?
//qualityScore: Hat der Ort Adresse, Öffnungszeiten, Website, Beschreibung?
//itineraryFitScore: Passt der Ort räumlich und zeitlich in einen Tagesplan?
//penalties: Abzüge, wenn etwas nicht passt, z. B. falsche Kategorie oder zu weit entfernt.


    public List<String> categoriesFor(InterestType interest) {
        return CATEGORIES.getOrDefault(interest, List.of());
    }

    public Map<InterestType, Integer> scoreInterests(Set<String> categories) {
        Map<InterestType, Integer> scores = new EnumMap<>(InterestType.class);
        for (Map.Entry<InterestType, List<String>> entry : CATEGORIES.entrySet()) {
            if (categories.stream().anyMatch(category -> matches(category, entry.getValue()))) {
                scores.put(entry.getKey(), 10);
            }
        }
        return scores;
    }

    public Map<InterestType, Integer> scoreInterest(InterestType interest) {
        return interest == null ? Map.of() : Map.of(interest, 10);
    }

    private static boolean matches(String category, List<String> accepted) {
        return accepted.stream().anyMatch(value -> category.equals(value) || category.startsWith(value + "."));
    }
}
