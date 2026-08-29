package com.example.fraud.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Runs every enabled {@link FraudRule} against a {@link FraudContext} and
 * collects the outcomes. A misbehaving rule is logged and skipped so it can
 * never take the whole service down.
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final List<FraudRule> rules;

    public RuleEngine(List<FraudRule> rules) {
        this.rules = rules.stream().sorted(Comparator.comparingInt(FraudRule::order)).toList();
        log.info("Fraud rule engine initialised with {} rule(s): {}",
                this.rules.size(), this.rules.stream().map(FraudRule::id).toList());
    }

    public List<RuleOutcome> evaluate(FraudContext context) {
        return rules.stream()
                .filter(FraudRule::enabled)
                .map(rule -> safeEvaluate(rule, context))
                .flatMap(Optional::stream)
                .toList();
    }

    /** Rules currently registered, for the introspection endpoint. */
    public List<FraudRule> registeredRules() {
        return rules;
    }

    private Optional<RuleOutcome> safeEvaluate(FraudRule rule, FraudContext context) {
        try {
            return rule.evaluate(context);
        } catch (RuntimeException ex) {
            log.warn("Rule {} ({}) failed for transaction {} - skipping", rule.id(), rule.name(),
                    context.transaction().transactionId(), ex);
            return Optional.empty();
        }
    }
}
