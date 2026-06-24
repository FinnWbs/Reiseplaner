package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import de.travelmate.interest.InterestType;
import org.junit.jupiter.api.Test;

class GeoapifyCategoryMapperTest {
    private final GeoapifyCategoryMapper mapper = new GeoapifyCategoryMapper();

    @Test
    void sightseeingUsesOnlySpecificVisitorCategories() {
        assertFalse(mapper.categoriesFor(InterestType.SIGHTSEEING).contains("tourism.sights"));
        assertFalse(mapper.categoriesFor(InterestType.SIGHTSEEING).contains("tourism.sights.viewpoint"));
    }
}
