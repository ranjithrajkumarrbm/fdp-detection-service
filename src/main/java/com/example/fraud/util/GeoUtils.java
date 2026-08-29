package com.example.fraud.util;

import com.example.fraud.domain.GeoLocation;

/**
 * Great-circle distance helpers.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtils() {
    }

    /**
     * Haversine distance in kilometres between two locations, or {@code -1} if
     * either location is missing coordinates.
     */
    public static double haversineKm(GeoLocation a, GeoLocation b) {
        if (a == null || b == null || !a.hasCoordinates() || !b.hasCoordinates()) {
            return -1;
        }
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
