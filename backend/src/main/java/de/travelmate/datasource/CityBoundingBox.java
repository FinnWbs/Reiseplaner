package de.travelmate.datasource;

public record CityBoundingBox(
    double minLat,
    double minLon,
    double maxLat,
    double maxLon
) {
    public boolean contains(double latitude, double longitude) {
        return latitude >= minLat && latitude <= maxLat && longitude >= minLon && longitude <= maxLon;
    }
}
