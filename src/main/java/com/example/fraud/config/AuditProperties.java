package com.example.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding for {@code audit.dynamodb.*}. All env-driven, see {@code application.yml}.
 *
 * @param enabled    turn the DynamoDB audit trail on/off (off = no-op, no AWS needed)
 * @param tableName  DynamoDB table name (required when enabled)
 * @param ttlDays    retention; written to the {@code ttl} attribute as epoch seconds
 */
@ConfigurationProperties(prefix = "audit.dynamodb")
public record AuditProperties(boolean enabled, String tableName, int ttlDays) {
}
