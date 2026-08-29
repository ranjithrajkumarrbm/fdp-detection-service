package com.example.fraud.service;

import com.example.fraud.domain.Transaction;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Short-lived, in-memory store of recent transactions per customer. Used by the
 * velocity and "failed then success" rules.
 *
 * <p>This is intentionally simple. In production replace it with Redis / a
 * streaming store; the rule code does not need to change.
 */
@Service
public class TransactionHistoryService {

    /** How long a transaction is retained for scoring purposes. */
    private static final Duration RETENTION = Duration.ofHours(24);
    /** Hard cap per customer to bound memory. */
    private static final int MAX_PER_CUSTOMER = 500;

    private final Map<String, Deque<Transaction>> byCustomer = new ConcurrentHashMap<>();

    public void record(Transaction txn) {
        Deque<Transaction> deque = byCustomer.computeIfAbsent(txn.customerId(), k -> new ConcurrentLinkedDeque<>());
        deque.removeIf(t -> t.transactionId().equals(txn.transactionId()));
        deque.addLast(txn);
        prune(deque, txn.timestamp());
    }

    /** Recent transactions for a customer inside {@code window}, oldest first. */
    public List<Transaction> recentFor(String customerId, Duration window) {
        Deque<Transaction> deque = byCustomer.get(customerId);
        if (deque == null) {
            return List.of();
        }
        Instant cutoff = Instant.now().minus(window);
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : deque) {
            if (t.timestamp().isAfter(cutoff)) {
                result.add(t);
            }
        }
        result.sort(Comparator.comparing(Transaction::timestamp));
        return result;
    }

    private void prune(Deque<Transaction> deque, Instant now) {
        Instant cutoff = now.minus(RETENTION);
        Transaction head;
        while ((head = deque.peekFirst()) != null && head.timestamp().isBefore(cutoff)) {
            deque.pollFirst();
        }
        while (deque.size() > MAX_PER_CUSTOMER) {
            deque.pollFirst();
        }
    }
}
