package com.example.fraud.api.dto;

public record BeneficiaryDto(
        String accountNumber,
        String ifsc,
        String name,
        String bankName
) {
}
