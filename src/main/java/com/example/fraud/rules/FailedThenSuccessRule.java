package com.example.fraud.rules;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.domain.Decision;
import com.example.fraud.domain.TransactionStatus;
import com.example.fraud.engine.FraudContext;
import com.example.fraud.engine.FraudRule;
import com.example.fraud.engine.RuleOutcome;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * FDP-005 - Multiple failed transactions followed by a success.
 * Classic card-testing / brute-force pattern.
 */
@Component
public class FailedThenSuccessRule implements FraudRule {

    private final FraudProperties.FailedThenSuccess cfg;

    public FailedThenSuccessRule(FraudProperties properties) {
        this.cfg = properties.rules().failedThenSuccess();
    }

    @Override
    public String id() {
        return "FDP-005";
    }

    @Override
    public String name() {
        return "FAILED_THEN_SUCCESS";
    }

    @Override
    public boolean enabled() {
        return cfg.enabled();
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public Optional<RuleOutcome> evaluate(FraudContext context) {
        if (context.transaction().status() != TransactionStatus.SUCCESS) {
            return Optional.empty();
        }
        Duration window = Duration.ofSeconds(cfg.windowSeconds());
        long failures = context.countWithin(window, TransactionStatus.FAILED);

        if (failures >= cfg.minFailures()) {
            Decision action = failures >= cfg.minFailures() * 2L ? Decision.BLOCK : Decision.CHALLENGE;
            int score = action == Decision.BLOCK ? cfg.score() + 20 : cfg.score();
            return Optional.of(new RuleOutcome(id(), name(), action, score,
                    "%d failed attempt(s) in the last %ds immediately before this success"
                            .formatted(failures, cfg.windowSeconds())));
        }
        return Optional.empty();
    }
}
