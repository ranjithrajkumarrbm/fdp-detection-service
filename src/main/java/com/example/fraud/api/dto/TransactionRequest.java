package com.example.fraud.api.dto;

import com.example.fraud.domain.TransactionStatus;
import com.example.fraud.domain.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Incoming transaction payload. Only {@code transactionId}, {@code customerId},
 * {@code type} and {@code amount} are mandatory; everything else enriches the
 * decision when present.
 */
public record TransactionRequest(

        @NotBlank String transactionId,
        @NotBlank String customerId,
        @NotNull TransactionType type,

        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        String currency,

        /** Defaults to SUCCESS when omitted. */
        TransactionStatus status,

        /** Defaults to "now" when omitted. */
        Instant timestamp,

        /** POS, ECOM, MOBILE, BRANCH ... free text. */
        String channel,

        @Valid LocationDto location,
        @Valid BeneficiaryDto beneficiary,

        String deviceId,
        String ipAddress
) {
}
