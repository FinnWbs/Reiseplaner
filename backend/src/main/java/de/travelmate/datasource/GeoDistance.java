package de.travelmate.datasource;

public final class GeoDistance {
    private GeoDistance() {}

    public static double distanceKm(double firstLatitude, double firstLongitude, double secondLatitude, double secondLongitude) {
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
            + Math.cos(Math.toRadians(firstLatitude)) * Math.cos(Math.toRadians(secondLatitude))
            * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static double[] destination(double latitude, double longitude, double bearingDegrees, double distanceKm) {
        double radius = 6371.0088;
        double angularDistance = distanceKm / radius;
        double bearing = Math.toRadians(bearingDegrees);
        double lat1 = Math.toRadians(latitude);
        double lon1 = Math.toRadians(longitude);

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angularDistance)
            + Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearing));
        double lon2 = lon1 + Math.atan2(
            Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(lat1),
            Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2)
        );
        return new double[] {Math.toDegrees(lat2), Math.toDegrees(lon2)};
    }
}
