package com.example.fraud.audit;

import com.example.fraud.api.dto.FraudEvaluationResponse;
import com.example.fraud.api.dto.TransactionRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Active when {@code audit.dynamodb.enabled} is false or unset - keeps local runs
 * and tests free of any AWS dependency.
 */
@Service
@ConditionalOnProperty(prefix = "audit.dynamodb", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class NoOpTransactionAuditService implements TransactionAuditService {

    @Override
    public void record(TransactionRequest request, FraudEvaluationResponse response) {
        // auditing disabled
    }
}
