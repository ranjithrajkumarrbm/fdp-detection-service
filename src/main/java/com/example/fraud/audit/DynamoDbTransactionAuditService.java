package com.example.fraud.audit;

import com.example.fraud.api.dto.FraudEvaluationResponse;
import com.example.fraud.api.dto.TransactionRequest;
import com.example.fraud.api.dto.TriggeredRuleDto;
import com.example.fraud.config.AuditProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Writes one row per evaluated transaction to DynamoDB, off the request thread.
 * Any failure is logged and swallowed.
 */
@Service
@ConditionalOnProperty(prefix = "audit.dynamodb", name = "enabled", havingValue = "true")
public class DynamoDbTransactionAuditService implements TransactionAuditService {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbTransactionAuditService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

    private final DynamoDbTable<TransactionAuditRecord> table;
    private final Duration ttl;

    public DynamoDbTransactionAuditService(DynamoDbTable<TransactionAuditRecord> table, AuditProperties props) {
        this.table = table;
        this.ttl = Duration.ofDays(props.ttlDays() > 0 ? props.ttlDays() : 400);
    }

    @Async("auditExecutor")
    @Override
    public void record(TransactionRequest request, FraudEvaluationResponse response) {
        try {
            table.putItem(toRecord(request, response));
        } catch (RuntimeException ex) {
            log.warn("Audit write failed for txn {}: {}", response.transactionId(), ex.toString());
        }
    }

    private TransactionAuditRecord toRecord(TransactionRequest req, FraudEvaluationResponse resp) {
        Instant evaluatedAt = resp.evaluatedAt() != null ? resp.evaluatedAt() : Instant.now();

        TransactionAuditRecord r = new TransactionAuditRecord();
        r.setTransactionId(resp.transactionId());
        r.setCustomerId(resp.customerId());
        r.setType(req.type() != null ? req.type().name() : null);
        r.setAmount(req.amount());
        r.setCurrency(req.currency() != null ? req.currency() : "INR");
        r.setStatus(req.status() != null ? req.status().name() : "SUCCESS");
        r.setChannel(req.channel());

        r.setDecision(resp.decision().name());
        r.setRiskScore(resp.riskScore());
        r.setTriggeredRuleIds(resp.triggeredRules().stream().map(TriggeredRuleDto::ruleId).toList());
        r.setReasons(resp.reasons());

        r.setLocationCity(req.location() != null ? req.location().city() : null);
        r.setIpAddress(req.ipAddress());

        r.setEvaluatedAt(TS.format(evaluatedAt));
        r.setReceivedAt(TS.format(Instant.now()));
        r.setTtl(evaluatedAt.plus(ttl).getEpochSecond());
        return r;
    }
}
