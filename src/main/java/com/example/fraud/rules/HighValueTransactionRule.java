package com.example.fraud.rules;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.domain.Decision;
import com.example.fraud.domain.Transaction;
import com.example.fraud.engine.FraudContext;
import com.example.fraud.engine.FraudRule;
import com.example.fraud.engine.RuleOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * FDP-001 - High value transaction.
 * Amount is compared against per-instrument challenge / block thresholds.
 */
@Component
public class HighValueTransactionRule implements FraudRule {

    private final FraudProperties.HighValue cfg;

    public HighValueTransactionRule(FraudProperties properties) {
        this.cfg = properties.rules().highValue();
    }

    @Override
    public String id() {
        return "FDP-001";
    }

    @Override
    public String name() {
        return "HIGH_VALUE_TRANSACTION";
    }

    @Override
    public boolean enabled() {
        return cfg.enabled();
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Optional<RuleOutcome> evaluate(FraudContext context) {
        Transaction txn = context.transaction();
        BigDecimal blockAt = cfg.blockAmount().get(txn.type());
        BigDecimal challengeAt = cfg.challengeAmount().get(txn.type());

        if (blockAt != null && txn.amount().compareTo(blockAt) >= 0) {
            return Optional.of(new RuleOutcome(id(), name(), Decision.BLOCK, cfg.score() + 25,
                    "Amount %s %s is at or above the hard block limit %s for %s"
                            .formatted(txn.currency(), txn.amount().toPlainString(), blockAt.toPlainString(), txn.type())));
        }
        if (challengeAt != null && txn.amount().compareTo(challengeAt) >= 0) {
            return Optional.of(new RuleOutcome(id(), name(), Decision.CHALLENGE, cfg.score(),
                    "Amount %s %s is at or above the review threshold %s for %s"
                            .formatted(txn.currency(), txn.amount().toPlainString(), challengeAt.toPlainString(), txn.type())));
        }
        return Optional.empty();
    }
}
