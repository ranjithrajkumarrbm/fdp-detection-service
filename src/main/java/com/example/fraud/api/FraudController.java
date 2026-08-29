package com.example.fraud.api;

import com.example.fraud.api.dto.FraudEvaluationResponse;
import com.example.fraud.api.dto.RuleInfoDto;
import com.example.fraud.api.dto.TransactionRequest;
import com.example.fraud.engine.RuleEngine;
import com.example.fraud.service.FraudDetectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudController {

    private final FraudDetectionService fraudDetectionService;
    private final RuleEngine ruleEngine;

    public FraudController(FraudDetectionService fraudDetectionService, RuleEngine ruleEngine) {
        this.fraudDetectionService = fraudDetectionService;
        this.ruleEngine = ruleEngine;
    }

    /** Evaluate a single transaction and return GOOD | CHALLENGE | BLOCK. */
    @PostMapping("/evaluate")
    public FraudEvaluationResponse evaluate(@Valid @RequestBody TransactionRequest request) {
        return fraudDetectionService.evaluate(request);
    }

    /** List the rules currently registered and whether each is enabled. */
    @GetMapping("/rules")
    public List<RuleInfoDto> rules() {
        return ruleEngine.registeredRules().stream()
                .map(r -> new RuleInfoDto(r.id(), r.name(), r.enabled()))
                .toList();
    }
}
