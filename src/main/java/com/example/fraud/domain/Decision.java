package com.example.fraud.domain;

/**
 * Final risk decision returned to the caller, ordered by severity.
 * GOOD  - allow straight through
 * CHALLENGE - step up authentication (OTP / 3DS / call back)
 * BLOCK - decline the transaction
 */
public enum Decision {
    GOOD(0),
    CHALLENGE(1),
    BLOCK(2);

    private final int severity;

    Decision(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    /**
     * @return the more severe of the two decisions.
     */
    public static Decision max(Decision a, Decision b) {
        return a.severity >= b.severity ? a : b;
    }
}
