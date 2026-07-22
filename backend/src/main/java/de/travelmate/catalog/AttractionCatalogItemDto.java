package de.travelmate.catalog;

import de.travelmate.interest.InterestType;
import java.util.List;

public record AttractionCatalogItemDto(
    String catalogId,
    String name,
    String city,
    String wikidataId,
    String wikipediaProject,
    String wikipediaTitle,
    InterestType primaryInterest,
    String category,
    Double latitude,
    Double longitude,
    int rank,
    String description,
    Double publicAttractionScore,
    Long pageviews,
    Integer sitelinkCount,
    String source,
    boolean alreadyPlanned,
    List<Integer> plannedDayNumbers
) {}
