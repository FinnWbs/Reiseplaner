package de.travelmate.catalog;

import java.util.List;

public record AttractionCatalogResponse(
    boolean supported,
    String message,
    List<AttractionCatalogItemDto> items
) {}
