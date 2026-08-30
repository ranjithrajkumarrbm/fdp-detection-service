package com.example.fraud.audit;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.math.BigDecimal;
import java.util.List;

/**
 * One audit row per evaluated transaction: the incoming transaction plus the
 * decision it got.
 *
 * <p>Table {@code fdp-audit-table}: partition key = {@code transactionId} (primary
 * access pattern is look-up by transaction). The {@code customerId} /
 * {@code evaluatedAt} annotations describe an optional GSI
 * ({@code customerId-evaluatedAt-index}) for "all evaluations for a customer,
 * time-ordered" - writes work whether or not that GSI exists.
 * {@code ttl} (epoch seconds) drives DynamoDB TTL when enabled on the table.
 */
@DynamoDbBean
public class TransactionAuditRecord {

    private String transactionId;
    private String customerId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String channel;

    private String decision;
    private int riskScore;
    private List<String> triggeredRuleIds;
    private List<String> reasons;

    private String locationCity;
    private String ipAddress;

    private String evaluatedAt;
    private String receivedAt;
    private long ttl;

    @DynamoDbPartitionKey
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "customerId-evaluatedAt-index")
    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public List<String> getTriggeredRuleIds() {
        return triggeredRuleIds;
    }

    public void setTriggeredRuleIds(List<String> triggeredRuleIds) {
        this.triggeredRuleIds = triggeredRuleIds;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public String getLocationCity() {
        return locationCity;
    }

    public void setLocationCity(String locationCity) {
        this.locationCity = locationCity;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @DynamoDbSecondarySortKey(indexNames = "customerId-evaluatedAt-index")
    public String getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(String evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public String getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(String receivedAt) {
        this.receivedAt = receivedAt;
    }

    @DynamoDbAttribute("ttl")
    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }
}
