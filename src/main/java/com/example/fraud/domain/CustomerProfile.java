package com.example.fraud.domain;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Known behavioural baseline for a customer. In a real system this would be served
 * from a profile store / feature store; here it is seeded in-memory.
 */
public record CustomerProfile(
        String customerId,
        GeoLocation homeLocation,
        BigDecimal averageTransactionAmount,
        Set<TransactionType> knownTransactionTypes,
        Set<String> knownBeneficiaries,
        int accountAgeDays
) {
    public static CustomerProfile unknown(String customerId) {
        return new CustomerProfile(customerId, null, BigDecimal.ZERO, Set.of(), Set.of(), 0);
    }

    public boolean isEstablished() {
        return accountAgeDays >= 90;
    }
}
