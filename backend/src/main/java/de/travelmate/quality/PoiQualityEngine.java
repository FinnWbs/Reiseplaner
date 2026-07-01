package de.travelmate.quality;

import de.travelmate.activity.ActivitySource;
import de.travelmate.datasource.ExternalActivityCandidate;
import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class PoiQualityEngine {
    public PoiQualityEvaluation evaluate(ExternalActivityCandidate candidate, InterestType requestedInterest) {
        EnumSet<QualityReasonCode> reasons = EnumSet.noneOf(QualityReasonCode.class);
        if (candidate.name == null || candidate.name.isBlank()) {
            reasons.add(QualityReasonCode.HARD_EXCLUDED_MISSING_NAME);
            return excluded(CanonicalCategory.GENERIC, "missing_name", reasons);
        }
        if (candidate.latitude == null || candidate.longitude == null) {
            reasons.add(QualityReasonCode.HARD_EXCLUDED_MISSING_COORDINATES);
            return excluded(CanonicalCategory.GENERIC, "missing_coordinates", reasons);
        }

        CanonicalCategory canonicalCategory = canonicalCategory(candidate);
        double notabilityScore = wikidataNotabilityScore(candidate, reasons);
        double popularityScore = popularityScore(candidate, notabilityScore, reasons);
        double qualityScore = qualityScore(candidate, reasons);
        double categoryFitScore = categoryFitScore(candidate, requestedInterest, canonicalCategory, notabilityScore, popularityScore, reasons);
        double itineraryFitScore = itineraryFitScore(candidate, requestedInterest);
        double diversityScore = 1.0;
        double penalties = penalties(candidate, requestedInterest, categoryFitScore, reasons);

        String hardExclusion = hardExclusion(candidate, requestedInterest, notabilityScore, popularityScore, canonicalCategory, reasons);
        boolean hardExcluded = hardExclusion != null;
        double finalScore = hardExcluded ? 0 : ScoreNormalizer.clamp01(
            PoiQualityWeights.FINAL_CATEGORY_FIT_WEIGHT * categoryFitScore
                + PoiQualityWeights.FINAL_POPULARITY_WEIGHT * popularityScore
                + PoiQualityWeights.FINAL_QUALITY_WEIGHT * qualityScore
                + PoiQualityWeights.FINAL_ITINERARY_FIT_WEIGHT * itineraryFitScore
                + PoiQualityWeights.FINAL_DIVERSITY_WEIGHT * diversityScore
                - penalties
        );

        return new PoiQualityEvaluation(
            canonicalCategory,
            popularityScore,
            notabilityScore,
            qualityScore,
            categoryFitScore,
            itineraryFitScore,
            diversityScore,
            finalScore,
            penalties,
            hardExcluded,
            hardExclusion,
            Set.copyOf(reasons)
        );
    }

    public double planningScore(double storedFinalScore, double categoryFitScore, double itineraryFitScore, double diversityScore) {
        return ScoreNormalizer.clamp01(
            PoiQualityWeights.FINAL_CATEGORY_FIT_WEIGHT * categoryFitScore
                + 0.45 * storedFinalScore
                + PoiQualityWeights.FINAL_ITINERARY_FIT_WEIGHT * itineraryFitScore
                + PoiQualityWeights.FINAL_DIVERSITY_WEIGHT * diversityScore
        );
    }

    private PoiQualityEvaluation excluded(
        CanonicalCategory category,
        String reason,
        Set<QualityReasonCode> reasons
    ) {
        return new PoiQualityEvaluation(category, 0, 0, 0, 0, 0, 0, 0, 1, true, reason, Set.copyOf(reasons));
    }

    private double popularityScore(
        ExternalActivityCandidate candidate,
        double notabilityScore,
        Set<QualityReasonCode> reasons
    ) {
        double pageviewScore = ScoreNormalizer.logNormalize(
            candidate.wikipediaPageviews365d == null ? 0 : candidate.wikipediaPageviews365d,
            PoiQualityWeights.DEFAULT_PAGEVIEW_P95
        );
        double sourceConsensus = sourceConsensusScore(candidate, reasons);
        double geoProminence = geoProminenceScore(candidate, reasons);
        double categoryPrior = categoryPriorScore(candidate);
        return ScoreNormalizer.clamp01(
            PoiQualityWeights.PAGEVIEW_WEIGHT * pageviewScore
                + PoiQualityWeights.WIKIDATA_NOTABILITY_WEIGHT * notabilityScore
                + PoiQualityWeights.SOURCE_CONSENSUS_WEIGHT * sourceConsensus
                + PoiQualityWeights.GEO_PROMINENCE_WEIGHT * geoProminence
                + PoiQualityWeights.CATEGORY_PRIOR_WEIGHT * categoryPrior
        );
    }

    private double sourceConsensusScore(ExternalActivityCandidate candidate, Set<QualityReasonCode> reasons) {
        Map<ActivitySource, Double> weights = new EnumMap<>(ActivitySource.class);
        weights.put(ActivitySource.GEOAPIFY, 0.20);
        weights.put(ActivitySource.OPEN_STREET_MAP, 0.15);
        weights.put(ActivitySource.WIKIDATA, 0.20);
        weights.put(ActivitySource.WIKIPEDIA, 0.20);
        double score = candidate.externalRefs.entrySet().stream()
            .mapToDouble(entry -> weights.getOrDefault(entry.getKey(), 0.0))
            .sum();
        if (candidate.website != null && !candidate.website.isBlank()) {
            score += 0.05;
        }
        if (score >= 0.55) {
            reasons.add(QualityReasonCode.HIGH_SOURCE_CONSENSUS);
        }
        return ScoreNormalizer.clamp01(score);
    }

    private double wikidataNotabilityScore(ExternalActivityCandidate candidate, Set<QualityReasonCode> reasons) {
        double score = 0;
        if (candidate.hasWikidata || candidate.externalRefs.containsKey(ActivitySource.WIKIDATA)) {
            score += 0.15;
            reasons.add(QualityReasonCode.HAS_WIKIDATA);
        }
        if (candidate.externalRefs.containsKey(ActivitySource.WIKIPEDIA)) {
            score += 0.25;
            reasons.add(QualityReasonCode.HAS_WIKIPEDIA);
        }
        score += 0.25 * ScoreNormalizer.logNormalize(candidate.wikidataSitelinksCount, 80);
        if (candidate.hasImage) {
            score += 0.05;
            reasons.add(QualityReasonCode.HAS_IMAGE);
        }
        if (candidate.website != null && !candidate.website.isBlank()) {
            score += 0.05;
            reasons.add(QualityReasonCode.HAS_OFFICIAL_WEBSITE);
        }
        if (candidate.hasHeritageStatus) {
            score += 0.15;
            reasons.add(QualityReasonCode.HAS_HERITAGE_STATUS);
        }
        if (candidate.isUnescoWorldHeritage) {
            score += 0.20;
            reasons.add(QualityReasonCode.UNESCO_WORLD_HERITAGE);
        }
        return ScoreNormalizer.clamp01(score);
    }

    private double geoProminenceScore(ExternalActivityCandidate candidate, Set<QualityReasonCode> reasons) {
        double areaScore = ScoreNormalizer.logNormalize(
            candidate.geometryAreaM2 == null ? 0 : candidate.geometryAreaM2,
            PoiQualityWeights.DEFAULT_AREA_P95_M2
        );
        if (areaScore >= 0.45) {
            reasons.add(QualityReasonCode.LARGE_GEO_AREA);
        }
        double distanceScore = candidate.distanceToCenterKm == null
            ? 0
            : ScoreNormalizer.clamp01(1 - candidate.distanceToCenterKm / PoiQualityWeights.CITY_CENTER_PROMINENCE_KM);
        if (distanceScore >= 0.65) {
            reasons.add(QualityReasonCode.NEAR_CITY_CENTER);
        }
        CanonicalCategory category = canonicalCategory(candidate);
        double areaWeight = category == CanonicalCategory.NATURE ? 0.75 : 0.35;
        return ScoreNormalizer.clamp01(areaWeight * areaScore + (1 - areaWeight) * distanceScore);
    }

    private double qualityScore(ExternalActivityCandidate candidate, Set<QualityReasonCode> reasons) {
        double score = 0.15;
        if (candidate.latitude != null && candidate.longitude != null) {
            score += 0.15;
            reasons.add(QualityReasonCode.HAS_COORDINATES);
        }
        if (candidate.address != null && !candidate.address.isBlank()) {
            score += 0.10;
            reasons.add(QualityReasonCode.HAS_ADDRESS);
        }
        if (candidate.openingHours != null && !candidate.openingHours.isBlank()) {
            score += 0.15;
            reasons.add(QualityReasonCode.HAS_OPENING_HOURS);
        }
        if (candidate.website != null && !candidate.website.isBlank()) score += 0.15;
        if (candidate.description != null && !candidate.description.isBlank()) score += 0.10;
        if (candidate.rawTags.containsKey("cuisine")) {
            score += 0.10;
            reasons.add(QualityReasonCode.HAS_CUISINE);
        }
        score += 0.20 * sourceConsensusScore(candidate, reasons);
        return ScoreNormalizer.clamp01(score);
    }

    private double categoryFitScore(
        ExternalActivityCandidate candidate,
        InterestType requestedInterest,
        CanonicalCategory canonicalCategory,
        double notabilityScore,
        double popularityScore,
        Set<QualityReasonCode> reasons
    ) {
        if (requestedInterest == null) return 0;
        if (requestedInterest == InterestType.NATURE && !canBeMainNatureActivity(candidate)) return 0;
        if (requestedInterest == InterestType.SIGHTSEEING
            && !canBeMainSightseeingActivity(candidate, notabilityScore, popularityScore)) {
            reasons.add(QualityReasonCode.SIGHTSEEING_VIEWPOINT_REQUIRES_NOTABILITY);
            return 0;
        }
        if (requestedInterest == InterestType.SHOPPING && !canBeMainShoppingActivity(candidate, notabilityScore, popularityScore)) {
            return 0;
        }
        if (requestedInterest == InterestType.NATURE && isViewpoint(candidate) && canBeMainNatureActivity(candidate)) {
            reasons.add(QualityReasonCode.NATURAL_VIEWPOINT_ALLOWED);
        }
        if (requestedInterest == canonicalCategory.interestType()) {
            reasons.add(QualityReasonCode.MATCHES_USER_INTEREST);
            return 1;
        }
        if (requestedInterest == InterestType.SIGHTSEEING
            && Set.of(CanonicalCategory.HERITAGE, CanonicalCategory.CULTURE, CanonicalCategory.MONUMENT).contains(canonicalCategory)
            && (notabilityScore >= 0.55 || popularityScore >= 0.55)) {
            reasons.add(QualityReasonCode.MATCHES_USER_INTEREST);
            return 0.75;
        }
        if (requestedInterest == InterestType.CULTURE && canonicalCategory == CanonicalCategory.HERITAGE) {
            reasons.add(QualityReasonCode.MATCHES_USER_INTEREST);
            return 0.9;
        }
        return 0;
    }

    private double itineraryFitScore(ExternalActivityCandidate candidate, InterestType requestedInterest) {
        if (requestedInterest == InterestType.NIGHTLIFE && candidate.openingHours == null) return 0.45;
        if (candidate.distanceToCenterKm == null) return 0.65;
        return ScoreNormalizer.clamp01(1 - candidate.distanceToCenterKm / 25);
    }

    private double penalties(
        ExternalActivityCandidate candidate,
        InterestType requestedInterest,
        double categoryFitScore,
        Set<QualityReasonCode> reasons
    ) {
        double penalty = 0;
        if (categoryFitScore <= 0) {
            penalty += 0.35;
            reasons.add(QualityReasonCode.CATEGORY_MISMATCH);
        }
        if (candidate.distanceToCenterKm != null && candidate.distanceToCenterKm > 20) {
            penalty += 0.10;
        }
        if (requestedInterest == InterestType.FOOD && candidate.rating == null) {
            penalty += 0.05;
        }
        return ScoreNormalizer.clamp01(penalty);
    }

    private String hardExclusion(
        ExternalActivityCandidate candidate,
        InterestType requestedInterest,
        double notabilityScore,
        double popularityScore,
        CanonicalCategory canonicalCategory,
        Set<QualityReasonCode> reasons
    ) {
        if (requestedInterest == InterestType.NATURE && isFountain(candidate)) {
            reasons.add(QualityReasonCode.HARD_EXCLUDED_NATURE_FOUNTAIN);
            return "fountain_cannot_be_nature";
        }
        if (requestedInterest == InterestType.NATURE && isCemeteryOrBurialSite(candidate)) {
            reasons.add(isCrematoriumOrFuneralSite(candidate)
                ? QualityReasonCode.BURIAL_SITE_EXCLUDED_FROM_NATURE
                : QualityReasonCode.CEMETERY_EXCLUDED_FROM_NATURE);
            return isCrematoriumOrFuneralSite(candidate)
                ? "burial_site_not_nature_activity"
                : "cemetery_not_nature_activity";
        }
        if (requestedInterest == InterestType.NATURE && isViewpoint(candidate) && !canBeMainNatureActivity(candidate)) {
            addViewpointNatureReason(candidate, reasons);
            return "viewpoint_not_nature_context";
        }
        if (requestedInterest == InterestType.NATURE && !canBeMainNatureActivity(candidate)) {
            reasons.add(QualityReasonCode.HARD_EXCLUDED_NATURE_NON_MAIN);
            return "not_main_nature_activity";
        }
        if (requestedInterest == InterestType.NATURE && canonicalCategory != CanonicalCategory.NATURE) {
            reasons.add(QualityReasonCode.HARD_EXCLUDED_NATURE_NON_NATURE);
            return "non_nature_category";
        }
        if (requestedInterest == InterestType.SHOPPING
            && !canBeMainShoppingActivity(candidate, notabilityScore, popularityScore)) {
            reasons.add(QualityReasonCode.HARD_EXCLUDED_SHOPPING_NON_DESTINATION);
            return "not_main_shopping_destination";
        }
        if (requestedInterest == InterestType.SIGHTSEEING
            && !canBeMainSightseeingActivity(candidate, notabilityScore, popularityScore)) {
            reasons.add(QualityReasonCode.INFRASTRUCTURE_VIEWPOINT_EXCLUDED_FROM_SIGHTSEEING);
            return "viewpoint_requires_notability_for_sightseeing";
        }
        if (requestedInterest == InterestType.SIGHTSEEING
            && (isMinorMonument(candidate) || isFountain(candidate))
            && Math.max(notabilityScore, popularityScore) < PoiQualityWeights.MINOR_MONUMENT_NOTABILITY_THRESHOLD) {
            reasons.add(QualityReasonCode.LOW_NOTABILITY_MINOR_MONUMENT);
            return "low_notability_minor_sight";
        }
        return null;
    }

    private CanonicalCategory canonicalCategory(ExternalActivityCandidate candidate) {
        if (matches(candidate, "catering.restaurant", "catering.cafe", "catering.food_court")
            || hasTagValue(candidate, "amenity", "restaurant", "cafe", "fast_food", "bar", "pub")) {
            if (hasTagValue(candidate, "amenity", "bar", "pub") || matches(candidate, "catering.bar", "catering.pub")) {
                return CanonicalCategory.NIGHTLIFE;
            }
            return CanonicalCategory.FOOD;
        }
        if (isShoppingDestination(candidate) || isSingleRetailStore(candidate) || matches(candidate, "commercial", "shop")) {
            return CanonicalCategory.SHOPPING;
        }
        if (matches(candidate, "entertainment.museum", "entertainment.culture.gallery", "entertainment.culture.theatre")
            || hasTagValue(candidate, "tourism", "museum", "gallery")) {
            return CanonicalCategory.CULTURE;
        }
        if (matches(candidate, "heritage", "tourism.sights.castle", "tourism.sights.place_of_worship", "historic.castle")
            || hasTag(candidate, "heritage")) {
            return CanonicalCategory.HERITAGE;
        }
        if (canBeMainNatureActivity(candidate)) return CanonicalCategory.NATURE;
        if (isMinorMonument(candidate)) return CanonicalCategory.MONUMENT;
        if (matches(candidate, "tourism.sights", "tourism.attraction")) return CanonicalCategory.LANDMARK;
        return CanonicalCategory.GENERIC;
    }

    private double categoryPriorScore(ExternalActivityCandidate candidate) {
        if (candidate.isUnescoWorldHeritage || matches(candidate, "heritage.unesco")) return 1.00;
        if (isShoppingDestination(candidate)) return 0.75;
        if (isSingleRetailStore(candidate)) return 0.20;
        if (viewpointSubtype(candidate) == ViewpointSubtype.VIEWPOINT_NATURAL) return 0.75;
        if (isViewpoint(candidate)) return 0.35;
        if (matches(candidate, "natural.protected_area", "leisure.park.nature_reserve")) return 0.85;
        if (matches(candidate, "beach")) return 0.80;
        if (matches(candidate, "entertainment.museum")) return 0.75;
        if (matches(candidate, "tourism.sights")) return 0.65;
        if (matches(candidate, "tourism.attraction")) return 0.55;
        if (matches(candidate, "leisure.park")) return 0.55;
        if (hasTagValue(candidate, "historic", "memorial", "monument")) return 0.25;
        if (matches(candidate, "tourism.attraction.artwork") || hasTag(candidate, "artwork_type")) return 0.20;
        if (isFountain(candidate)) return 0.15;
        return 0.35;
    }

    public boolean hasPositiveNatureEvidence(ExternalActivityCandidate candidate) {
        if (isViewpoint(candidate)) {
            return hasNaturalViewpointContext(candidate);
        }
        return matches(candidate,
            "leisure.park", "leisure.park.garden", "leisure.park.nature_reserve",
            "national_park", "natural.protected_area", "natural.forest", "beach",
            "natural.mountain", "natural.water", "natural.waterfall", "natural.wood"
        ) || hasTagValue(candidate, "leisure", "park", "garden", "nature_reserve")
            || hasTagValue(candidate, "natural", "wood", "forest", "beach", "waterfall")
            || hasTagValue(candidate, "natural", "peak", "cliff", "hill", "mountain_range", "water")
            || hasTagValue(candidate, "boundary", "national_park")
            || hasTag(candidate, "protected_area")
            || hasTagValue(candidate, "garden:type", "botanical")
            || hasTagValue(candidate, "garden:type", "botanic")
            || hasTagValue(candidate, "garden", "botanical")
            || hasTagValue(candidate, "botanical", "yes")
            || hasTagValue(candidate, "route", "hiking");
    }

    public boolean hasNatureHardExclusion(ExternalActivityCandidate candidate) {
        return isFountain(candidate)
            || isCemeteryOrBurialSite(candidate)
            || hasUrbanOrInfrastructureViewpointContext(candidate)
            || isGolfCourse(candidate)
            || isPrivateGarden(candidate)
            || isSmallGreenInfrastructure(candidate)
            || isWeakSubPoiWithoutOwnRelevance(candidate)
            || isShoppingDestination(candidate)
            || isSingleRetailStore(candidate)
            || matches(candidate,
                "commercial", "shop", "catering", "entertainment.museum", "entertainment.culture.gallery",
                "tourism.attraction.artwork", "tourism.sights.memorial", "historic.memorial"
            )
            || hasTagValue(candidate, "amenity", "restaurant", "cafe", "fast_food", "bar", "pub")
            || hasTagValue(candidate, "tourism", "museum", "gallery", "artwork")
            || hasTagValue(candidate, "historic", "memorial", "monument")
            || hasTag(candidate, "man_made")
            || hasTagValue(candidate, "building", "yes", "retail", "commercial", "office")
            || hasTagValue(candidate, "office", "yes")
            || hasTagValue(candidate, "indoor", "yes")
            || isPlainAttractionWithoutNatureEvidence(candidate);
    }

    public boolean canBeMainNatureActivity(ExternalActivityCandidate candidate) {
        if (isViewpoint(candidate)) {
            return hasNaturalViewpointContext(candidate) && !hasUrbanOrInfrastructureViewpointContext(candidate);
        }
        return hasPositiveNatureEvidence(candidate) && !hasNatureHardExclusion(candidate);
    }

    public boolean isViewpoint(ExternalActivityCandidate candidate) {
        String name = normalized(candidate.name);
        return matches(candidate, "tourism.attraction.viewpoint", "tourism.sights.viewpoint")
            || hasTagValue(candidate, "tourism", "viewpoint")
            || name.contains("viewpoint")
            || name.contains("aussichtspunkt")
            || name.contains("observation deck")
            || name.contains("observation tower")
            || name.contains("viewing platform")
            || name.contains("belvedere");
    }

    public ViewpointSubtype viewpointSubtype(ExternalActivityCandidate candidate) {
        if (!isViewpoint(candidate)) return ViewpointSubtype.NONE;
        if (hasInfrastructureViewpointContext(candidate)) return ViewpointSubtype.VIEWPOINT_INFRASTRUCTURE;
        if (hasIndoorViewpointContext(candidate)) return ViewpointSubtype.VIEWPOINT_INDOOR;
        if (hasNaturalViewpointContext(candidate)) return ViewpointSubtype.VIEWPOINT_NATURAL;
        return ViewpointSubtype.VIEWPOINT_URBAN;
    }

    public boolean hasNaturalViewpointContext(ExternalActivityCandidate candidate) {
        return matches(candidate,
            "leisure.park", "leisure.park.garden", "leisure.park.nature_reserve",
            "national_park", "natural.protected_area", "natural.forest", "natural.wood",
            "natural.mountain", "natural.water", "natural.waterfall", "beach"
        ) || hasTagValue(candidate, "leisure", "park", "garden", "nature_reserve")
            || hasTagValue(candidate, "natural",
                "peak", "cliff", "hill", "mountain", "mountain_range", "water", "wood", "forest", "beach", "waterfall"
            )
            || hasTagValue(candidate, "boundary", "national_park")
            || hasTag(candidate, "protected_area")
            || hasTagValue(candidate, "route", "hiking")
            || hasTagValue(candidate, "garden:type", "botanical", "botanic")
            || hasTagValue(candidate, "garden", "botanical")
            || hasTagValue(candidate, "botanical", "yes");
    }

    public boolean hasUrbanOrInfrastructureViewpointContext(ExternalActivityCandidate candidate) {
        return isViewpoint(candidate) && (hasInfrastructureViewpointContext(candidate) || hasIndoorViewpointContext(candidate));
    }

    public boolean canBeMainSightseeingActivity(
        ExternalActivityCandidate candidate,
        double notabilityScore,
        double popularityScore
    ) {
        if (!isViewpoint(candidate)) return true;
        ViewpointSubtype subtype = viewpointSubtype(candidate);
        if (subtype == ViewpointSubtype.VIEWPOINT_NATURAL) return true;
        if (subtype == ViewpointSubtype.VIEWPOINT_INFRASTRUCTURE || subtype == ViewpointSubtype.VIEWPOINT_INDOOR) {
            return hasStrongTouristRelevance(candidate, notabilityScore, popularityScore);
        }
        return true;
    }

    public boolean isCemeteryOrBurialSite(ExternalActivityCandidate candidate) {
        return matches(candidate, "cemetery", "graveyard", "burial", "crematorium", "funeral")
            || containsCategoryTerm(candidate, "cemetery", "graveyard", "burial", "crematorium", "funeral")
            || hasTagValue(candidate, "amenity", "grave_yard", "crematorium", "funeral_hall")
            || hasTagValue(candidate, "landuse", "cemetery")
            || hasTag(candidate, "cemetery")
            || hasTag(candidate, "grave")
            || hasTag(candidate, "grave_yard")
            || hasTag(candidate, "funeral")
            || hasTagValue(candidate, "historic", "cemetery")
            || normalized(candidate.name).contains("friedhof")
            || normalized(candidate.name).contains("cemetery")
            || normalized(candidate.name).contains("graveyard");
    }

    public ShoppingSubtype shoppingSubtype(ExternalActivityCandidate candidate) {
        String name = normalized(candidate.name);
        if (matches(candidate, "commercial.shopping_mall")
            || hasTagValue(candidate, "shop", "mall")
            || name.contains("shopping center")
            || name.contains("shopping centre")) {
            return ShoppingSubtype.SHOPPING_MALL;
        }
        if (matches(candidate, "commercial.marketplace")
            || hasTagValue(candidate, "amenity", "marketplace")
            || hasTagValue(candidate, "shop", "market", "marketplace")
            || name.contains("market") || name.contains("mercato") || name.contains("bazaar")) {
            return ShoppingSubtype.MARKET;
        }
        if (matches(candidate, "commercial.department_store")
            || hasTagValue(candidate, "shop", "department_store")) {
            return ShoppingSubtype.DEPARTMENT_STORE;
        }
        if (hasTagValue(candidate, "shop", "outlet") || name.contains("outlet")) {
            return ShoppingSubtype.OUTLET;
        }
        if (name.contains("galleria") || name.contains("arcade") || name.contains("passage")
            || name.contains("passagen") || name.contains("galerie commerciale")) {
            return ShoppingSubtype.SHOPPING_ARCADE;
        }
        if ((hasTagValue(candidate, "highway", "pedestrian") || name.contains("shopping street")
            || name.contains("einkaufsstrasse") || name.contains("einkaufsstraße"))
            && candidate.nearbyShopDensity >= 6) {
            return ShoppingSubtype.SHOPPING_STREET;
        }
        if (candidate.nearbyShopDensity >= 10 && matches(candidate, "commercial")) {
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

    private void addViewpointNatureReason(ExternalActivityCandidate candidate, Set<QualityReasonCode> reasons) {
        ViewpointSubtype subtype = viewpointSubtype(candidate);
        if (subtype == ViewpointSubtype.VIEWPOINT_INFRASTRUCTURE) {
            reasons.add(QualityReasonCode.INFRASTRUCTURE_VIEWPOINT_EXCLUDED_FROM_NATURE);
        } else if (subtype == ViewpointSubtype.VIEWPOINT_INDOOR) {
            reasons.add(QualityReasonCode.INDOOR_VIEWPOINT_EXCLUDED_FROM_NATURE);
        } else if (subtype == ViewpointSubtype.VIEWPOINT_URBAN) {
            reasons.add(QualityReasonCode.URBAN_VIEWPOINT_EXCLUDED_FROM_NATURE);
        }
        if (!hasNaturalViewpointContext(candidate)) {
            reasons.add(QualityReasonCode.VIEWPOINT_REQUIRES_NATURE_CONTEXT);
            reasons.add(QualityReasonCode.VIEWPOINT_NOT_NATURE_CONTEXT);
        }
    }

    private boolean hasInfrastructureViewpointContext(ExternalActivityCandidate candidate) {
        String name = normalized(candidate.name);
        return containsAny(name,
            "railway", "train", "train tracks", "station", "platform", "subway", "metro",
            "airport", "terminal", "parking", "parking deck", "highway", "road infrastructure",
            "industrial", "port", "harbor", "harbour"
        )
            || hasTag(candidate, "railway")
            || hasTag(candidate, "public_transport")
            || hasTag(candidate, "aeroway")
            || hasTag(candidate, "platform")
            || hasTag(candidate, "subway")
            || hasTag(candidate, "rail")
            || hasTag(candidate, "terminal")
            || hasTagValue(candidate, "amenity", "parking")
            || hasTag(candidate, "parking")
            || hasTag(candidate, "highway")
            || hasTagValue(candidate, "bridge", "yes")
            || hasTagValue(candidate, "man_made", "bridge");
    }

    private boolean hasIndoorViewpointContext(ExternalActivityCandidate candidate) {
        String name = normalized(candidate.name);
        return containsAny(name,
            "building", "tower", "skyscraper", "observation deck", "rooftop", "roof terrace",
            "terrace", "floor", "mall", "shopping center", "shopping centre", "department store",
            "office", "hotel rooftop", "restaurant viewpoint", "bar viewpoint", "museum viewpoint", "deck"
        )
            || name.matches(".*\\b\\d{1,2}f\\b.*")
            || hasTag(candidate, "level")
            || hasTag(candidate, "addr:floor")
            || hasTag(candidate, "building")
            || hasTagValue(candidate, "indoor", "yes")
            || hasTag(candidate, "office")
            || hasTag(candidate, "shop")
            || hasTagValue(candidate, "amenity", "restaurant", "cafe", "fast_food", "bar", "pub")
            || hasTagValue(candidate, "tourism", "museum", "gallery")
            || matches(candidate, "commercial", "shop", "catering", "entertainment.museum", "entertainment.culture.gallery");
    }

    private boolean hasStrongTouristRelevance(
        ExternalActivityCandidate candidate,
        double notabilityScore,
        double popularityScore
    ) {
        return notabilityScore >= 0.70
            || popularityScore >= 0.80
            || hasStrongIndependentNotability(candidate)
            || rawSourceConsensusScore(candidate) >= 0.55;
    }

    private boolean isFountain(ExternalActivityCandidate candidate) {
        String name = normalized(candidate.name);
        return matches(candidate, "amenity.fountain", "tourism.attraction.fountain")
            || hasTagValue(candidate, "amenity", "fountain")
            || hasTagValue(candidate, "tourism", "fountain")
            || (matches(candidate, "natural.water") && containsAny(name, "fountain", "brunnen", "cascade", "kaskade"));
    }

    private boolean isCrematoriumOrFuneralSite(ExternalActivityCandidate candidate) {
        return containsCategoryTerm(candidate, "crematorium", "funeral")
            || hasTagValue(candidate, "amenity", "crematorium", "funeral_hall")
            || hasTag(candidate, "funeral");
    }

    private boolean isGolfCourse(ExternalActivityCandidate candidate) {
        return matches(candidate, "sport.golf", "leisure.golf_course")
            || hasTagValue(candidate, "leisure", "golf_course")
            || hasTagValue(candidate, "sport", "golf");
    }

    private boolean isPrivateGarden(ExternalActivityCandidate candidate) {
        return hasTagValue(candidate, "access", "private", "no")
            && (matches(candidate, "leisure.park.garden") || hasTagValue(candidate, "leisure", "garden"));
    }

    private boolean isSmallGreenInfrastructure(ExternalActivityCandidate candidate) {
        String name = normalized(candidate.name);
        return name.contains("traffic island")
            || name.contains("verkehrsinsel")
            || name.contains("green strip")
            || name.contains("gruenstreifen")
            || name.contains("grünstreifen")
            || name.contains("courtyard")
            || name.contains("innenhof")
            || hasTagValue(candidate, "traffic_calming", "island")
            || hasTagValue(candidate, "place", "islet")
            || hasTagValue(candidate, "landuse", "grass")
            && candidate.geometryAreaM2 != null
            && candidate.geometryAreaM2 < 2_000;
    }

    private boolean isWeakSubPoiWithoutOwnRelevance(ExternalActivityCandidate candidate) {
        String name = normalized(candidate.name);
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

    private boolean isMinorMonument(ExternalActivityCandidate candidate) {
        return matches(candidate, "tourism.attraction.artwork", "tourism.sights.memorial", "historic.memorial")
            || hasTagValue(candidate, "historic", "memorial", "monument")
            || hasTag(candidate, "artwork_type")
            || normalized(candidate.name).contains("statue")
            || normalized(candidate.name).contains("denkmal");
    }

    private boolean isPlainAttractionWithoutNatureEvidence(ExternalActivityCandidate candidate) {
        boolean attraction = matches(candidate, "tourism.attraction") || hasTagValue(candidate, "tourism", "attraction");
        boolean explicitNature = hasNaturalViewpointContext(candidate)
            || hasTagValue(candidate, "natural", "wood", "forest", "beach", "waterfall")
            || hasTagValue(candidate, "leisure", "park", "garden", "nature_reserve")
            || hasTagValue(candidate, "garden:type", "botanical", "botanic")
            || hasTagValue(candidate, "garden", "botanical")
            || hasTagValue(candidate, "botanical", "yes");
        return attraction && !explicitNature;
    }

    private boolean isRetailStoreSignal(ExternalActivityCandidate candidate) {
        return matches(candidate,
            "shop", "commercial.clothing", "commercial.gift_and_souvenir",
            "commercial.fashion", "commercial.shoes", "commercial.jewelry"
        ) || hasTag(candidate, "shop");
    }

    boolean hasStrongIndependentNotability(ExternalActivityCandidate candidate) {
        return (candidate.externalRefs.containsKey(ActivitySource.WIKIDATA)
            && candidate.externalRefs.containsKey(ActivitySource.WIKIPEDIA)
            && ScoreNormalizer.logNormalize(candidate.wikipediaPageviews365d == null ? 0 : candidate.wikipediaPageviews365d, 250_000) >= 0.65)
            || candidate.wikidataSitelinksCount >= 120
            || candidate.isUnescoWorldHeritage;
    }

    private boolean containsCategoryTerm(ExternalActivityCandidate candidate, String... terms) {
        for (String category : candidate.rawCategories) {
            String normalizedCategory = normalized(category);
            for (String term : terms) {
                if (normalizedCategory.contains(normalized(term))) return true;
            }
        }
        return false;
    }

    private boolean matches(ExternalActivityCandidate candidate, String... prefixes) {
        for (String category : candidate.rawCategories) {
            String normalizedCategory = category.toLowerCase(Locale.ROOT);
            for (String prefix : prefixes) {
                if (normalizedCategory.equals(prefix) || normalizedCategory.startsWith(prefix + ".")) return true;
            }
        }
        return false;
    }

    private boolean hasTag(ExternalActivityCandidate candidate, String key) {
        return candidate.rawTags.containsKey(key);
    }

    private boolean hasTagValue(ExternalActivityCandidate candidate, String key, String... acceptedValues) {
        String value = candidate.rawTags.get(key);
        if (value == null) return false;
        String normalizedValue = normalized(value);
        for (String accepted : acceptedValues) {
            if (normalizedValue.equals(normalized(accepted))) return true;
        }
        return false;
    }

    private double rawSourceConsensusScore(ExternalActivityCandidate candidate) {
        double score = 0;
        if (candidate.externalRefs.containsKey(ActivitySource.GEOAPIFY)) score += 0.20;
        if (candidate.externalRefs.containsKey(ActivitySource.OPEN_STREET_MAP)) score += 0.15;
        if (candidate.externalRefs.containsKey(ActivitySource.WIKIDATA)) score += 0.20;
        if (candidate.externalRefs.containsKey(ActivitySource.WIKIPEDIA)) score += 0.20;
        if (candidate.website != null && !candidate.website.isBlank()) score += 0.05;
        return ScoreNormalizer.clamp01(score);
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }

    private String normalized(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
