package com.example.fraud.audit;

import com.example.fraud.api.dto.FraudEvaluationResponse;
import com.example.fraud.api.dto.TransactionRequest;

/**
 * Persists an audit trail of every evaluated transaction. Implementations MUST be
 * asynchronous / fire-and-forget - a failure here must never affect the decision
 * response or its latency.
 */
public interface TransactionAuditService {

    void record(TransactionRequest request, FraudEvaluationResponse response);
}
