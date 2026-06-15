package de.travelmate.trip;

public enum DayRhythm {
    EARLY(480, 1020),
    BALANCED(540, 1200),
    LATE(720, 1440);

    private final int availableFrom;
    private final int availableUntil;

    DayRhythm(int availableFrom, int availableUntil) {
        this.availableFrom = availableFrom;
        this.availableUntil = availableUntil;
    }

    public int availableFrom() {
        return availableFrom;
    }

    public int availableUntil() {
        return availableUntil;
    }
}
