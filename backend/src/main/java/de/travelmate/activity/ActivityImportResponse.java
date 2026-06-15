package de.travelmate.activity;

import java.util.List;

public record ActivityImportResponse(
    String city,
    int createdCount,
    int updatedCount,
    int skippedCount,
    List<ActivityDto> activities,
    List<String> warnings
) {}
