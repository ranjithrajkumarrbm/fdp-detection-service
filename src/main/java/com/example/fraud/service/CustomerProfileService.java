package com.example.fraud.service;

import com.example.fraud.domain.CustomerProfile;
import com.example.fraud.domain.GeoLocation;
import com.example.fraud.domain.TransactionType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Serves the behavioural baseline for a customer.
 *
 * <p>Seeded in-memory for the sample service. Swap this implementation for one
 * backed by a profile store / feature store without touching the rules.
 */
@Service
public class CustomerProfileService {

    private final Map<String, CustomerProfile> profiles = Map.of(
            "CUST1001", new CustomerProfile(
                    "CUST1001",
                    new GeoLocation(19.0760, 72.8777, "Mumbai", "IN"),
                    new BigDecimal("3500"),
                    Set.of(TransactionType.DEBIT_CARD, TransactionType.CREDIT_CARD, TransactionType.IMPS),
                    Set.of("11122233344", "55566677788"),
                    900),
            "CUST1002", new CustomerProfile(
                    "CUST1002",
                    new GeoLocation(28.6139, 77.2090, "New Delhi", "IN"),
                    new BigDecimal("12000"),
                    Set.of(TransactionType.CREDIT_CARD, TransactionType.NEFT),
                    Set.of("99988877766"),
                    1500),
            "CUST1003", new CustomerProfile(
                    "CUST1003",
                    new GeoLocation(12.9716, 77.5946, "Bengaluru", "IN"),
                    new BigDecimal("800"),
                    Set.of(TransactionType.DEBIT_CARD),
                    Set.of(),
                    20)
    );

    public CustomerProfile getProfile(String customerId) {
        return profiles.getOrDefault(customerId, CustomerProfile.unknown(customerId));
    }
}
