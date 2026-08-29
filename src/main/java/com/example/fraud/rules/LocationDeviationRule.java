package com.example.fraud.rules;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.domain.Decision;
import com.example.fraud.domain.GeoLocation;
import com.example.fraud.domain.Transaction;
import com.example.fraud.engine.FraudContext;
import com.example.fraud.engine.FraudRule;
import com.example.fraud.engine.RuleOutcome;
import com.example.fraud.util.GeoUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * FDP-003 - Location deviation. Transaction originates a long way from the
 * customer's known home location.
 */
@Component
public class LocationDeviationRule implements FraudRule {

    private final FraudProperties.LocationDeviation cfg;

    public LocationDeviationRule(FraudProperties properties) {
        this.cfg = properties.rules().locationDeviation();
    }

    @Override
    public String id() {
        return "FDP-003";
    }

    @Override
    public String name() {
        return "LOCATION_DEVIATION";
    }

    @Override
    public boolean enabled() {
        return cfg.enabled();
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Optional<RuleOutcome> evaluate(FraudContext context) {
        Transaction txn = context.transaction();
        GeoLocation home = context.profile().homeLocation();
        GeoLocation here = txn.location();

        double km = GeoUtils.haversineKm(home, here);
        if (km < 0) {
            return Optional.empty();
        }

        if (km >= cfg.blockDistanceKm()) {
            return Optional.of(new RuleOutcome(id(), name(), Decision.BLOCK, cfg.score() + 20,
                    "Transaction in %s is %.0f km from home %s (block distance %.0f km)"
                            .formatted(here.label(), km, home.label(), cfg.blockDistanceKm())));
        }
        if (km >= cfg.challengeDistanceKm()) {
            return Optional.of(new RuleOutcome(id(), name(), Decision.CHALLENGE, cfg.score(),
                    "Transaction in %s is %.0f km from home %s (review distance %.0f km)"
                            .formatted(here.label(), km, home.label(), cfg.challengeDistanceKm())));
        }
        return Optional.empty();
    }
}
