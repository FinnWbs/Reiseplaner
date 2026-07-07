package de.travelmate.quality;

import de.travelmate.activity.ActivitySource;
import de.travelmate.datasource.ExternalActivityCandidate;
import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.EnumSet;
import java.util.Set;

@ApplicationScoped
public class PoiQualityEngine {
    @Inject
    PoiEligibilityService eligibility;

    @Inject
    PoiSignalMatcher matcher;

    @Inject
    PoiViewpointClassifier viewpoints;

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
        double score = signals().sourceConsensusScore(candidate);
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
        CanonicalCategory category = canonicalCategory(candidate);
        if (Set.of(CanonicalCategory.FOOD, CanonicalCategory.NIGHTLIFE, CanonicalCategory.SHOPPING).contains(category)) {
            return 0;
        }
        double areaWeight = category == CanonicalCategory.NATURE ? 0.75 : 0.35;
        return ScoreNormalizer.clamp01(areaWeight * areaScore);
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
        if (signals().hasTag(candidate, "cuisine")) {
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
        if (requestedInterest == InterestType.NATURE && rules().isFountain(candidate)) {
            reasons.add(QualityReasonCode.HARD_EXCLUDED_NATURE_FOUNTAIN);
            return "fountain_cannot_be_nature";
        }
        if (requestedInterest == InterestType.NATURE && isCemeteryOrBurialSite(candidate)) {
            reasons.add(rules().isCrematoriumOrFuneralSite(candidate)
                ? QualityReasonCode.BURIAL_SITE_EXCLUDED_FROM_NATURE
                : QualityReasonCode.CEMETERY_EXCLUDED_FROM_NATURE);
            return rules().isCrematoriumOrFuneralSite(candidate)
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
            && (isMinorMonument(candidate) || rules().isFountain(candidate))
            && Math.max(notabilityScore, popularityScore) < PoiQualityWeights.MINOR_MONUMENT_NOTABILITY_THRESHOLD) {
            reasons.add(QualityReasonCode.LOW_NOTABILITY_MINOR_MONUMENT);
            return "low_notability_minor_sight";
        }
        return null;
    }

    private CanonicalCategory canonicalCategory(ExternalActivityCandidate candidate) {
        if (signals().matches(candidate, "catering.restaurant", "catering.cafe", "catering.food_court")
            || signals().hasTagValue(candidate, "amenity", "restaurant", "cafe", "fast_food", "bar", "pub")) {
            if (signals().hasTagValue(candidate, "amenity", "bar", "pub") || signals().matches(candidate, "catering.bar", "catering.pub")) {
                return CanonicalCategory.NIGHTLIFE;
            }
            return CanonicalCategory.FOOD;
        }
        if (isShoppingDestination(candidate) || isSingleRetailStore(candidate) || signals().matches(candidate, "commercial", "shop")) {
            return CanonicalCategory.SHOPPING;
        }
        if (signals().matches(candidate, "entertainment.museum", "entertainment.culture.gallery", "entertainment.culture.theatre")
            || signals().hasTagValue(candidate, "tourism", "museum", "gallery")) {
            return CanonicalCategory.CULTURE;
        }
        if (signals().matches(candidate, "heritage", "tourism.sights.castle", "tourism.sights.place_of_worship", "historic.castle")
            || signals().hasTag(candidate, "heritage")) {
            return CanonicalCategory.HERITAGE;
        }
        if (canBeMainNatureActivity(candidate)) return CanonicalCategory.NATURE;
        if (isMinorMonument(candidate)) return CanonicalCategory.MONUMENT;
        if (signals().matches(candidate, "tourism.sights", "tourism.attraction")) return CanonicalCategory.LANDMARK;
        return CanonicalCategory.GENERIC;
    }

    private double categoryPriorScore(ExternalActivityCandidate candidate) {
        if (candidate.isUnescoWorldHeritage || signals().matches(candidate, "heritage.unesco")) return 1.00;
        if (isShoppingDestination(candidate)) return 0.75;
        if (isSingleRetailStore(candidate)) return 0.20;
        if (viewpointSubtype(candidate) == ViewpointSubtype.VIEWPOINT_NATURAL) return 0.75;
        if (isViewpoint(candidate)) return 0.35;
        if (signals().matches(candidate, "natural.protected_area", "leisure.park.nature_reserve")) return 0.85;
        if (signals().matches(candidate, "beach")) return 0.80;
        if (signals().matches(candidate, "entertainment.museum")) return 0.75;
        if (signals().matches(candidate, "tourism.sights")) return 0.65;
        if (signals().matches(candidate, "tourism.attraction")) return 0.55;
        if (signals().matches(candidate, "leisure.park")) return 0.55;
        if (signals().hasTagValue(candidate, "historic", "memorial", "monument")) return 0.25;
        if (signals().matches(candidate, "tourism.attraction.artwork") || signals().hasTag(candidate, "artwork_type")) return 0.20;
        if (rules().isFountain(candidate)) return 0.15;
        return 0.35;
    }

    public boolean hasPositiveNatureEvidence(ExternalActivityCandidate candidate) {
        return rules().hasPositiveNatureEvidence(candidate);
    }

    public boolean hasNatureHardExclusion(ExternalActivityCandidate candidate) {
        return rules().hasNatureHardExclusion(candidate);
    }

    public boolean canBeMainNatureActivity(ExternalActivityCandidate candidate) {
        return rules().canBeMainNatureActivity(candidate);
    }

    public boolean isViewpoint(ExternalActivityCandidate candidate) {
        return viewpointRules().isViewpoint(candidate);
    }

    public ViewpointSubtype viewpointSubtype(ExternalActivityCandidate candidate) {
        return viewpointRules().subtype(candidate);
    }

    public boolean hasNaturalViewpointContext(ExternalActivityCandidate candidate) {
        return viewpointRules().hasNaturalContext(candidate);
    }

    public boolean hasUrbanOrInfrastructureViewpointContext(ExternalActivityCandidate candidate) {
        return viewpointRules().hasUrbanOrInfrastructureContext(candidate);
    }

    public boolean canBeMainSightseeingActivity(
        ExternalActivityCandidate candidate,
        double notabilityScore,
        double popularityScore
    ) {
        return rules().canBeMainSightseeingActivity(candidate, notabilityScore, popularityScore);
    }

    public boolean isCemeteryOrBurialSite(ExternalActivityCandidate candidate) {
        return rules().isCemeteryOrBurialSite(candidate);
    }

    public ShoppingSubtype shoppingSubtype(ExternalActivityCandidate candidate) {
        return rules().shoppingSubtype(candidate);
    }

    public boolean isShoppingDestination(ExternalActivityCandidate candidate) {
        return rules().isShoppingDestination(candidate);
    }

    public boolean isSingleRetailStore(ExternalActivityCandidate candidate) {
        return rules().isSingleRetailStore(candidate);
    }

    public boolean canBeMainShoppingActivity(
        ExternalActivityCandidate candidate,
        double notabilityScore,
        double popularityScore
    ) {
        return rules().canBeMainShoppingActivity(candidate, notabilityScore, popularityScore);
    }

    boolean hasStrongIndependentNotability(ExternalActivityCandidate candidate) {
        return rules().hasStrongIndependentNotability(candidate);
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

    private boolean isMinorMonument(ExternalActivityCandidate candidate) {
        return rules().isMinorMonument(candidate);
    }

    private PoiEligibilityService rules() {
        return eligibility == null ? new PoiEligibilityService() : eligibility;
    }

    private PoiSignalMatcher signals() {
        return matcher == null ? new PoiSignalMatcher() : matcher;
    }

    private PoiViewpointClassifier viewpointRules() {
        return viewpoints == null ? new PoiViewpointClassifier() : viewpoints;
    }
}
