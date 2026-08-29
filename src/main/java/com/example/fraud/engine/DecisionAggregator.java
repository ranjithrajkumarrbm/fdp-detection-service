package com.example.fraud.engine;

import com.example.fraud.api.dto.FraudEvaluationResponse;
import com.example.fraud.api.dto.TriggeredRuleDto;
import com.example.fraud.config.FraudProperties;
import com.example.fraud.domain.Decision;
import com.example.fraud.domain.Transaction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Combines the individual {@link RuleOutcome}s into a single {@link Decision}.
 *
 * <p>Logic:
 * <ol>
 *   <li>if any rule recommends {@code BLOCK} &rarr; BLOCK</li>
 *   <li>else if the cumulative score &ge; {@code fraud.decision.block-score} &rarr; BLOCK</li>
 *   <li>else if any rule recommends {@code CHALLENGE} or score &ge; {@code challenge-score} &rarr; CHALLENGE</li>
 *   <li>else &rarr; GOOD</li>
 * </ol>
 */
@Component
public class DecisionAggregator {

    private final FraudProperties.DecisionThresholds thresholds;

    public DecisionAggregator(FraudProperties properties) {
        this.thresholds = properties.decision();
    }

    public FraudEvaluationResponse aggregate(Transaction txn, List<RuleOutcome> outcomes) {
        int totalScore = Math.min(100, outcomes.stream().mapToInt(RuleOutcome::score).sum());

        Decision ruleDecision = outcomes.stream()
                .map(RuleOutcome::action)
                .reduce(Decision.GOOD, Decision::max);

        Decision scoreDecision = Decision.GOOD;
        if (totalScore >= thresholds.blockScore()) {
            scoreDecision = Decision.BLOCK;
        } else if (totalScore >= thresholds.challengeScore()) {
            scoreDecision = Decision.CHALLENGE;
        }

        Decision decision = Decision.max(ruleDecision, scoreDecision);

        List<TriggeredRuleDto> triggered = outcomes.stream()
                .map(o -> new TriggeredRuleDto(o.ruleId(), o.ruleName(), o.action(), o.score(), o.reason()))
                .toList();

        List<String> reasons = outcomes.isEmpty()
                ? List.of("No fraud indicators matched")
                : outcomes.stream().map(o -> "[" + o.ruleId() + "] " + o.reason()).toList();

        return new FraudEvaluationResponse(
                txn.transactionId(),
                txn.customerId(),
                decision,
                totalScore,
                triggered,
                reasons,
                Instant.now()
        );
    }
}
