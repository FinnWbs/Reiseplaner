package de.travelmate.datasource;

import de.travelmate.activity.ActivitySource;
import de.travelmate.interest.InterestType;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ExternalActivityCandidate {
    public ActivitySource source;
    public String externalId;
    public String name;
    public String description;
    public String city;
    public String rawCategory;
    public final Set<String> rawCategories = new java.util.LinkedHashSet<>();
    public final Set<InterestType> matchedInterests = EnumSet.noneOf(InterestType.class);
    public InterestType primaryInterest;
    public String website;
    public String openingHours;
    public boolean hasWikidata;
    public boolean hasImage;
    public boolean hasHeritageStatus;
    public boolean isUnescoWorldHeritage;
    public int wikidataSitelinksCount;
    public Integer wikipediaPageviews365d;
    public Double geometryAreaM2;
    public Double distanceToCenterKm;
    public int nearbyShopDensity;
    public boolean suppressedAsSubPoi;
    public String suppressionReason;
    public String preferredParentName;
    public final Map<String, String> rawTags = new HashMap<>();
    public Double latitude;
    public Double longitude;
    public String address;
    public Double rating;
    public final Map<ActivitySource, String> externalRefs = new EnumMap<>(ActivitySource.class);
}
