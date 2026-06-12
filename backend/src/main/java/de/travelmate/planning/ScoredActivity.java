package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;

public record ScoredActivity(ActivityEntity activity, double totalScore) {}
