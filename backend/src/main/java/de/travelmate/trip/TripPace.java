package de.travelmate.trip;

public enum TripPace {
    RELAXED(1),
    BALANCED(2),
    ACTIVE(3);

    private final int activitiesPerDay;

    TripPace(int activitiesPerDay) {
        this.activitiesPerDay = activitiesPerDay;
    }

    public int activitiesPerDay() {
        return activitiesPerDay;
    }
}
