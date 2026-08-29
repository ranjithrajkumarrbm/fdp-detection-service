package com.example.fraud.api.dto;

import com.example.fraud.domain.Decision;

public record TriggeredRuleDto(
        String ruleId,
        String ruleName,
        Decision action,
        int score,
        String reason
) {
}
