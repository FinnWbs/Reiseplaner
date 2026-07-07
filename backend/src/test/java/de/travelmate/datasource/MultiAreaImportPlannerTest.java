package de.travelmate.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.travelmate.activity.ActivityImportSettings;
import de.travelmate.activity.ImportDemand;
import de.travelmate.interest.InterestType;
import de.travelmate.trip.TripPace;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MultiAreaImportPlannerTest {
    @Test
    void radiusResolverUsesConfigAndKeepsNatureLarger() {
        ActivityImportSettings settings = new ActivityImportSettings();
        ImportRadiusResolver resolver = new ImportRadiusResolver();
        resolver.settings = settings;
        ImportDemand demand = demand(InterestType.CULTURE, 7, 80, true);
        CitySpatialContext context = CitySpatialContext.from("Berlin", 52.52, 13.405, null, demand);

        int culture = resolver.resolveImportRadiusMeters(context, InterestType.CULTURE, demand);
        int nature = resolver.resolveImportRadiusMeters(context, InterestType.NATURE, demand);

        assertTrue(culture >= 30000);
        assertTrue(nature >= culture);
    }

    @Test
    void multiAreaBudgetIsDistributedWithoutIncreasingTotal() throws Exception {
        ActivityImportSettings settings = new ActivityImportSettings();
        set(settings, "multiAreaEnabled", true);
        set(settings, "multiAreaMinRawTargetPerArea", 12);
        MultiAreaImportPlanner planner = planner(settings);
        ImportDemand demand = demand(InterestType.FOOD, 7, 120, true);
        CitySpatialContext context = CitySpatialContext.from("Berlin", 52.52, 13.405, null, demand);

        MultiAreaImportPlan plan = planner.plan(context, InterestType.FOOD, demand);

        assertTrue(plan.areas().size() > 1);
        assertTrue(plan.distributedRawTarget() <= 120);
        int centerBudget = plan.areas().stream()
            .filter(area -> area.areaType() == ImportAreaType.CENTER)
            .findFirst()
            .orElseThrow()
            .rawTarget();
        assertTrue(centerBudget >= 42 && centerBudget <= 54);
    }

    @Test
    void smallRawTargetReducesAreasInsteadOfCreatingTinyBudgets() throws Exception {
        ActivityImportSettings settings = new ActivityImportSettings();
        set(settings, "multiAreaEnabled", true);
        set(settings, "multiAreaMinRawTargetPerArea", 20);
        MultiAreaImportPlanner planner = planner(settings);
        ImportDemand demand = demand(InterestType.FOOD, 7, 35, true);
        CitySpatialContext context = CitySpatialContext.from("Berlin", 52.52, 13.405, null, demand);

        MultiAreaImportPlan plan = planner.plan(context, InterestType.FOOD, demand);

        assertEquals(1, plan.areas().size());
        assertEquals(35, plan.distributedRawTarget());
    }

    private static MultiAreaImportPlanner planner(ActivityImportSettings settings) {
        MultiAreaImportPlanner planner = new MultiAreaImportPlanner();
        planner.settings = settings;
        planner.radiusResolver = new ImportRadiusResolver();
        planner.radiusResolver.settings = settings;
        planner.reachability = new ReachabilityPolicy();
        planner.reachability.settings = settings;
        return planner;
    }

    private static ImportDemand demand(InterestType interest, int days, int rawTarget, boolean multiAreaAllowed) {
        return new ImportDemand(
            "Berlin",
            Set.of(interest),
            days,
            TripPace.BALANCED,
            3,
            days * 3,
            days * 8,
            rawTarget,
            Map.of(interest, rawTarget),
            Map.of(interest, Math.max(1, rawTarget / 4)),
            days >= 7 ? 3 : 2,
            days >= 7 ? 0.55 : 0.60,
            days >= 7 ? 4 : 2,
            days >= 5,
            multiAreaAllowed
        );
    }

    private static void set(Object target, String field, Object value) throws Exception {
        java.lang.reflect.Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }
}
