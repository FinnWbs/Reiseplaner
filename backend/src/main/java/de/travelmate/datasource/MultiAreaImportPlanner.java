package de.travelmate.datasource;

import de.travelmate.activity.ActivityImportSettings;
import de.travelmate.activity.ImportDemand;
import de.travelmate.interest.InterestType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class MultiAreaImportPlanner {
    private static final double[] DEFAULT_BEARINGS = {0, 90, 180, 270, 45, 225};

    @Inject
    ActivityImportSettings settings;

    @Inject
    ImportRadiusResolver radiusResolver;

    @Inject
    ReachabilityPolicy reachability;

    public MultiAreaImportPlan plan(CitySpatialContext context, InterestType interest, ImportDemand demand) {
        int rawTarget = rawTargetFor(interest, demand);
        if (!shouldUseMultiArea(context, demand, rawTarget)) {
            return centerOnlyPlan(context, interest, demand, rawTarget);
        }
        int maxAreas = Math.min(settings().multiAreaMaxAreas(), DEFAULT_BEARINGS.length + 1);
        int minBudget = settings().multiAreaMinRawTargetPerArea();
        double centerShare = settings().centerBudgetShareForTrip(context.tripDays());
        int centerBudget = Math.max(minBudget, (int) Math.round(rawTarget * centerShare));
        centerBudget = Math.min(centerBudget, rawTarget);
        int outerBudget = rawTarget - centerBudget;
        int outerCount = Math.min(maxAreas - 1, context.metroAreaCandidate() || context.tripDays() >= 7 ? 6 : 4);
        outerCount = Math.min(outerCount, outerBudget / minBudget);
        if (outerCount <= 0) {
            return centerOnlyPlan(context, interest, demand, rawTarget);
        }
        while (outerCount > 0 && outerBudget / outerCount < minBudget) {
            outerCount--;
        }
        if (outerCount <= 0) {
            return centerOnlyPlan(context, interest, demand, rawTarget);
        }

        List<ImportArea> areas = new ArrayList<>();
        int centerRadius = areaRadiusMeters(interest);
        areas.add(new ImportArea(
            "center",
            "Center",
            context.centerLat(),
            context.centerLon(),
            centerRadius,
            centerBudget / (double) rawTarget,
            centerBudget,
            ImportAreaType.CENTER,
            0,
            true
        ));

        int remaining = outerBudget;
        double ringDistance = ringDistance(context);
        for (int index = 0; index < outerCount; index++) {
            int budget = outerBudget / outerCount + (index < outerBudget % outerCount ? 1 : 0);
            budget = Math.min(budget, remaining);
            remaining -= budget;
            double[] point = outerPoint(context, DEFAULT_BEARINGS[index], ringDistance);
            double distance = GeoDistance.distanceKm(context.centerLat(), context.centerLon(), point[0], point[1]);
            ImportAreaType type = areaType(DEFAULT_BEARINGS[index]);
            ImportArea area = new ImportArea(
                type.name().toLowerCase(java.util.Locale.ROOT) + "-" + index,
                type.name(),
                point[0],
                point[1],
                areaRadiusMeters(interest),
                budget / (double) rawTarget,
                budget,
                type,
                distance,
                true
            );
            boolean reachable = reachability().isAreaReachableForTrip(area, context, interest);
            if (reachable) {
                areas.add(area);
            } else {
                centerBudget += budget;
            }
        }
        if (areas.size() == 1) {
            return centerOnlyPlan(context, interest, demand, rawTarget);
        }
        int distributed = areas.stream().mapToInt(ImportArea::rawTarget).sum();
        if (distributed < rawTarget) {
            ImportArea center = areas.get(0);
            areas.set(0, new ImportArea(
                center.id(),
                center.label(),
                center.centerLat(),
                center.centerLon(),
                center.radiusMeters(),
                (center.rawTarget() + rawTarget - distributed) / (double) rawTarget,
                center.rawTarget() + rawTarget - distributed,
                center.areaType(),
                center.distanceFromCityCenterKm(),
                center.reachable()
            ));
        }
        return new MultiAreaImportPlan(context.cityName(), interest, rawTarget, List.copyOf(areas), settings().maxPagesPerInterest());
    }

    private MultiAreaImportPlan centerOnlyPlan(
        CitySpatialContext context,
        InterestType interest,
        ImportDemand demand,
        int rawTarget
    ) {
        int radius = radiusResolver().resolveImportRadiusMeters(context, interest, demand);
        ImportArea area = new ImportArea(
            "center",
            "Center",
            context.centerLat(),
            context.centerLon(),
            radius,
            1,
            rawTarget,
            ImportAreaType.CENTER,
            0,
            true
        );
        return new MultiAreaImportPlan(context.cityName(), interest, rawTarget, List.of(area), settings().maxPagesPerInterest());
    }

    private boolean shouldUseMultiArea(CitySpatialContext context, ImportDemand demand, int rawTarget) {
        if (!settings().multiAreaEnabled() || demand == null || !demand.multiAreaAllowed()) {
            return false;
        }
        if (rawTarget < settings().multiAreaMinRawTargetPerArea() * 2) {
            return false;
        }
        return context.tripDays() >= settings().multiAreaLongTripDays()
            || context.largeCity()
            || context.metroAreaCandidate()
            || demand.requireOuterCoverageForLongTrip();
    }

    private int rawTargetFor(InterestType interest, ImportDemand demand) {
        if (demand != null && demand.rawTargetFor(interest) > 0) {
            return Math.min(demand.rawTargetFor(interest), settings().maxRawPerInterest());
        }
        return settings().minRawPerInterest();
    }

    private int areaRadiusMeters(InterestType interest) {
        return interest == InterestType.NATURE ? settings().multiAreaNatureRadiusMeters() : settings().multiAreaRadiusMeters();
    }

    private double ringDistance(CitySpatialContext context) {
        double target = context.metroAreaCandidate() ? settings().metroRingDistanceKm() : settings().largeCityRingDistanceKm();
        return Math.min(target, settings().maxAreaCenterDistanceKm());
    }

    private double[] outerPoint(CitySpatialContext context, double bearing, double ringDistance) {
        double[] point = GeoDistance.destination(context.centerLat(), context.centerLon(), bearing, ringDistance);
        if (context.bbox() == null || context.bbox().contains(point[0], point[1])) {
            return point;
        }
        return GeoDistance.destination(context.centerLat(), context.centerLon(), bearing, ringDistance * 0.65);
    }

    private ImportAreaType areaType(double bearing) {
        if (bearing == 0) return ImportAreaType.NORTH;
        if (bearing == 90) return ImportAreaType.EAST;
        if (bearing == 180) return ImportAreaType.SOUTH;
        if (bearing == 270) return ImportAreaType.WEST;
        return ImportAreaType.OUTER_RING;
    }

    private ActivityImportSettings settings() {
        return settings == null ? new ActivityImportSettings() : settings;
    }

    private ImportRadiusResolver radiusResolver() {
        if (radiusResolver == null) {
            radiusResolver = new ImportRadiusResolver();
            radiusResolver.settings = settings();
        }
        return radiusResolver;
    }

    private ReachabilityPolicy reachability() {
        if (reachability == null) {
            reachability = new ReachabilityPolicy();
            reachability.settings = settings();
        }
        return reachability;
    }
}
