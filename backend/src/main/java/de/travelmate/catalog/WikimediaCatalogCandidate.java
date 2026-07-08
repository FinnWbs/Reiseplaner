package de.travelmate.catalog;

import de.travelmate.interest.InterestType;

class WikimediaCatalogCandidate {
    String catalogId;
    String name;
    String city;
    String wikidataId;
    String wikipediaProject;
    String wikipediaTitle;
    InterestType primaryInterest;
    String category;
    Double latitude;
    Double longitude;
    String description;
    int sitelinkCount;
    boolean hasImage;
    boolean hasWebsite;
    boolean hasCoordinates;
    boolean administrativelyInCity;
    boolean acceptedAsNearbyEnclave;
    double distanceFromCityCenterKm;
    long pageviews;
    double categoryFitScore;
    double dataQualityScore;
    double publicAttractionScore;
}
