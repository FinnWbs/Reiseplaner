package de.travelmate.planning;

import de.travelmate.activity.ActivityEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityTimeRulesTest {
    @Test
    void placeNameContainingBarIsNotAutomaticallyNightlife() {
        ActivityEntity cathedral = activity("Kultur", "cathedral");
        cathedral.name = "Barcelona Cathedral";

        ActivityTimeRules.TimeProfile profile = ActivityTimeRules.profile(cathedral);

        assertTrue(profile.earliestStart() < 1080);
        assertTrue(ActivityTimeRules.fitsAt(cathedral, 600, 90));
    }

    @Test
    void nightlifeFitsEveningButNotMorning() {
        ActivityEntity club = activity("Nightlife", "nightclub");

        assertFalse(ActivityTimeRules.fitsAt(club, 600, 180));
        assertTrue(ActivityTimeRules.fitsAt(club, 1200, 180));
    }

    @Test
    void cultureFitsDaytimeButNotLateNight() {
        ActivityEntity museum = activity("Kultur", "museum");

        assertTrue(ActivityTimeRules.fitsAt(museum, 600, 120));
        assertFalse(ActivityTimeRules.fitsAt(museum, 1260, 120));
    }

    @Test
    void knownCategoriesKeepTheirExpectedProfiles() {
        assertEquals(1080, profile("Nightlife", "bar").earliestStart());
        assertEquals(1080, profile("Nightlife", "club").earliestStart());
        assertEquals(120, profile("Kultur", "museum").durationMinutes());
        assertEquals(360, profile("Natur", "park").earliestStart());
        assertEquals(660, profile("Food", "restaurant").earliestStart());
        assertEquals(540, profile("Sport", "stadium").earliestStart());
    }

    @Test
    void categoryProfilesDefineUsefulDefaultDurations() {
        assertEquals(180, profile("Nightlife", "club").durationMinutes());
        assertEquals(90, profile("Food", "restaurant").durationMinutes());
        assertEquals(120, profile("Natur", "park").durationMinutes());
        assertEquals(120, profile("Kultur", "museum").durationMinutes());
        assertEquals(90, profile("Geschichte", "monument").durationMinutes());
        assertEquals(90, profile("Sport", "stadium").durationMinutes());
    }

    private ActivityTimeRules.TimeProfile profile(String category, String subcategory) {
        return ActivityTimeRules.profile(activity(category, subcategory));
    }

    private ActivityEntity activity(String category, String subcategory) {
        ActivityEntity activity = new ActivityEntity();
        activity.name = subcategory;
        activity.category = category;
        activity.subcategory = subcategory;
        return activity;
    }
}
