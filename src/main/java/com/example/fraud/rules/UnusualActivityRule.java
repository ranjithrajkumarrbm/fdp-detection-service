package com.example.fraud.rules;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.domain.CustomerProfile;
import com.example.fraud.domain.Decision;
import com.example.fraud.domain.Transaction;
import com.example.fraud.engine.FraudContext;
import com.example.fraud.engine.FraudRule;
import com.example.fraud.engine.RuleOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * FDP-004 - Unusual transaction type / amount for this customer.
 * Fires when the amount is a large multiple of the customer's average, or when
 * the instrument has never been used by the customer before.
 */
@Component
public class UnusualActivityRule implements FraudRule {

    private final FraudProperties.UnusualActivity cfg;

    public UnusualActivityRule(FraudProperties properties) {
        this.cfg = properties.rules().unusualActivity();
    }

    @Override
    public String id() {
        return "FDP-004";
    }

    @Override
    public String name() {
        return "UNUSUAL_ACTIVITY";
    }

    @Override
    public boolean enabled() {
        return cfg.enabled();
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public Optional<RuleOutcome> evaluate(FraudContext context) {
        Transaction txn = context.transaction();
        CustomerProfile profile = context.profile();

        List<String> reasons = new ArrayList<>();
        int score = 0;

        BigDecimal avg = profile.averageTransactionAmount();
        if (avg != null && avg.signum() > 0) {
            BigDecimal threshold = avg.multiply(BigDecimal.valueOf(cfg.amountMultiplier()));
            if (txn.amount().compareTo(threshold) > 0) {
                double ratio = txn.amount().divide(avg, java.math.MathContext.DECIMAL64).doubleValue();
                reasons.add("amount %s is %.1fx the customer average %s"
                        .formatted(txn.amount().toPlainString(), ratio, avg.toPlainString()));
                score += cfg.score();
            }
        }

        if (!profile.knownTransactionTypes().isEmpty()
                && !profile.knownTransactionTypes().contains(txn.type())) {
            reasons.add("customer has no prior %s transactions".formatted(txn.type()));
            score += Math.max(1, cfg.score() / 2);
        }

        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RuleOutcome(id(), name(), Decision.CHALLENGE, Math.min(score, 60),
                "Unusual for this customer: " + String.join("; ", reasons)));
    }
}
