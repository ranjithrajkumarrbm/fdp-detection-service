package com.example.fraud.api.dto;

import com.example.fraud.domain.Decision;

import java.time.Instant;
import java.util.List;

/**
 * Response returned for every evaluated transaction.
 *
 * @param decision      GOOD | CHALLENGE | BLOCK
 * @param riskScore     cumulative score 0-100
 * @param triggeredRules rules that fired, with their individual recommendation
 * @param reasons       flat list of human readable reason strings
 */
public record FraudEvaluationResponse(
        String transactionId,
        String customerId,
        Decision decision,
        int riskScore,
        List<TriggeredRuleDto> triggeredRules,
        List<String> reasons,
        Instant evaluatedAt
) {
}
