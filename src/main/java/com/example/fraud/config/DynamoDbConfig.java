package com.example.fraud.config;

import com.example.fraud.audit.TransactionAuditRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Only created when {@code audit.dynamodb.enabled=true}. Region and credentials
 * come from the standard AWS provider chains (env {@code AWS_REGION} + IRSA in EKS).
 */
@Configuration
@ConditionalOnProperty(prefix = "audit.dynamodb", name = "enabled", havingValue = "true")
public class DynamoDbConfig {

    @Bean(destroyMethod = "close")
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    }

    @Bean
    public DynamoDbTable<TransactionAuditRecord> auditTable(DynamoDbEnhancedClient enhanced,
                                                           AuditProperties props) {
        return enhanced.table(props.tableName(), TableSchema.fromBean(TransactionAuditRecord.class));
    }
}
