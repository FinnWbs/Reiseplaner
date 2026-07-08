package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;

record PlanSlotChoice(ActivityEntity activity, int start, int duration) {}
