package com.example.fraud.engine;

import java.util.Optional;

/**
 * A single, self-contained fraud rule.
 *
 * <p>To add a new rule: implement this interface, annotate it with
 * {@code @Component}, and it is automatically picked up by {@link RuleEngine}.
 * Keep rules stateless and driven by {@code fraud.rules.*} configuration.
 */
public interface FraudRule {

    /** Stable identifier returned to the caller, e.g. {@code FDP-007}. */
    String id();

    /** Human readable name, e.g. {@code NEW_DEVICE}. */
    String name();

    /** Whether the rule is currently switched on (usually backed by config). */
    boolean enabled();

    /** Lower runs first. Only affects reporting order, not the decision. */
    default int order() {
        return 100;
    }

    /**
     * @return an outcome if the rule fired, otherwise {@link Optional#empty()}.
     */
    Optional<RuleOutcome> evaluate(FraudContext context);
}
