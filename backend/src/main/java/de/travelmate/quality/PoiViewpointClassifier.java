package de.travelmate.quality;

import de.travelmate.datasource.ExternalActivityCandidate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PoiViewpointClassifier {
    @Inject
    PoiSignalMatcher matcher;

    public boolean isViewpoint(ExternalActivityCandidate candidate) {
        String name = signals().normalized(candidate.name);
        return signals().matches(candidate, "tourism.attraction.viewpoint", "tourism.sights.viewpoint")
            || signals().hasTagValue(candidate, "tourism", "viewpoint")
            || name.contains("viewpoint")
            || name.contains("aussichtspunkt")
            || name.contains("observation deck")
            || name.contains("observation tower")
            || name.contains("viewing platform")
            || name.contains("belvedere");
    }

    public ViewpointSubtype subtype(ExternalActivityCandidate candidate) {
        if (!isViewpoint(candidate)) return ViewpointSubtype.NONE;
        if (hasInfrastructureContext(candidate)) return ViewpointSubtype.VIEWPOINT_INFRASTRUCTURE;
        if (hasIndoorContext(candidate)) return ViewpointSubtype.VIEWPOINT_INDOOR;
        if (hasNaturalContext(candidate)) return ViewpointSubtype.VIEWPOINT_NATURAL;
        return ViewpointSubtype.VIEWPOINT_URBAN;
    }

    public boolean hasNaturalContext(ExternalActivityCandidate candidate) {
        return signals().matches(candidate,
            "leisure.park", "leisure.park.garden", "leisure.park.nature_reserve",
            "national_park", "natural.protected_area", "natural.forest", "natural.wood",
            "natural.mountain", "natural.water", "natural.waterfall", "beach"
        ) || signals().hasTagValue(candidate, "leisure", "park", "garden", "nature_reserve")
            || signals().hasTagValue(candidate, "natural",
                "peak", "cliff", "hill", "mountain", "mountain_range", "water", "wood", "forest", "beach", "waterfall"
            )
            || signals().hasTagValue(candidate, "boundary", "national_park")
            || signals().hasTag(candidate, "protected_area")
            || signals().hasTagValue(candidate, "route", "hiking")
            || signals().hasTagValue(candidate, "garden:type", "botanical", "botanic")
            || signals().hasTagValue(candidate, "garden", "botanical")
            || signals().hasTagValue(candidate, "botanical", "yes");
    }

    public boolean hasUrbanOrInfrastructureContext(ExternalActivityCandidate candidate) {
        return isViewpoint(candidate) && (hasInfrastructureContext(candidate) || hasIndoorContext(candidate));
    }

    boolean hasInfrastructureContext(ExternalActivityCandidate candidate) {
        String name = signals().normalized(candidate.name);
        return signals().containsAny(name,
            "railway", "train", "train tracks", "station", "platform", "subway", "metro",
            "airport", "terminal", "parking", "parking deck", "highway", "road infrastructure",
            "industrial", "port", "harbor", "harbour"
        )
            || signals().hasTag(candidate, "railway")
            || signals().hasTag(candidate, "public_transport")
            || signals().hasTag(candidate, "aeroway")
            || signals().hasTag(candidate, "platform")
            || signals().hasTag(candidate, "subway")
            || signals().hasTag(candidate, "rail")
            || signals().hasTag(candidate, "terminal")
            || signals().hasTagValue(candidate, "amenity", "parking")
            || signals().hasTag(candidate, "parking")
            || signals().hasTag(candidate, "highway")
            || signals().hasTagValue(candidate, "bridge", "yes")
            || signals().hasTagValue(candidate, "man_made", "bridge");
    }

    boolean hasIndoorContext(ExternalActivityCandidate candidate) {
        String name = signals().normalized(candidate.name);
        return signals().containsAny(name,
            "building", "tower", "skyscraper", "observation deck", "rooftop", "roof terrace",
            "terrace", "floor", "mall", "shopping center", "shopping centre", "department store",
            "office", "hotel rooftop", "restaurant viewpoint", "bar viewpoint", "museum viewpoint", "deck"
        )
            || name.matches(".*\\b\\d{1,2}f\\b.*")
            || signals().hasTag(candidate, "level")
            || signals().hasTag(candidate, "addr:floor")
            || signals().hasTag(candidate, "building")
            || signals().hasTagValue(candidate, "indoor", "yes")
            || signals().hasTag(candidate, "office")
            || signals().hasTag(candidate, "shop")
            || signals().hasTagValue(candidate, "amenity", "restaurant", "cafe", "fast_food", "bar", "pub")
            || signals().hasTagValue(candidate, "tourism", "museum", "gallery")
            || signals().matches(candidate, "commercial", "shop", "catering", "entertainment.museum", "entertainment.culture.gallery");
    }

    private PoiSignalMatcher signals() {
        return matcher == null ? new PoiSignalMatcher() : matcher;
    }
}
