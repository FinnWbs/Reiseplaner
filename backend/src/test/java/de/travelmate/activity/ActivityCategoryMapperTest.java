package de.travelmate.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import de.travelmate.interest.InterestType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActivityCategoryMapperTest {
    private final ActivityCategoryMapper mapper = new ActivityCategoryMapper();

    @Test
    void mapsRepresentativeExternalCategories() {
        assertEquals("Kultur & Museen", mapper.map(Set.of("entertainment.museum")).dominantCategory());
        assertEquals("Sehenswürdigkeiten", mapper.map(Set.of("tourism.sights.castle")).dominantCategory());
        assertEquals("Natur & Outdoor", mapper.map(Set.of("leisure.park")).dominantCategory());
        assertEquals("Essen & Cafés", mapper.map(Set.of("catering.restaurant")).dominantCategory());
        assertEquals("Nachtleben & Unterhaltung", mapper.map(Set.of("catering.bar")).dominantCategory());
        assertEquals("Shopping & Märkte", mapper.map(Set.of("commercial.shopping_mall")).dominantCategory());
        assertEquals(10, mapper.map(Set.of("catering.bar")).interestScores().get(InterestType.NIGHTLIFE));
    }
}
