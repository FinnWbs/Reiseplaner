package de.travelmate.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.activity.ActivitySource;
import de.travelmate.datasource.ExternalActivityCandidate;
import de.travelmate.interest.InterestType;
import org.junit.jupiter.api.Test;

class PoiQualityEngineTest {
    private final PoiQualityEngine engine = new PoiQualityEngine();

    @Test
    void fountainIsNeverNature() {
        ExternalActivityCandidate fountain = candidate("Neptunbrunnen");
        fountain.rawCategories.add("natural.water");
        fountain.rawCategories.add("tourism.attraction.fountain");
        fountain.rawTags.put("amenity", "fountain");

        PoiQualityEvaluation evaluation = engine.evaluate(fountain, InterestType.NATURE);

        assertTrue(evaluation.hardExcluded());
        assertEquals("fountain_cannot_be_nature", evaluation.hardExclusionReason());
        assertTrue(evaluation.reasonCodes().contains(QualityReasonCode.HARD_EXCLUDED_NATURE_FOUNTAIN));
    }

    @Test
    void famousFountainCanBeSightseeing() {
        ExternalActivityCandidate fountain = candidate("Trevi-Brunnen");
        fountain.rawCategories.add("tourism.attraction.fountain");
        fountain.rawTags.put("amenity", "fountain");
        fountain.externalRefs.put(ActivitySource.WIKIDATA, "Q185382");
        fountain.externalRefs.put(ActivitySource.WIKIPEDIA, "Trevi-Brunnen");
        fountain.hasWikidata = true;
        fountain.hasImage = true;
        fountain.wikidataSitelinksCount = 160;
        fountain.wikipediaPageviews365d = 500_000;
        fountain.website = "https://example.test/trevi";
        fountain.distanceToCenterKm = 0.5;

        PoiQualityEvaluation evaluation = engine.evaluate(fountain, InterestType.SIGHTSEEING);

        assertFalse(evaluation.hardExcluded());
        assertEquals(CanonicalCategory.LANDMARK, evaluation.canonicalCategory());
        assertTrue(evaluation.finalScore() > 0.75);
        assertTrue(evaluation.reasonCodes().contains(QualityReasonCode.HAS_WIKIDATA));
        assertTrue(evaluation.reasonCodes().contains(QualityReasonCode.HAS_WIKIPEDIA));
    }

    @Test
    void smallStatueIsNotRelevantSightseeing() {
        ExternalActivityCandidate statue = candidate("Kleine Statue am Platz");
        statue.rawCategories.add("tourism.attraction.artwork.statue");
        statue.rawTags.put("artwork_type", "statue");

        PoiQualityEvaluation evaluation = engine.evaluate(statue, InterestType.SIGHTSEEING);

        assertTrue(evaluation.hardExcluded());
        assertEquals("low_notability_minor_sight", evaluation.hardExclusionReason());
        assertTrue(evaluation.reasonCodes().contains(QualityReasonCode.LOW_NOTABILITY_MINOR_MONUMENT));
    }

    @Test
    void museumWithReferencesScoresAsCulture() {
        ExternalActivityCandidate museum = candidate("Pergamonmuseum");
        museum.rawCategories.add("entertainment.museum");
        museum.externalRefs.put(ActivitySource.WIKIDATA, "Q160236");
        museum.externalRefs.put(ActivitySource.WIKIPEDIA, "Pergamonmuseum");
        museum.hasWikidata = true;
        museum.hasImage = true;
        museum.hasHeritageStatus = true;
        museum.wikidataSitelinksCount = 120;
        museum.wikipediaPageviews365d = 320_000;
        museum.website = "https://example.test/museum";
        museum.openingHours = "Mo-Su 10:00-18:00";
        museum.distanceToCenterKm = 1.2;

        PoiQualityEvaluation evaluation = engine.evaluate(museum, InterestType.CULTURE);

        assertFalse(evaluation.hardExcluded());
        assertEquals(CanonicalCategory.CULTURE, evaluation.canonicalCategory());
        assertTrue(evaluation.finalScore() > 0.8);
        assertTrue(evaluation.reasonCodes().contains(QualityReasonCode.HAS_HERITAGE_STATUS));
    }

    @Test
    void largeParkScoresAsNature() {
        ExternalActivityCandidate park = candidate("Tiergarten");
        park.rawCategories.add("leisure.park");
        park.geometryAreaM2 = 2_100_000d;
        park.distanceToCenterKm = 2.0;
        park.website = "https://example.test/tiergarten";

        PoiQualityEvaluation evaluation = engine.evaluate(park, InterestType.NATURE);

        assertFalse(evaluation.hardExcluded());
        assertEquals(CanonicalCategory.NATURE, evaluation.canonicalCategory());
        assertTrue(evaluation.finalScore() > 0.6);
        assertTrue(evaluation.reasonCodes().contains(QualityReasonCode.LARGE_GEO_AREA));
    }

    @Test
    void cemeteryIsNeverNatureEvenWithLargeArea() {
        ExternalActivityCandidate cemetery = candidate("Katharinenfriedhof (Garnisonsfriedhof)");
        cemetery.rawCategories.add("leisure.park");
        cemetery.rawCategories.add("cemetery");
        cemetery.rawTags.put("landuse", "cemetery");
        cemetery.geometryAreaM2 = 120_000d;

        PoiQualityEvaluation evaluation = engine.evaluate(cemetery, InterestType.NATURE);

        assertTrue(engine.isCemeteryOrBurialSite(cemetery));
        assertFalse(engine.canBeMainNatureActivity(cemetery));
        assertTrue(evaluation.hardExcluded());
        assertEquals("cemetery_not_nature_activity", evaluation.hardExclusionReason());
        assertEquals(0, evaluation.categoryFitScore());
        assertTrue(evaluation.reasonCodes().contains(QualityReasonCode.CEMETERY_EXCLUDED_FROM_NATURE));
    }

    @Test
    void notableCemeteryStillIsNotNature() {
        ExternalActivityCandidate cemetery = candidate("Pere Lachaise Cemetery");
        cemetery.rawCategories.add("natural.protected_area");
        cemetery.rawCategories.add("cemetery");
        cemetery.rawTags.put("landuse", "cemetery");
        cemetery.externalRefs.put(ActivitySource.WIKIDATA, "Q311");
        cemetery.externalRefs.put(ActivitySource.WIKIPEDIA, "Pere Lachaise Cemetery");
        cemetery.hasWikidata = true;
        cemetery.hasImage = true;
        cemetery.hasHeritageStatus = true;
        cemetery.website = "https://example.test/cemetery";
        cemetery.wikidataSitelinksCount = 180;
        cemetery.wikipediaPageviews365d = 800_000;

        PoiQualityEvaluation evaluation = engine.evaluate(cemetery, InterestType.NATURE);

        assertTrue(engine.isCemeteryOrBurialSite(cemetery));
        assertTrue(evaluation.notabilityScore() > 0.7);
        assertTrue(evaluation.hardExcluded());
        assertEquals("cemetery_not_nature_activity", evaluation.hardExclusionReason());
    }

    @Test
    void golfPrivateGardenAndSmallGreenInfrastructureAreNotNatureStops() {
        ExternalActivityCandidate golf = candidate("Golf Club");
        golf.rawCategories.add("leisure.golf_course");
        golf.rawTags.put("leisure", "golf_course");

        ExternalActivityCandidate privateGarden = candidate("Private Garden");
        privateGarden.rawCategories.add("leisure.park.garden");
        privateGarden.rawTags.put("leisure", "garden");
        privateGarden.rawTags.put("access", "private");

        ExternalActivityCandidate trafficIsland = candidate("Traffic Island Green Strip");
        trafficIsland.rawCategories.add("leisure.park");
        trafficIsland.rawTags.put("landuse", "grass");
        trafficIsland.geometryAreaM2 = 500d;

        assertFalse(engine.canBeMainNatureActivity(golf));
        assertFalse(engine.canBeMainNatureActivity(privateGarden));
        assertFalse(engine.canBeMainNatureActivity(trafficIsland));
        assertTrue(engine.evaluate(golf, InterestType.NATURE).hardExcluded());
        assertTrue(engine.evaluate(privateGarden, InterestType.NATURE).hardExcluded());
        assertTrue(engine.evaluate(trafficIsland, InterestType.NATURE).hardExcluded());
    }

    @Test
    void highlineGalleriaCannotBeNatureWithoutNatureEvidence() {
        ExternalActivityCandidate galleria = candidate("Highline Galleria");
        galleria.rawCategories.add("tourism.attraction");
        galleria.rawCategories.add("commercial");
        galleria.rawTags.put("tourism", "attraction");
        galleria.rawTags.put("building", "retail");

        PoiQualityEvaluation evaluation = engine.evaluate(galleria, InterestType.NATURE);

        assertFalse(engine.hasPositiveNatureEvidence(galleria));
        assertTrue(engine.hasNatureHardExclusion(galleria));
        assertFalse(engine.canBeMainNatureActivity(galleria));
        assertTrue(evaluation.hardExcluded());
        assertEquals(0, evaluation.categoryFitScore());
    }

    @Test
    void botanicalGardenRemainsNature() {
        ExternalActivityCandidate garden = candidate("Botanical Garden");
        garden.rawCategories.add("tourism.attraction");
        garden.rawTags.put("tourism", "attraction");
        garden.rawTags.put("leisure", "garden");
        garden.rawTags.put("garden:type", "botanical");
        garden.geometryAreaM2 = 500_000d;

        PoiQualityEvaluation evaluation = engine.evaluate(garden, InterestType.NATURE);

        assertTrue(engine.hasPositiveNatureEvidence(garden));
        assertFalse(engine.hasNatureHardExclusion(garden));
        assertTrue(engine.canBeMainNatureActivity(garden));
        assertFalse(evaluation.hardExcluded());
        assertEquals(CanonicalCategory.NATURE, evaluation.canonicalCategory());
        assertTrue(evaluation.categoryFitScore() >= 0.9);
    }

    @Test
    void railwayStationViewpointIsNeitherNatureNorSightseeingWithoutNotability() {
        ExternalActivityCandidate viewpoint = candidate("Tokyo Station train tracks Viewpoint 6F");
        viewpoint.rawCategories.add("tourism.attraction.viewpoint");
        viewpoint.rawTags.put("tourism", "viewpoint");
        viewpoint.rawTags.put("railway", "station");
        viewpoint.rawTags.put("level", "6");
        viewpoint.rawTags.put("building", "yes");

        PoiQualityEvaluation nature = engine.evaluate(viewpoint, InterestType.NATURE);
        PoiQualityEvaluation sightseeing = engine.evaluate(viewpoint, InterestType.SIGHTSEEING);

        assertTrue(engine.isViewpoint(viewpoint));
        assertEquals(ViewpointSubtype.VIEWPOINT_INFRASTRUCTURE, engine.viewpointSubtype(viewpoint));
        assertFalse(engine.hasNaturalViewpointContext(viewpoint));
        assertTrue(engine.hasUrbanOrInfrastructureViewpointContext(viewpoint));
        assertFalse(engine.canBeMainNatureActivity(viewpoint));
        assertTrue(nature.hardExcluded());
        assertEquals("viewpoint_not_nature_context", nature.hardExclusionReason());
        assertEquals(0, nature.categoryFitScore());
        assertTrue(nature.reasonCodes().contains(QualityReasonCode.INFRASTRUCTURE_VIEWPOINT_EXCLUDED_FROM_NATURE));
        assertTrue(sightseeing.hardExcluded());
        assertEquals("viewpoint_requires_notability_for_sightseeing", sightseeing.hardExclusionReason());
        assertEquals(0, sightseeing.categoryFitScore());
        assertTrue(sightseeing.reasonCodes().contains(QualityReasonCode.INFRASTRUCTURE_VIEWPOINT_EXCLUDED_FROM_SIGHTSEEING));
    }

    @Test
    void viewpointAloneIsNotNature() {
        ExternalActivityCandidate viewpoint = candidate("City Viewpoint");
        viewpoint.rawCategories.add("tourism.attraction.viewpoint");
        viewpoint.rawTags.put("tourism", "viewpoint");

        PoiQualityEvaluation evaluation = engine.evaluate(viewpoint, InterestType.NATURE);

        assertTrue(engine.isViewpoint(viewpoint));
        assertFalse(engine.hasNaturalViewpointContext(viewpoint));
        assertFalse(engine.canBeMainNatureActivity(viewpoint));
        assertTrue(evaluation.hardExcluded());
        assertTrue(evaluation.reasonCodes().contains(QualityReasonCode.VIEWPOINT_REQUIRES_NATURE_CONTEXT));
    }

    @Test
    void naturalViewpointRemainsNatureWithDirectNatureContext() {
        ExternalActivityCandidate viewpoint = candidate("Lake Cliff Viewpoint");
        viewpoint.rawCategories.add("tourism.attraction.viewpoint");
        viewpoint.rawTags.put("tourism", "viewpoint");
        viewpoint.rawTags.put("natural", "cliff");
        viewpoint.rawTags.put("route", "hiking");

        PoiQualityEvaluation evaluation = engine.evaluate(viewpoint, InterestType.NATURE);

        assertEquals(ViewpointSubtype.VIEWPOINT_NATURAL, engine.viewpointSubtype(viewpoint));
        assertTrue(engine.hasNaturalViewpointContext(viewpoint));
        assertTrue(engine.canBeMainNatureActivity(viewpoint));
        assertFalse(evaluation.hardExcluded());
        assertEquals(CanonicalCategory.NATURE, evaluation.canonicalCategory());
        assertTrue(evaluation.reasonCodes().contains(QualityReasonCode.NATURAL_VIEWPOINT_ALLOWED));
    }

    @Test
    void observationDeckRequiresNotabilityForSightseeing() {
        ExternalActivityCandidate deck = candidate("Observation Deck");
        deck.rawCategories.add("tourism.attraction.viewpoint");
        deck.rawTags.put("tourism", "viewpoint");
        deck.rawTags.put("building", "yes");
        deck.rawTags.put("level", "7");

        PoiQualityEvaluation weak = engine.evaluate(deck, InterestType.SIGHTSEEING);

        assertEquals(ViewpointSubtype.VIEWPOINT_INDOOR, engine.viewpointSubtype(deck));
        assertTrue(weak.hardExcluded());

        deck.externalRefs.put(ActivitySource.WIKIDATA, "Q123");
        deck.externalRefs.put(ActivitySource.WIKIPEDIA, "Observation Deck");
        deck.hasWikidata = true;
        deck.hasImage = true;
        deck.website = "https://example.test/deck";
        deck.wikidataSitelinksCount = 90;
        deck.wikipediaPageviews365d = 500_000;

        PoiQualityEvaluation notable = engine.evaluate(deck, InterestType.SIGHTSEEING);

        assertFalse(notable.hardExcluded());
        assertEquals(CanonicalCategory.LANDMARK, notable.canonicalCategory());
        assertTrue(notable.categoryFitScore() > 0);
    }

    @Test
    void parkNameWithShopOrBarEvidenceIsNotNature() {
        ExternalActivityCandidate shop = candidate("Park Concept Store");
        shop.rawCategories.add("shop");
        shop.rawTags.put("shop", "clothes");
        shop.rawTags.put("building", "retail");

        ExternalActivityCandidate bar = candidate("Garden Bar");
        bar.rawCategories.add("catering.bar");
        bar.rawTags.put("amenity", "bar");

        assertFalse(engine.canBeMainNatureActivity(shop));
        assertFalse(engine.canBeMainNatureActivity(bar));
        assertTrue(engine.evaluate(shop, InterestType.NATURE).hardExcluded());
        assertTrue(engine.evaluate(bar, InterestType.NATURE).hardExcluded());
    }

    @Test
    void xiMilanoSingleShopIsNotMainShoppingStop() {
        ExternalActivityCandidate shop = candidate("Xi Milano");
        shop.rawCategories.add("commercial.clothing");
        shop.rawTags.put("shop", "clothes");

        PoiQualityEvaluation evaluation = engine.evaluate(shop, InterestType.SHOPPING);

        assertTrue(engine.isSingleRetailStore(shop));
        assertFalse(engine.isShoppingDestination(shop));
        assertFalse(engine.canBeMainShoppingActivity(shop, evaluation.notabilityScore(), evaluation.popularityScore()));
        assertTrue(evaluation.hardExcluded());
        assertEquals(0, evaluation.categoryFitScore());
    }

    @Test
    void shoppingMallMarketAndDepartmentStoreAreMainShoppingStops() {
        ExternalActivityCandidate mall = candidate("Centro Commerciale");
        mall.rawCategories.add("commercial.shopping_mall");
        ExternalActivityCandidate market = candidate("Mercato Centrale");
        market.rawCategories.add("commercial.marketplace");
        ExternalActivityCandidate departmentStore = candidate("La Rinascente");
        departmentStore.rawCategories.add("commercial.department_store");

        assertEquals(ShoppingSubtype.SHOPPING_MALL, engine.shoppingSubtype(mall));
        assertEquals(ShoppingSubtype.MARKET, engine.shoppingSubtype(market));
        assertEquals(ShoppingSubtype.DEPARTMENT_STORE, engine.shoppingSubtype(departmentStore));
        assertFalse(engine.evaluate(mall, InterestType.SHOPPING).hardExcluded());
        assertFalse(engine.evaluate(market, InterestType.SHOPPING).hardExcluded());
        assertFalse(engine.evaluate(departmentStore, InterestType.SHOPPING).hardExcluded());
    }

    @Test
    void singleFlagshipStoreRequiresStrongNotability() {
        ExternalActivityCandidate flagship = candidate("Famous Flagship Store");
        flagship.rawCategories.add("shop.clothes");
        flagship.rawTags.put("shop", "clothes");
        flagship.externalRefs.put(ActivitySource.WIKIDATA, "Q123");
        flagship.externalRefs.put(ActivitySource.WIKIPEDIA, "Famous Flagship Store");
        flagship.hasWikidata = true;
        flagship.hasImage = true;
        flagship.website = "https://example.test/flagship";
        flagship.wikidataSitelinksCount = 200;
        flagship.wikipediaPageviews365d = 700_000;

        PoiQualityEvaluation evaluation = engine.evaluate(flagship, InterestType.SHOPPING);

        assertTrue(engine.isSingleRetailStore(flagship));
        assertTrue(evaluation.notabilityScore() >= 0.75 || evaluation.popularityScore() >= 0.75);
        assertFalse(evaluation.hardExcluded());
        assertTrue(evaluation.categoryFitScore() >= 0.9);
    }

    @Test
    void galleriaNameDoesNotMakeNatureButCanSignalShoppingArcade() {
        ExternalActivityCandidate arcade = candidate("Galleria Commerciale");
        arcade.rawCategories.add("tourism.attraction.viewpoint");
        arcade.rawTags.put("building", "retail");

        PoiQualityEvaluation nature = engine.evaluate(arcade, InterestType.NATURE);
        PoiQualityEvaluation shopping = engine.evaluate(arcade, InterestType.SHOPPING);

        assertFalse(engine.canBeMainNatureActivity(arcade));
        assertEquals(ShoppingSubtype.SHOPPING_ARCADE, engine.shoppingSubtype(arcade));
        assertTrue(nature.hardExcluded());
        assertFalse(shopping.hardExcluded());
    }

    @Test
    void foodWithoutRatingStillGetsQualityFallback() {
        ExternalActivityCandidate restaurant = candidate("Kiez Restaurant");
        restaurant.rawCategories.add("catering.restaurant");
        restaurant.rawTags.put("amenity", "restaurant");
        restaurant.rawTags.put("cuisine", "regional");
        restaurant.externalRefs.put(ActivitySource.OPEN_STREET_MAP, "node/123");
        restaurant.address = "Teststrasse 1";
        restaurant.website = "https://example.test/restaurant";
        restaurant.openingHours = "Mo-Su 11:00-23:00";

        PoiQualityEvaluation evaluation = engine.evaluate(restaurant, InterestType.FOOD);

        assertFalse(evaluation.hardExcluded());
        assertEquals(CanonicalCategory.FOOD, evaluation.canonicalCategory());
        assertTrue(evaluation.qualityScore() > 0.75);
        assertTrue(evaluation.finalScore() > 0.6);
        assertTrue(evaluation.reasonCodes().contains(QualityReasonCode.HAS_CUISINE));
    }

    @Test
    void missingCoordinatesAreHardExcluded() {
        ExternalActivityCandidate place = candidate("Ort ohne Koordinaten");
        place.latitude = null;

        PoiQualityEvaluation evaluation = engine.evaluate(place, InterestType.SIGHTSEEING);

        assertTrue(evaluation.hardExcluded());
        assertEquals("missing_coordinates", evaluation.hardExclusionReason());
        assertEquals(0, evaluation.finalScore());
    }

    @Test
    void diversityChangesPlanningScore() {
        double diverse = engine.planningScore(0.7, 1.0, 0.8, 1.0);
        double repeated = engine.planningScore(0.7, 1.0, 0.8, 0.2);

        assertTrue(diverse > repeated);
    }

    @Test
    void centerDistanceDoesNotCreateFoodPopularityAdvantage() {
        ExternalActivityCandidate central = candidate("Central Restaurant");
        central.rawCategories.add("catering.restaurant");
        central.rawTags.put("cuisine", "regional");
        central.distanceToCenterKm = 0.2;
        ExternalActivityCandidate outer = candidate("Outer Restaurant");
        outer.rawCategories.add("catering.restaurant");
        outer.rawTags.put("cuisine", "regional");
        outer.distanceToCenterKm = 10.0;

        PoiQualityEvaluation centralEvaluation = engine.evaluate(central, InterestType.FOOD);
        PoiQualityEvaluation outerEvaluation = engine.evaluate(outer, InterestType.FOOD);

        assertEquals(centralEvaluation.popularityScore(), outerEvaluation.popularityScore(), 0.001);
    }

    private static ExternalActivityCandidate candidate(String name) {
        ExternalActivityCandidate candidate = new ExternalActivityCandidate();
        candidate.source = ActivitySource.GEOAPIFY;
        candidate.externalId = "geo-" + name;
        candidate.externalRefs.put(ActivitySource.GEOAPIFY, candidate.externalId);
        candidate.name = name;
        candidate.latitude = 52.52;
        candidate.longitude = 13.405;
        candidate.distanceToCenterKm = 1.0;
        return candidate;
    }
}
