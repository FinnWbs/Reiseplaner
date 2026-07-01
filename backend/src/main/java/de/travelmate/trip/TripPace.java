package de.travelmate.trip;

public enum TripPace {
    RELAXED(2),
    BALANCED(3),
    ACTIVE(4);

    private final int activitiesPerDay;

    TripPace(int activitiesPerDay) {
        this.activitiesPerDay = activitiesPerDay;
    }

    public int activitiesPerDay() {
        return activitiesPerDay;
    }
}
