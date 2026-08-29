package com.example.fraud.util;

import com.example.fraud.domain.GeoLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoUtilsTest {

    @Test
    void returnsMinusOneWhenCoordinatesMissing() {
        assertThat(GeoUtils.haversineKm(null, null)).isEqualTo(-1);
        assertThat(GeoUtils.haversineKm(
                new GeoLocation(null, null, "Mumbai", "IN"),
                new GeoLocation(19.0, 72.0, "x", "IN"))).isEqualTo(-1);
    }

    @Test
    void mumbaiToDelhiIsRoughly1150Km() {
        double km = GeoUtils.haversineKm(
                new GeoLocation(19.0760, 72.8777, "Mumbai", "IN"),
                new GeoLocation(28.6139, 77.2090, "New Delhi", "IN"));
        assertThat(km).isBetween(1100.0, 1200.0);
    }
}
