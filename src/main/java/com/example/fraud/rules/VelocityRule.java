package com.example.fraud.rules;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.domain.Decision;
import com.example.fraud.engine.FraudContext;
import com.example.fraud.engine.FraudRule;
import com.example.fraud.engine.RuleOutcome;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * FDP-002 - Velocity. Too many transactions for the same customer inside a
 * short rolling window (the current transaction is included in the count).
 */
@Component
public class VelocityRule implements FraudRule {

    private final FraudProperties.Velocity cfg;

    public VelocityRule(FraudProperties properties) {
        this.cfg = properties.rules().velocity();
    }

    @Override
    public String id() {
        return "FDP-002";
    }

    @Override
    public String name() {
        return "TRANSACTION_VELOCITY";
    }

    @Override
    public boolean enabled() {
        return cfg.enabled();
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Optional<RuleOutcome> evaluate(FraudContext context) {
        Duration window = Duration.ofSeconds(cfg.windowSeconds());
        // +1 for the transaction currently being evaluated.
        long count = context.historyWithin(window).size() + 1;

        if (count >= cfg.blockTransactions()) {
            return Optional.of(new RuleOutcome(id(), name(), Decision.BLOCK, cfg.score() + 20,
                    "%d transactions in the last %ds (block threshold %d)"
                            .formatted(count, cfg.windowSeconds(), cfg.blockTransactions())));
        }
        if (count >= cfg.maxTransactions()) {
            return Optional.of(new RuleOutcome(id(), name(), Decision.CHALLENGE, cfg.score(),
                    "%d transactions in the last %ds (review threshold %d)"
                            .formatted(count, cfg.windowSeconds(), cfg.maxTransactions())));
        }
        return Optional.empty();
    }
}
