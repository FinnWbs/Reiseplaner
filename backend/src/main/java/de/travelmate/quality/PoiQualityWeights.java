package de.travelmate.quality;

public final class PoiQualityWeights {
    public static final double PAGEVIEW_WEIGHT = 0.35;
    public static final double WIKIDATA_NOTABILITY_WEIGHT = 0.25;
    public static final double SOURCE_CONSENSUS_WEIGHT = 0.20;
    public static final double GEO_PROMINENCE_WEIGHT = 0.10;
    public static final double CATEGORY_PRIOR_WEIGHT = 0.10;

    public static final double FINAL_CATEGORY_FIT_WEIGHT = 0.30;
    public static final double FINAL_POPULARITY_WEIGHT = 0.25;
    public static final double FINAL_QUALITY_WEIGHT = 0.20;
    public static final double FINAL_ITINERARY_FIT_WEIGHT = 0.15;
    public static final double FINAL_DIVERSITY_WEIGHT = 0.10;

    public static final double MINOR_MONUMENT_NOTABILITY_THRESHOLD = 0.55;
    public static final double DEFAULT_PAGEVIEW_P95 = 250_000;
    public static final double DEFAULT_AREA_P95_M2 = 2_000_000;
    private PoiQualityWeights() {}
}
