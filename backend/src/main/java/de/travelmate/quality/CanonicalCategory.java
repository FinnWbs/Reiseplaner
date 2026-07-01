package de.travelmate.quality;

import de.travelmate.interest.InterestType;

public enum CanonicalCategory {
    LANDMARK(InterestType.SIGHTSEEING, "Sehenswürdigkeiten"),
    MONUMENT(InterestType.SIGHTSEEING, "Sehenswürdigkeiten"),
    CULTURE(InterestType.CULTURE, "Kultur & Museen"),
    HERITAGE(InterestType.CULTURE, "Kultur & Museen"),
    NATURE(InterestType.NATURE, "Natur & Outdoor"),
    FOOD(InterestType.FOOD, "Essen & Cafés"),
    NIGHTLIFE(InterestType.NIGHTLIFE, "Nachtleben & Unterhaltung"),
    SHOPPING(InterestType.SHOPPING, "Shopping & Märkte"),
    GENERIC(null, "Sehenswürdigkeiten");

    private final InterestType interestType;
    private final String displayName;

    CanonicalCategory(InterestType interestType, String displayName) {
        this.interestType = interestType;
        this.displayName = displayName;
    }

    public InterestType interestType() {
        return interestType;
    }

    public String displayName() {
        return displayName;
    }
}
