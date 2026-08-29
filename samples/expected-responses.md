# Sample transactions & expected responses

Base URL (local): `http://localhost:8080`
Endpoint: `POST /api/v1/fraud/evaluate`

All responses also include `evaluatedAt` (ISO-8601 instant); it is omitted below for brevity.
Scores/decisions assume the default configuration in `application.yml`.

---

## 01 — Good debit card (`01-good-debit-card.json`)

Small in-pattern POS purchase near the customer's home city.

```bash
curl -s localhost:8080/api/v1/fraud/evaluate \
  -H 'Content-Type: application/json' -d @samples/01-good-debit-card.json | jq
```

```json
{
  "transactionId": "TXN-20260829-0001",
  "customerId": "CUST1001",
  "decision": "GOOD",
  "riskScore": 0,
  "triggeredRules": [],
  "reasons": ["No fraud indicators matched"]
}
```

---

## 02 — High-value credit card (`02-high-value-credit-card.json`)

₹12.5L on a credit card — above the hard block limit; also far above the customer average.

```json
{
  "transactionId": "TXN-20260829-0002",
  "customerId": "CUST1002",
  "decision": "BLOCK",
  "riskScore": 100,
  "triggeredRules": [
    { "ruleId": "FDP-001", "ruleName": "HIGH_VALUE_TRANSACTION", "action": "BLOCK",
      "score": 70, "reason": "Amount INR 1250000.00 is at or above the hard block limit 1000000 for CREDIT_CARD" },
    { "ruleId": "FDP-004", "ruleName": "UNUSUAL_ACTIVITY", "action": "CHALLENGE",
      "score": 30, "reason": "Unusual for this customer: amount 1250000.00 is 104.2x the customer average 12000" }
  ],
  "reasons": [
    "[FDP-001] Amount INR 1250000.00 is at or above the hard block limit 1000000 for CREDIT_CARD",
    "[FDP-004] Unusual for this customer: amount 1250000.00 is 104.2x the customer average 12000"
  ]
}
```

---

## 03 — Location deviation (`03-location-deviation.json`)

Card-not-present purchase from Dubai (~1,900 km) for a customer whose home is Mumbai,
and the amount is well above their norm.

```json
{
  "transactionId": "TXN-20260829-0003",
  "customerId": "CUST1001",
  "decision": "CHALLENGE",
  "riskScore": 65,
  "triggeredRules": [
    { "ruleId": "FDP-003", "ruleName": "LOCATION_DEVIATION", "action": "CHALLENGE",
      "score": 35, "reason": "Transaction in Dubai, AE is 1935 km from home Mumbai, IN (review distance 300 km)" },
    { "ruleId": "FDP-004", "ruleName": "UNUSUAL_ACTIVITY", "action": "CHALLENGE",
      "score": 30, "reason": "Unusual for this customer: amount 42000.00 is 12.0x the customer average 3500" }
  ]
}
```

---

## 04 — Velocity burst (`04-velocity-burst.json`)

Replace `<N>` with 1,2,3… and POST rapidly for the **same** `customerId`.

```bash
for i in $(seq 1 8); do
  sed "s/<N>/$i/" samples/04-velocity-burst.json \
   | curl -s localhost:8080/api/v1/fraud/evaluate -H 'Content-Type: application/json' -d @- \
   | jq -c '{txn: .transactionId, decision, score: .riskScore}'
done
```

| Attempt (within 120 s) | Decision  | Triggered |
|------------------------|-----------|-----------|
| 1 – 3                  | GOOD      | –         |
| 4 – 7                  | CHALLENGE | FDP-002   |
| 8+                     | BLOCK     | FDP-002   |

```json
{
  "transactionId": "TXN-20260829-0004-8",
  "customerId": "CUST1003",
  "decision": "BLOCK",
  "riskScore": 60,
  "triggeredRules": [
    { "ruleId": "FDP-002", "ruleName": "TRANSACTION_VELOCITY", "action": "BLOCK",
      "score": 60, "reason": "8 transactions in the last 120s (block threshold 8)" }
  ]
}
```

---

## 05 — Failed attempts then success (`05-failed-then-success.json`)

POST 3 requests with `"status":"FAILED"` for `CUST2005`, then the success payload.

```bash
for i in 1 2 3; do
  curl -s localhost:8080/api/v1/fraud/evaluate -H 'Content-Type: application/json' \
    -d '{"transactionId":"TXN-0005-f'$i'","customerId":"CUST2005","type":"CREDIT_CARD","amount":799,"status":"FAILED"}' >/dev/null
done
curl -s localhost:8080/api/v1/fraud/evaluate -H 'Content-Type: application/json' -d @samples/05-failed-then-success.json | jq
```

```json
{
  "transactionId": "TXN-20260829-0005-success",
  "customerId": "CUST2005",
  "decision": "CHALLENGE",
  "riskScore": 50,
  "triggeredRules": [
    { "ruleId": "FDP-005", "ruleName": "FAILED_THEN_SUCCESS", "action": "CHALLENGE",
      "score": 50, "reason": "3 failed attempt(s) in the last 600s immediately before this success" }
  ]
}
```

> If you also fire the four calls inside the 120 s velocity window, `FDP-002` adds
> another 40 points and the decision becomes `BLOCK`.

---

## 06 — Suspicious IMPS transfer (`06-suspicious-imps.json`)

₹4,87,500 — deliberately just under the ₹5,00,000 IMPS ceiling — to a payee this
customer has never sent money to before.

```json
{
  "transactionId": "TXN-20260829-0006",
  "customerId": "CUST1001",
  "decision": "BLOCK",
  "riskScore": 100,
  "triggeredRules": [
    { "ruleId": "FDP-001", "ruleName": "HIGH_VALUE_TRANSACTION", "action": "CHALLENGE",
      "score": 45, "reason": "Amount INR 487500.00 is at or above the review threshold 200000 for IMPS" },
    { "ruleId": "FDP-004", "ruleName": "UNUSUAL_ACTIVITY", "action": "CHALLENGE",
      "score": 30, "reason": "Unusual for this customer: amount 487500.00 is 139.3x the customer average 3500" },
    { "ruleId": "FDP-006", "ruleName": "SUSPICIOUS_TRANSFER", "action": "BLOCK",
      "score": 80, "reason": "Suspicious transfer: amount 487500.00 sits just below the IMPS regulatory limit 500000 (possible structuring); high-value transfer 487500.00 to beneficiary Quick Cash Traders not seen on this account before" }
  ]
}
```
