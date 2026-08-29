package com.example.fraud.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalised, immutable view of an incoming transaction used by the rule engine.
 */
public record Transaction(
        String transactionId,
        String customerId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        String channel,
        GeoLocation location,
        Beneficiary beneficiary,
        String deviceId,
        String ipAddress
) {
    public boolean isCardTransaction() {
        return type == TransactionType.DEBIT_CARD || type == TransactionType.CREDIT_CARD;
    }

    public boolean isTransfer() {
        return type == TransactionType.NEFT || type == TransactionType.IMPS;
    }
}
