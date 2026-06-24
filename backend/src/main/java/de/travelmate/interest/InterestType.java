package de.travelmate.interest;

public enum InterestType {
    SIGHTSEEING("Sehenswürdigkeiten", true),
    CULTURE("Kultur & Museen", true),
    NATURE("Natur & Outdoor", true),
    FOOD("Essen & Cafés", true),
    SHOPPING("Shopping & Märkte", true),
    NIGHTLIFE("Nachtleben & Unterhaltung", true),
    HISTORY("Geschichte & Architektur", false),
    RELAXATION("Entspannung", false),
    ADVENTURE("Abenteuer", false),
    FAMILY("Familienfreundlich", false);

    private final String displayName;
    private final boolean primary;

    InterestType(String displayName, boolean primary) {
        this.displayName = displayName;
        this.primary = primary;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isPrimary() {
        return primary;
    }

    public static java.util.Set<InterestType> primaryTypes() {
        return java.util.Arrays.stream(values()).filter(InterestType::isPrimary)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
