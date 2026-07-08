package de.travelmate.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.datasource.CityBoundingBox;
import org.junit.jupiter.api.Test;

class CityBoundaryTest {
    @Test
    void rejectsWolfsburgSizedDistanceOutsideBraunschweigBoundary() {
        CityBoundary braunschweig = CityBoundary.fromBoundingBox(
            "Braunschweig",
            "Q2773",
            52.2689,
            10.5268,
            new CityBoundingBox(52.18, 10.43, 52.34, 10.62),
            1.5,
            3.0
        );

        assertFalse(braunschweig.rejectsHard(52.2642, 10.5238));
        assertTrue(braunschweig.rejectsHard(52.4319, 10.7915));
    }

    @Test
    void queryRadiusComesFromBoundingBoxInsteadOfFixedThirtyFiveKilometers() {
        CityBoundary braunschweig = CityBoundary.fromBoundingBox(
            "Braunschweig",
            "Q2773",
            52.2689,
            10.5268,
            new CityBoundingBox(52.18, 10.43, 52.34, 10.62),
            1.5,
            3.0
        );

        assertTrue(braunschweig.queryRadiusKm(80) < 20.0);
        assertTrue(braunschweig.queryRadiusKm(80) > 5.0);
    }
}
