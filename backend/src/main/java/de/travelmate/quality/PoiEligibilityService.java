package de.travelmate.quality;

import de.travelmate.activity.ActivitySource;
import de.travelmate.datasource.ExternalActivityCandidate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;

@ApplicationScoped
public class PoiEligibilityService {
    @Inject
    PoiSignalMatcher matcher;

    @Inject
    PoiViewpointClassifier viewpoints;

    public boolean hasPositiveNatureEvidence(ExternalActivityCandidate candidate) {
        if (viewpoints().isViewpoint(candidate)) {
            return viewpoints().hasNaturalContext(candidate);
        }
        return signals().matches(candidate,
            "leisure.park", "leisure.park.garden", "leisure.park.nature_reserve",
            "national_park", "natural.protected_area", "natural.forest", "beach",
            "natural.mountain", "natural.water", "natural.waterfall", "natural.wood"
        ) || signals().hasTagValue(candidate, "leisure", "park", "garden", "nature_reserve")
            || signals().hasTagValue(candidate, "natural", "wood", "forest", "beach", "waterfall")
            || signals().hasTagValue(candidate, "natural", "peak", "cliff", "hill", "mountain_range", "water")
            || signals().hasTagValue(candidate, "boundary", "national_park")
            || signals().hasTag(candidate, "protected_area")
            || signals().hasTagValue(candidate, "garden:type", "botanical")
            || signals().hasTagValue(candidate, "garden:type", "botanic")
            || signals().hasTagValue(candidate, "garden", "botanical")
            || signals().hasTagValue(candidate, "botanical", "yes")
            || signals().hasTagValue(candidate, "route", "hiking");
    }

    public boolean hasNatureHardExclusion(ExternalActivityCandidate candidate) {
        return isFountain(candidate)
            || isCemeteryOrBurialSite(candidate)
            || viewpoints().hasUrbanOrInfrastructureContext(candidate)
            || isGolfCourse(candidate)
            || isPrivateGarden(candidate)
            || isSmallGreenInfrastructure(candidate)
            || isWeakSubPoiWithoutOwnRelevance(candidate)
            || isShoppingDestination(candidate)
            || isSingleRetailStore(candidate)
            || signals().matches(candidate,
                "commercial", "shop", "catering", "entertainment.museum", "entertainment.culture.gallery",
                "tourism.attraction.artwork", "tourism.sights.memorial", "historic.memorial",
                "waterway", "highway", "emergency", "parking", "amenity.toilet",
                "leisure.playground", "leisure.picnic"
            )
            || signals().hasTagValue(candidate, "amenity", "restaurant", "cafe", "fast_food", "bar", "pub")
            || signals().hasTagValue(candidate, "amenity", "toilets", "parking")
            || signals().hasTagValue(candidate, "tourism", "museum", "gallery", "artwork")
            || signals().hasTagValue(candidate, "historic", "memorial", "monument")
            || signals().hasTagValue(candidate, "natural", "tree", "spring")
            || signals().hasTagValue(candidate, "highway", "path", "footway", "track")
            || signals().hasTagValue(candidate, "barrier", "entrance")
            || signals().hasTag(candidate, "man_made")
            || signals().hasTag(candidate, "parking")
            || signals().hasTagValue(candidate, "building", "yes", "retail", "commercial", "office")
            || signals().hasTagValue(candidate, "office", "yes")
            || signals().hasTagValue(candidate, "indoor", "yes")
            || isPlainAttractionWithoutNatureEvidence(candidate);
    }

    public boolean canBeMainNatureActivity(ExternalActivityCandidate candidate) {
        if (viewpoints().isViewpoint(candidate)) {
            return viewpoints().hasNaturalContext(candidate) && !viewpoints().hasUrbanOrInfrastructureContext(candidate);
        }
        return hasPositiveNatureEvidence(candidate) && !hasNatureHardExclusion(candidate);
    }

    public boolean canBeMainSightseeingActivity(
        ExternalActivityCandidate candidate,
        double notabilityScore,
        double popularityScore
    ) {
        if (!viewpoints().isViewpoint(candidate)) return true;
        ViewpointSubtype subtype = viewpoints().subtype(candidate);
        if (subtype == ViewpointSubtype.VIEWPOINT_NATURAL) return true;
        if (subtype == ViewpointSubtype.VIEWPOINT_INFRASTRUCTURE || subtype == ViewpointSubtype.VIEWPOINT_INDOOR) {
            return hasStrongTouristRelevance(candidate, notabilityScore, popularityScore);
        }
        return true;
    }

    public boolean isCemeteryOrBurialSite(ExternalActivityCandidate candidate) {
        return signals().matches(candidate, "cemetery", "graveyard", "burial", "crematorium", "funeral")
            || signals().containsCategoryTerm(candidate, "cemetery", "graveyard", "burial", "crematorium", "funeral")
            || signals().hasTagValue(candidate, "amenity", "grave_yard", "crematorium", "funeral_hall")
            || signals().hasTagValue(candidate, "landuse", "cemetery")
            || signals().hasTag(candidate, "cemetery")
            || signals().hasTag(candidate, "grave")
            || signals().hasTag(candidate, "grave_yard")
            || signals().hasTag(candidate, "funeral")
            || signals().hasTagValue(candidate, "historic", "cemetery")
            || signals().normalized(candidate.name).contains("friedhof")
            || signals().normalized(candidate.name).contains("cemetery")
            || signals().normalized(candidate.name).contains("graveyard");
    }

    public ShoppingSubtype shoppingSubtype(ExternalActivityCandidate candidate) {
        String name = signals().normalized(candidate.name);
        if (signals().matches(candidate, "commercial.shopping_mall")
            || signals().hasTagValue(candidate, "shop", "mall")
            || name.contains("shopping center")
            || name.contains("shopping centre")) {
            return ShoppingSubtype.SHOPPING_MALL;
        }
        if (signals().matches(candidate, "commercial.marketplace")
            || signals().hasTagValue(candidate, "amenity", "marketplace")
            || signals().hasTagValue(candidate, "shop", "market", "marketplace")
            || name.contains("market") || name.contains("mercato") || name.contains("bazaar")) {
            return ShoppingSubtype.MARKET;
        }
        if (signals().matches(candidate, "commercial.department_store")
            || signals().hasTagValue(candidate, "shop", "department_store")) {
            return ShoppingSubtype.DEPARTMENT_STORE;
        }
        if (signals().hasTagValue(candidate, "shop", "outlet") || name.contains("outlet")) {
            return ShoppingSubtype.OUTLET;
        }
        if (name.contains("galleria") || name.contains("arcade") || name.contains("passage")
            || name.contains("passagen") || name.contains("galerie commerciale")) {
            return ShoppingSubtype.SHOPPING_ARCADE;
        }
        if ((signals().hasTagValue(candidate, "highway", "pedestrian") || name.contains("shopping street")
            || name.contains("einkaufsstrasse") || name.contains("einkaufsstraÃŸe"))
            && candidate.nearbyShopDensity >= 6) {
            return ShoppingSubtype.SHOPPING_STREET;
        }
        if (candidate.nearbyShopDensity >= 10 && signals().matches(candidate, "commercial")) {
            return ShoppingSubtype.SHOPPING_DISTRICT;
        }
        if (isRetailStoreSignal(candidate)) {
            return ShoppingSubtype.SINGLE_RETAIL_STORE;
        }
        return ShoppingSubtype.OTHER;
    }

    public boolean isShoppingDestination(ExternalActivityCandidate candidate) {
        return Set.of(
            ShoppingSubtype.SHOPPING_MALL,
            ShoppingSubtype.MARKET,
            ShoppingSubtype.SHOPPING_STREET,
            ShoppingSubtype.SHOPPING_ARCADE,
            ShoppingSubtype.DEPARTMENT_STORE,
            ShoppingSubtype.OUTLET,
            ShoppingSubtype.SHOPPING_DISTRICT
        ).contains(shoppingSubtype(candidate));
    }

    public boolean isSingleRetailStore(ExternalActivityCandidate candidate) {
        return shoppingSubtype(candidate) == ShoppingSubtype.SINGLE_RETAIL_STORE;
    }

    public boolean canBeMainShoppingActivity(
        ExternalActivityCandidate candidate,
        double notabilityScore,
        double popularityScore
    ) {
        if (isShoppingDestination(candidate)) return true;
        boolean hasStrongReference = candidate.externalRefs.containsKey(ActivitySource.WIKIDATA)
            && candidate.externalRefs.containsKey(ActivitySource.WIKIPEDIA);
        return isSingleRetailStore(candidate)
            && hasStrongReference
            && (notabilityScore >= 0.75 || popularityScore >= 0.75);
    }

    public boolean isFountain(ExternalActivityCandidate candidate) {
        String name = signals().normalized(candidate.name);
        return signals().matches(candidate, "amenity.fountain", "tourism.attraction.fountain")
            || signals().hasTagValue(candidate, "amenity", "fountain")
            || signals().hasTagValue(candidate, "tourism", "fountain")
            || (signals().matches(candidate, "natural.water") && signals().containsAny(name, "fountain", "brunnen", "cascade", "kaskade"));
    }

    public boolean isCrematoriumOrFuneralSite(ExternalActivityCandidate candidate) {
        return signals().containsCategoryTerm(candidate, "crematorium", "funeral")
            || signals().hasTagValue(candidate, "amenity", "crematorium", "funeral_hall")
            || signals().hasTag(candidate, "funeral");
    }

    public boolean isMinorMonument(ExternalActivityCandidate candidate) {
        return signals().matches(candidate, "tourism.attraction.artwork", "tourism.sights.memorial", "historic.memorial")
            || signals().hasTagValue(candidate, "historic", "memorial", "monument")
            || signals().hasTag(candidate, "artwork_type")
            || signals().normalized(candidate.name).contains("statue")
            || signals().normalized(candidate.name).contains("denkmal");
    }

    boolean hasStrongIndependentNotability(ExternalActivityCandidate candidate) {
        return (candidate.externalRefs.containsKey(ActivitySource.WIKIDATA)
            && candidate.externalRefs.containsKey(ActivitySource.WIKIPEDIA)
            && ScoreNormalizer.logNormalize(candidate.wikipediaPageviews365d == null ? 0 : candidate.wikipediaPageviews365d, 250_000) >= 0.65)
            || candidate.wikidataSitelinksCount >= 120
            || candidate.isUnescoWorldHeritage;
    }

    private boolean hasStrongTouristRelevance(
        ExternalActivityCandidate candidate,
        double notabilityScore,
        double popularityScore
    ) {
        return notabilityScore >= 0.70
            || popularityScore >= 0.80
            || hasStrongIndependentNotability(candidate)
            || signals().sourceConsensusScore(candidate) >= 0.55;
    }

    private boolean isGolfCourse(ExternalActivityCandidate candidate) {
        return signals().matches(candidate, "sport.golf", "leisure.golf_course")
            || signals().hasTagValue(candidate, "leisure", "golf_course")
            || signals().hasTagValue(candidate, "sport", "golf");
    }

    private boolean isPrivateGarden(ExternalActivityCandidate candidate) {
        return signals().hasTagValue(candidate, "access", "private", "no")
            && (signals().matches(candidate, "leisure.park.garden") || signals().hasTagValue(candidate, "leisure", "garden"));
    }

    private boolean isSmallGreenInfrastructure(ExternalActivityCandidate candidate) {
        String name = signals().normalized(candidate.name);
        return name.contains("traffic island")
            || name.contains("verkehrsinsel")
            || name.contains("green strip")
            || name.contains("gruenstreifen")
            || name.contains("grÃ¼nstreifen")
            || name.contains("courtyard")
            || name.contains("innenhof")
            || signals().hasTagValue(candidate, "traffic_calming", "island")
            || signals().hasTagValue(candidate, "place", "islet")
            || signals().hasTagValue(candidate, "landuse", "grass")
            && candidate.geometryAreaM2 != null
            && candidate.geometryAreaM2 < 2_000;
    }

    private boolean isWeakSubPoiWithoutOwnRelevance(ExternalActivityCandidate candidate) {
        String name = signals().normalized(candidate.name);
        boolean weakSubPoiName = name.contains("erweiterungsteil")
            || name.contains("erweiterung")
            || name.contains("annex")
            || name.contains("annexe")
            || name.contains("extension")
            || name.contains("eingang")
            || name.contains("entrance")
            || name.contains("parkplatz")
            || name.contains("parking")
            || name.contains("teilbereich")
            || name.contains("section")
            || name.contains("wing");
        return weakSubPoiName && !hasStrongIndependentNotability(candidate);
    }

    private boolean isPlainAttractionWithoutNatureEvidence(ExternalActivityCandidate candidate) {
        boolean attraction = signals().matches(candidate, "tourism.attraction") || signals().hasTagValue(candidate, "tourism", "attraction");
        boolean explicitNature = viewpoints().hasNaturalContext(candidate)
            || signals().hasTagValue(candidate, "natural", "wood", "forest", "beach", "waterfall")
            || signals().hasTagValue(candidate, "leisure", "park", "garden", "nature_reserve")
            || signals().hasTagValue(candidate, "garden:type", "botanical", "botanic")
            || signals().hasTagValue(candidate, "garden", "botanical")
            || signals().hasTagValue(candidate, "botanical", "yes");
        return attraction && !explicitNature;
    }

    private boolean isRetailStoreSignal(ExternalActivityCandidate candidate) {
        return signals().matches(candidate,
            "shop", "commercial.clothing", "commercial.gift_and_souvenir",
            "commercial.fashion", "commercial.shoes", "commercial.jewelry"
        ) || signals().hasTag(candidate, "shop");
    }

    private PoiSignalMatcher signals() {
        return matcher == null ? new PoiSignalMatcher() : matcher;
    }

    private PoiViewpointClassifier viewpoints() {
        return viewpoints == null ? new PoiViewpointClassifier() : viewpoints;
    }
}
