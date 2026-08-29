package com.example.fraud.rules;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.domain.Beneficiary;
import com.example.fraud.domain.Decision;
import com.example.fraud.domain.Transaction;
import com.example.fraud.engine.FraudContext;
import com.example.fraud.engine.FraudRule;
import com.example.fraud.engine.RuleOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * FDP-006 - Suspicious NEFT / IMPS transfer.
 * Looks for:
 *  - amounts parked just below a regulatory limit (structuring), and
 *  - high value transfers to a beneficiary the customer has not paid before.
 */
@Component
public class SuspiciousTransferRule implements FraudRule {

    private final FraudProperties.SuspiciousTransfer cfg;

    public SuspiciousTransferRule(FraudProperties properties) {
        this.cfg = properties.rules().suspiciousTransfer();
    }

    @Override
    public String id() {
        return "FDP-006";
    }

    @Override
    public String name() {
        return "SUSPICIOUS_TRANSFER";
    }

    @Override
    public boolean enabled() {
        return cfg.enabled();
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public Optional<RuleOutcome> evaluate(FraudContext context) {
        Transaction txn = context.transaction();
        if (!txn.isTransfer()) {
            return Optional.empty();
        }

        List<String> reasons = new ArrayList<>();
        int score = 0;
        Decision action = Decision.CHALLENGE;

        Beneficiary bene = txn.beneficiary();
        boolean newBeneficiary = bene != null && bene.accountNumber() != null
                && !context.profile().knownBeneficiaries().contains(bene.accountNumber());

        BigDecimal limit = cfg.regulatoryLimit().get(txn.type());
        if (limit != null) {
            BigDecimal bandStart = limit.multiply(BigDecimal.valueOf(cfg.structuringBand()));
            if (txn.amount().compareTo(bandStart) >= 0 && txn.amount().compareTo(limit) < 0) {
                reasons.add("amount %s sits just below the %s regulatory limit %s (possible structuring)"
                        .formatted(txn.amount().toPlainString(), txn.type(), limit.toPlainString()));
                score += cfg.score();
            }
        }

        if (newBeneficiary && txn.amount().compareTo(cfg.newBeneficiaryHighAmount()) >= 0) {
            reasons.add("high-value transfer %s to beneficiary %s not seen on this account before"
                    .formatted(txn.amount().toPlainString(),
                            bene.name() != null ? bene.name() : bene.accountNumber()));
            score += cfg.score();
        }

        if (newBeneficiary && txn.amount().compareTo(cfg.highAmount()) >= 0) {
            action = Decision.BLOCK;
            score += 15;
        }

        if (reasons.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RuleOutcome(id(), name(), action, Math.min(score, 80),
                "Suspicious transfer: " + String.join("; ", reasons)));
    }
}
