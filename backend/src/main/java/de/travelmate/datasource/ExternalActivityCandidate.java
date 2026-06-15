package de.travelmate.datasource;

import de.travelmate.activity.ActivitySource;
import java.util.EnumMap;
import java.util.Map;

public class ExternalActivityCandidate {
    public ActivitySource source;
    public String externalId;
    public String name;
    public String description;
    public String city;
    public String rawCategory;
    public Double latitude;
    public Double longitude;
    public String address;
    public Double rating;
    public final Map<ActivitySource, String> externalRefs = new EnumMap<>(ActivitySource.class);
}
