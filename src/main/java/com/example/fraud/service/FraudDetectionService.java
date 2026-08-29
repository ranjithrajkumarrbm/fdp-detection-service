package com.example.fraud.service;

import com.example.fraud.api.dto.BeneficiaryDto;
import com.example.fraud.api.dto.FraudEvaluationResponse;
import com.example.fraud.api.dto.LocationDto;
import com.example.fraud.api.dto.TransactionRequest;
import com.example.fraud.domain.Beneficiary;
import com.example.fraud.domain.CustomerProfile;
import com.example.fraud.domain.GeoLocation;
import com.example.fraud.domain.Transaction;
import com.example.fraud.domain.TransactionStatus;
import com.example.fraud.engine.DecisionAggregator;
import com.example.fraud.engine.FraudContext;
import com.example.fraud.engine.RuleEngine;
import com.example.fraud.engine.RuleOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Orchestrates a single evaluation: normalise the request, load context,
 * run the rule engine, aggregate the decision, and remember the transaction.
 */
@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);
    private static final Duration HISTORY_LOOKBACK = Duration.ofHours(24);

    private final RuleEngine ruleEngine;
    private final DecisionAggregator aggregator;
    private final CustomerProfileService profileService;
    private final TransactionHistoryService historyService;

    public FraudDetectionService(RuleEngine ruleEngine,
                                 DecisionAggregator aggregator,
                                 CustomerProfileService profileService,
                                 TransactionHistoryService historyService) {
        this.ruleEngine = ruleEngine;
        this.aggregator = aggregator;
        this.profileService = profileService;
        this.historyService = historyService;
    }

    public FraudEvaluationResponse evaluate(TransactionRequest request) {
        Transaction txn = toTransaction(request);
        CustomerProfile profile = profileService.getProfile(txn.customerId());

        // Record first so velocity-style rules see the current transaction too.
        historyService.record(txn);
        List<Transaction> recent = historyService.recentFor(txn.customerId(), HISTORY_LOOKBACK);

        FraudContext context = new FraudContext(txn, profile, recent);
        List<RuleOutcome> outcomes = ruleEngine.evaluate(context);
        FraudEvaluationResponse response = aggregator.aggregate(txn, outcomes);

        log.info("Evaluated txn={} customer={} type={} amount={} -> {} (score={}, rules={})",
                txn.transactionId(), txn.customerId(), txn.type(), txn.amount(),
                response.decision(), response.riskScore(),
                response.triggeredRules().stream().map(r -> r.ruleId()).toList());
        return response;
    }

    private static Transaction toTransaction(TransactionRequest r) {
        return new Transaction(
                r.transactionId(),
                r.customerId(),
                r.type(),
                r.status() != null ? r.status() : TransactionStatus.SUCCESS,
                r.amount(),
                r.currency() != null ? r.currency() : "INR",
                r.timestamp() != null ? r.timestamp() : Instant.now(),
                r.channel(),
                toGeoLocation(r.location()),
                toBeneficiary(r.beneficiary()),
                r.deviceId(),
                r.ipAddress()
        );
    }

    private static GeoLocation toGeoLocation(LocationDto d) {
        return d == null ? null : new GeoLocation(d.latitude(), d.longitude(), d.city(), d.country());
    }

    private static Beneficiary toBeneficiary(BeneficiaryDto d) {
        return d == null ? null : new Beneficiary(d.accountNumber(), d.ifsc(), d.name(), d.bankName());
    }
}
