package com.example.fraud.engine;

import com.example.fraud.domain.CustomerProfile;
import com.example.fraud.domain.Transaction;
import com.example.fraud.domain.TransactionStatus;

import java.time.Duration;
import java.util.List;

/**
 * Everything a rule needs to make a decision about {@link #transaction()}.
 *
 * <p>{@link #recentHistory()} is ordered oldest-first and already includes the
 * transaction currently being evaluated.
 */
public record FraudContext(Transaction transaction, CustomerProfile profile, List<Transaction> recentHistory) {

    /** Prior transactions (excluding the current one) that fall inside {@code window}. */
    public List<Transaction> historyWithin(Duration window) {
        var cutoff = transaction.timestamp().minus(window);
        return recentHistory.stream()
                .filter(t -> !t.transactionId().equals(transaction.transactionId()))
                .filter(t -> t.timestamp().isAfter(cutoff))
                .filter(t -> !t.timestamp().isAfter(transaction.timestamp()))
                .toList();
    }

    public long countWithin(Duration window, TransactionStatus status) {
        return historyWithin(window).stream().filter(t -> t.status() == status).count();
    }
}
