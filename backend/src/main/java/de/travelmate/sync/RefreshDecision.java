package de.travelmate.sync;

public enum RefreshDecision {
    NO_REFRESH_NEEDED(false, false, 0),
    MULTI_AREA_RECOMMENDED_ONLY(false, true, 1),
    REFRESH_FOR_VERSION(true, false, 3),
    REFRESH_FOR_COUNT(true, false, 4),
    MULTI_AREA_REFRESH_REQUIRED(true, true, 5);

    private final boolean importRequired;
    private final boolean spatial;
    private final int priority;

    RefreshDecision(boolean importRequired, boolean spatial, int priority) {
        this.importRequired = importRequired;
        this.spatial = spatial;
        this.priority = priority;
    }

    public boolean importRequired() {
        return importRequired;
    }

    public boolean spatial() {
        return spatial;
    }

    public RefreshDecision max(RefreshDecision other) {
        return other.priority > priority ? other : this;
    }
}
