package com.example.fraud.domain;

/**
 * Counterparty of a NEFT / IMPS transfer.
 */
public record Beneficiary(String accountNumber, String ifsc, String name, String bankName) {
}
