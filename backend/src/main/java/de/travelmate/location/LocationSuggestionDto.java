package de.travelmate.location;

public record LocationSuggestionDto(
    String id,
    String city,
    String country,
    String countryCode,
    String state,
    String formatted,
    Double latitude,
    Double longitude,
    String placeId
) {}
