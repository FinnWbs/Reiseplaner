package de.travelmate.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ActivityCategoryMapperTest {
    private final ActivityCategoryMapper mapper = new ActivityCategoryMapper();

    @Test
    void mapsRepresentativeExternalCategories() {
        assertEquals("Kultur", mapper.map("entertainment.museum", "Museum").dominantCategory());
        assertEquals("Geschichte", mapper.map("heritage.monument", "Denkmal").dominantCategory());
        assertEquals("Natur", mapper.map("leisure.park", "Stadtpark").dominantCategory());
        assertEquals("Food", mapper.map("catering.restaurant", "Restaurant").dominantCategory());
        assertEquals("Nightlife", mapper.map("catering.bar", "Bar").dominantCategory());
        assertEquals("Shopping", mapper.map("commercial.shopping_mall", "Center").dominantCategory());
        assertEquals("Sport", mapper.map("sport.stadium", "Stadion").dominantCategory());
    }
}
