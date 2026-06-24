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
    public final Map<String, String> rawTags = new HashMap<>();
    public Double latitude;
    public Double longitude;
    public String address;
    public Double rating;
    public final Map<ActivitySource, String> externalRefs = new EnumMap<>(ActivitySource.class);
}
