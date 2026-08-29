package com.example.fraud.domain;

/**
 * Outcome of a transaction attempt as reported by the acquiring/channel system.
 */
public enum TransactionStatus {
    SUCCESS,
    FAILED,
    PENDING
}
