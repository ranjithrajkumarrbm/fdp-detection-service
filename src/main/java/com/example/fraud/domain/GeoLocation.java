package com.example.fraud.domain;

/**
 * A point on the earth plus optional human readable context.
 */
public record GeoLocation(Double latitude, Double longitude, String city, String country) {

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    public String label() {
        if (city != null && !city.isBlank()) {
            return country != null && !country.isBlank() ? city + ", " + country : city;
        }
        return hasCoordinates() ? "(%.4f, %.4f)".formatted(latitude, longitude) : "unknown";
    }
}
