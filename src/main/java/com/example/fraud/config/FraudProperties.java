package com.example.fraud.config;

import com.example.fraud.domain.TransactionType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Strongly typed binding for everything under the {@code fraud.*} prefix.
 *
 * <p>Every value can be overridden from the environment using Spring Boot's relaxed
 * binding, e.g. {@code FRAUD_RULES_HIGHVALUE_ENABLED=false} or
 * {@code FRAUD_DECISION_BLOCKSCORE=90}. Map entries can be overridden via
 * {@code SPRING_APPLICATION_JSON}. Defaults live in {@code application.yml}.
 */
@ConfigurationProperties(prefix = "fraud")
public record FraudProperties(DecisionThresholds decision, Rules rules) {

    /** Score based fallback used when no single rule is decisive. */
    public record DecisionThresholds(int challengeScore, int blockScore) {
    }

    public record Rules(
            HighValue highValue,
            Velocity velocity,
            LocationDeviation locationDeviation,
            UnusualActivity unusualActivity,
            FailedThenSuccess failedThenSuccess,
            SuspiciousTransfer suspiciousTransfer
    ) {
    }

    /** FDP-001 high value transaction, per instrument. */
    public record HighValue(
            boolean enabled,
            Map<TransactionType, BigDecimal> challengeAmount,
            Map<TransactionType, BigDecimal> blockAmount,
            int score
    ) {
    }

    /** FDP-002 velocity / burst of transactions in a short window. */
    public record Velocity(
            boolean enabled,
            int windowSeconds,
            int maxTransactions,
            int blockTransactions,
            int score
    ) {
    }

    /** FDP-003 transaction far from the customer's home location. */
    public record LocationDeviation(
            boolean enabled,
            double challengeDistanceKm,
            double blockDistanceKm,
            int score
    ) {
    }

    /** FDP-004 unusual amount or previously unseen instrument for this customer. */
    public record UnusualActivity(
            boolean enabled,
            double amountMultiplier,
            int score
    ) {
    }

    /** FDP-005 several failed attempts shortly followed by a success. */
    public record FailedThenSuccess(
            boolean enabled,
            int windowSeconds,
            int minFailures,
            int score
    ) {
    }

    /** FDP-006 suspicious NEFT / IMPS transfer. */
    public record SuspiciousTransfer(
            boolean enabled,
            BigDecimal highAmount,
            double structuringBand,
            Map<TransactionType, BigDecimal> regulatoryLimit,
            BigDecimal newBeneficiaryHighAmount,
            int score
    ) {
    }
}
