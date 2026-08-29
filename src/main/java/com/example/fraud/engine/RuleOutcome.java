package com.example.fraud.engine;

import com.example.fraud.domain.Decision;

/**
 * Result of a single rule firing.
 *
 * @param ruleId  stable identifier, e.g. {@code FDP-001}
 * @param ruleName human readable name, e.g. {@code HIGH_VALUE_TRANSACTION}
 * @param action  the decision this rule recommends on its own
 * @param score   risk contribution (0-100) added to the cumulative score
 * @param reason  operator-facing explanation
 */
public record RuleOutcome(String ruleId, String ruleName, Decision action, int score, String reason) {
}
