# FDP Detection Service

A small, production-shaped **Fraud Detection microservice** built with Java 21 and
Spring Boot 3. It exposes a REST API that scores a transaction and returns one of:

| Decision   | Meaning                              |
|------------|--------------------------------------|
| `GOOD`     | allow straight through              |
| `CHALLENGE`| step-up auth (OTP / 3DS / call-back) |
| `BLOCK`    | decline                             |

along with the **rule IDs**, per-rule recommendation, score, and human-readable
reasons.

Supported instruments: `DEBIT_CARD`, `CREDIT_CARD`, `NEFT`, `IMPS`.

---

## Contents

- [How it works](#how-it-works)
- [Fraud rules](#fraud-rules)
- [Project layout](#project-layout)
- [Configuration (environment-driven)](#configuration-environment-driven)
- [Run locally](#run-locally)
- [API examples](#api-examples)
- [Health / readiness endpoints](#health--readiness-endpoints)
- [Run the tests](#run-the-tests)
- [Docker](#docker)
- [Push to Amazon ECR](#push-to-amazon-ecr)
- [Deploy to EKS](#deploy-to-eks)
- [GitHub Actions pipeline](#github-actions-pipeline)
- [Adding a new rule](#adding-a-new-rule)

---

## How it works

```
TransactionRequest ──► FraudDetectionService
                          │  1. normalise to Transaction
                          │  2. load CustomerProfile      (seeded in-memory)
                          │  3. record in TransactionHistoryService (24h, in-memory)
                          │  4. RuleEngine.evaluate(context)
                          │       └─ every enabled FraudRule -> Optional<RuleOutcome>
                          │  5. DecisionAggregator.aggregate(...)
                          ▼
                    FraudEvaluationResponse  (decision + rules + score + reasons)
```

Decision aggregation:

1. any rule says `BLOCK` **or** cumulative score ≥ `fraud.decision.block-score` → **BLOCK**
2. else any rule says `CHALLENGE` **or** score ≥ `fraud.decision.challenge-score` → **CHALLENGE**
3. else → **GOOD**

`CustomerProfileService` and `TransactionHistoryService` are deliberately simple
in-memory stubs. Swap them for a profile/feature store and Redis without touching
any rule.

---

## Fraud rules

| ID      | Name                   | Fires when …                                                                                          | Key config (`fraud.rules.*`)                                  |
|---------|------------------------|------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| FDP-001 | `HIGH_VALUE_TRANSACTION`| amount ≥ per-instrument challenge / block threshold                                                  | `high-value.challenge-amount`, `high-value.block-amount`     |
| FDP-002 | `TRANSACTION_VELOCITY` | too many transactions for the customer in a short rolling window (current txn counted)               | `velocity.window-seconds`, `max-transactions`, `block-transactions` |
| FDP-003 | `LOCATION_DEVIATION`   | transaction location is far (haversine km) from the customer's home location                         | `location-deviation.challenge-distance-km`, `block-distance-km` |
| FDP-004 | `UNUSUAL_ACTIVITY`     | amount ≫ customer's average (`amount-multiplier`×), or an instrument the customer has never used     | `unusual-activity.amount-multiplier`                         |
| FDP-005 | `FAILED_THEN_SUCCESS`  | ≥ `min-failures` failed attempts in the window, immediately followed by this success (card testing)  | `failed-then-success.window-seconds`, `min-failures`         |
| FDP-006 | `SUSPICIOUS_TRANSFER`  | NEFT/IMPS amount parked just under a regulatory limit (structuring), or high value to a new payee    | `suspicious-transfer.regulatory-limit`, `structuring-band`, `new-beneficiary-high-amount`, `high-amount` |

Every rule can be switched off independently with `fraud.rules.<rule>.enabled=false`.
`GET /api/v1/fraud/rules` lists what is currently registered and enabled.

---

## Project layout

```
src/main/java/com/example/fraud
├── api/            REST controller, error handling, DTOs
├── config/         FraudProperties  (binds all fraud.* config)
├── domain/         Transaction, CustomerProfile, enums, value objects
├── engine/         FraudRule, FraudContext, RuleEngine, DecisionAggregator
├── rules/          FDP-001 … FDP-006  (one class each)
├── service/        FraudDetectionService, CustomerProfileService, TransactionHistoryService
└── util/           GeoUtils (haversine)
src/main/resources/application.yml
k8s/                namespace, configmap, deployment, service, hpa  (envsubst templated)
.github/workflows/ci-cd.yml
samples/            request JSONs + expected-responses.md
Dockerfile
```

---

## Configuration (environment-driven)

Everything is read from `application.yml`, and **every scalar is overridable via an
environment variable** (Spring Boot relaxed binding). Nothing below requires a code
or image change.

### Runtime / platform

| Env var                 | Default | Purpose                          |
|-------------------------|---------|----------------------------------|
| `SERVER_PORT`           | `8080`  | HTTP port                        |
| `SPRING_PROFILES_ACTIVE`| –       | Spring profile                   |
| `LOG_LEVEL_APP`         | `INFO`  | log level for `com.example.fraud`|

### Decisioning

| Env var                          | Default | Purpose                              |
|----------------------------------|---------|--------------------------------------|
| `FRAUD_DECISION_CHALLENGE_SCORE` | `40`    | cumulative score → CHALLENGE         |
| `FRAUD_DECISION_BLOCK_SCORE`     | `80`    | cumulative score → BLOCK             |

### Per-rule (subset — see `application.yml` for the full list)

| Env var                                    | Default | Rule    |
|--------------------------------------------|---------|---------|
| `FRAUD_RULES_HIGH_VALUE_ENABLED`           | `true`  | FDP-001 |
| `FRAUD_RULES_VELOCITY_ENABLED`             | `true`  | FDP-002 |
| `FRAUD_RULES_VELOCITY_WINDOW_SECONDS`      | `120`   | FDP-002 |
| `FRAUD_RULES_VELOCITY_MAX_TRANSACTIONS`    | `4`     | FDP-002 |
| `FRAUD_RULES_VELOCITY_BLOCK_TRANSACTIONS`  | `8`     | FDP-002 |
| `FRAUD_RULES_LOCATION_DEVIATION_ENABLED`   | `true`  | FDP-003 |
| `FRAUD_RULES_LOCATION_CHALLENGE_KM`        | `300`   | FDP-003 |
| `FRAUD_RULES_LOCATION_BLOCK_KM`            | `2000`  | FDP-003 |
| `FRAUD_RULES_UNUSUAL_AMOUNT_MULTIPLIER`    | `5`     | FDP-004 |
| `FRAUD_RULES_FAILED_THEN_SUCCESS_MIN_FAILURES` | `3`  | FDP-005 |
| `FRAUD_RULES_SUSPICIOUS_STRUCTURING_BAND`  | `0.95`  | FDP-006 |
| `FRAUD_RULES_SUSPICIOUS_HIGH_AMOUNT`       | `200000`| FDP-006 |

Per-instrument maps (`challenge-amount`, `block-amount`, `regulatory-limit`) can be
overridden with `SPRING_APPLICATION_JSON`, e.g.:

```bash
export SPRING_APPLICATION_JSON='{"fraud":{"rules":{"high-value":{"block-amount":{"DEBIT_CARD":300000}}}}}'
```

---

## Run locally

Requirements: **JDK 21** and **Maven 3.9+** (or just Docker — see below).

```bash
mvn spring-boot:run
# or
mvn clean package && java -jar target/fdp-detection-service.jar
```

Override config on the fly:

```bash
FRAUD_RULES_VELOCITY_MAX_TRANSACTIONS=2 FRAUD_DECISION_BLOCK_SCORE=60 mvn spring-boot:run
```

Seeded demo customers: `CUST1001` (Mumbai), `CUST1002` (New Delhi), `CUST1003`
(Bengaluru, new account). Any other `customerId` is treated as an unknown customer
with no baseline.

---

## API examples

### Evaluate a transaction

```bash
curl -s http://localhost:8080/api/v1/fraud/evaluate \
  -H 'Content-Type: application/json' \
  -d '{
        "transactionId": "TXN-1001",
        "customerId": "CUST1001",
        "type": "DEBIT_CARD",
        "amount": 2200,
        "location": { "latitude": 19.08, "longitude": 72.89, "city": "Mumbai", "country": "IN" }
      }' | jq
```

```json
{
  "transactionId": "TXN-1001",
  "customerId": "CUST1001",
  "decision": "GOOD",
  "riskScore": 0,
  "triggeredRules": [],
  "reasons": ["No fraud indicators matched"],
  "evaluatedAt": "2026-08-29T10:15:30.123Z"
}
```

### A blocked transfer

```bash
curl -s http://localhost:8080/api/v1/fraud/evaluate \
  -H 'Content-Type: application/json' \
  -d @samples/06-suspicious-imps.json | jq
```

```json
{
  "transactionId": "TXN-20260829-0006",
  "customerId": "CUST1001",
  "decision": "BLOCK",
  "riskScore": 100,
  "triggeredRules": [
    { "ruleId": "FDP-001", "ruleName": "HIGH_VALUE_TRANSACTION", "action": "CHALLENGE", "score": 45, "reason": "Amount INR 487500.00 is at or above the review threshold 200000 for IMPS" },
    { "ruleId": "FDP-004", "ruleName": "UNUSUAL_ACTIVITY", "action": "CHALLENGE", "score": 30, "reason": "Unusual for this customer: amount 487500.00 is 139.3x the customer average 3500" },
    { "ruleId": "FDP-006", "ruleName": "SUSPICIOUS_TRANSFER", "action": "BLOCK", "score": 80, "reason": "Suspicious transfer: amount 487500.00 sits just below the IMPS regulatory limit 500000 (possible structuring); high-value transfer 487500.00 to beneficiary Quick Cash Traders not seen on this account before" }
  ],
  "reasons": [ "[FDP-001] ...", "[FDP-004] ...", "[FDP-006] ..." ],
  "evaluatedAt": "2026-08-29T10:16:02.001Z"
}
```

### List registered rules

```bash
curl -s http://localhost:8080/api/v1/fraud/rules | jq
```

### Request fields

| Field           | Required | Notes                                                   |
|-----------------|----------|---------------------------------------------------------|
| `transactionId` | yes      | unique id                                               |
| `customerId`    | yes      |                                                        |
| `type`          | yes      | `DEBIT_CARD` \| `CREDIT_CARD` \| `NEFT` \| `IMPS`       |
| `amount`        | yes      | > 0                                                     |
| `status`        | no       | `SUCCESS` (default) \| `FAILED` \| `PENDING`            |
| `timestamp`     | no       | ISO-8601 instant; defaults to now                       |
| `currency`      | no       | defaults to `INR`                                       |
| `channel`       | no       | `POS`, `ECOM`, `MOBILE`, `BRANCH`, …                    |
| `location`      | no       | `{ latitude, longitude, city, country }`                |
| `beneficiary`   | no       | `{ accountNumber, ifsc, name, bankName }` (NEFT/IMPS)   |
| `deviceId`, `ipAddress` | no |                                                     |

More request/response pairs: [`samples/expected-responses.md`](samples/expected-responses.md).

---

## Health / readiness endpoints

Spring Boot Actuator, used by the Kubernetes probes:

| Endpoint                         | Use                          |
|----------------------------------|------------------------------|
| `GET /actuator/health`           | overall health               |
| `GET /actuator/health/liveness`  | Kubernetes **livenessProbe** |
| `GET /actuator/health/readiness` | Kubernetes **readinessProbe**/startupProbe |
| `GET /actuator/info`             | build info                   |
| `GET /actuator/metrics`          | metrics                      |
| `GET /actuator/prometheus`       | Prometheus scrape endpoint   |

---

## Run the tests

```bash
mvn test
```

Covers `GeoUtils`, context loading, and end-to-end rule behaviour through the REST
API (good / high-value block / location / velocity escalation / failed-then-success
/ suspicious transfer / validation 400).

---

## Docker

Multi-stage build (Maven → JRE), runs as non-root uid `10001`.

```bash
docker build -t fdp-detection-service:local .

docker run --rm -p 8080:8080 \
  -e FRAUD_DECISION_BLOCK_SCORE=70 \
  fdp-detection-service:local

curl -s localhost:8080/actuator/health/readiness
```

---

## Push to Amazon ECR

Manual equivalent of the pipeline (set the vars for your account):

```bash
export AWS_REGION=ap-south-1
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export ECR_REPOSITORY=fdp-detection-service
export IMAGE_TAG=$(git rev-parse --short=12 HEAD)
export ECR_REGISTRY=$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# 1. repository (idempotent)
aws ecr describe-repositories --repository-names "$ECR_REPOSITORY" --region "$AWS_REGION" \
  || aws ecr create-repository --repository-name "$ECR_REPOSITORY" --region "$AWS_REGION" \
       --image-scanning-configuration scanOnPush=true

# 2. login
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# 3. build, tag, push
docker build -t "$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG" -t "$ECR_REGISTRY/$ECR_REPOSITORY:latest" .
docker push "$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG"
docker push "$ECR_REGISTRY/$ECR_REPOSITORY:latest"
```

---

## Deploy to EKS

The manifests in [`k8s/`](k8s/) are plain YAML with `${VAR}` placeholders resolved by
`envsubst`, so the same files work for any cluster / namespace / scale.

```bash
export AWS_REGION=ap-south-1
export EKS_CLUSTER_NAME=fdp-eks
export K8S_NAMESPACE=fraud-detection
export REPLICAS=3
export HPA_MIN=3
export HPA_MAX=10
export APP_ENV=prod
export LOG_LEVEL_APP=INFO
export IMAGE_TAG=$(git rev-parse --short=12 HEAD)
export IMAGE=$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/fdp-detection-service:$IMAGE_TAG
# defaults for the ConfigMap tunables (override as needed)
export FRAUD_DECISION_CHALLENGE_SCORE=40 FRAUD_DECISION_BLOCK_SCORE=80
export FRAUD_RULES_HIGH_VALUE_ENABLED=true FRAUD_RULES_VELOCITY_ENABLED=true
export FRAUD_RULES_VELOCITY_MAX_TRANSACTIONS=4 FRAUD_RULES_VELOCITY_WINDOW_SECONDS=120
export FRAUD_RULES_LOCATION_DEVIATION_ENABLED=true
export FRAUD_RULES_FAILED_THEN_SUCCESS_ENABLED=true FRAUD_RULES_SUSPICIOUS_TRANSFER_ENABLED=true

aws eks update-kubeconfig --name "$EKS_CLUSTER_NAME" --region "$AWS_REGION"

for f in namespace configmap deployment service hpa; do
  envsubst < "k8s/$f.yaml" | kubectl apply -f -
done

kubectl -n "$K8S_NAMESPACE" rollout status deployment/fdp-detection-service --timeout=240s
```

Test from inside the cluster:

```bash
kubectl -n "$K8S_NAMESPACE" run curl --rm -it --image=curlimages/curl --restart=Never -- \
  curl -s http://fdp-detection-service/api/v1/fraud/rules
```

Expose it with an `Ingress` / `Service type=LoadBalancer` as your platform dictates
(kept out of this repo since ingress controllers vary per cluster).

---

## GitHub Actions pipeline

[`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) runs on push to `main`
and via **Run workflow**:

1. **build-test** — `mvn clean verify` on JDK 21, uploads the jar.
2. **image-and-deploy**
   - authenticate to AWS (`aws-actions/configure-aws-credentials`)
   - `aws-actions/amazon-ecr-login`
   - create the ECR repo if missing
   - `docker build` → push `:<sha>` and `:latest`
   - `aws eks update-kubeconfig` for the **existing** cluster
   - `envsubst` the `k8s/*.yaml` and `kubectl apply`
   - `kubectl rollout status`

### Required GitHub **Secrets**

| Secret                  | Purpose                    |
|-------------------------|----------------------------|
| `AWS_ACCESS_KEY_ID`     | CI IAM user access key     |
| `AWS_SECRET_ACCESS_KEY` | CI IAM user secret key     |

The IAM principal needs ECR push (`ecr:*` on the repo + `ecr:GetAuthorizationToken`)
and `eks:DescribeCluster` plus RBAC in the cluster (an EKS access entry / `aws-auth`
mapping) that allows `kubectl apply` in the target namespace.

> Prefer OIDC? Replace the `configure-aws-credentials` inputs with
> `role-to-assume: <role-arn>` and add `permissions: { id-token: write, contents: read }`
> to the job.

### GitHub **Variables** (all optional — fallbacks in the workflow)

`AWS_REGION`, `ECR_REPOSITORY`, `EKS_CLUSTER_NAME`, `K8S_NAMESPACE`, `REPLICAS`,
`HPA_MIN`, `HPA_MAX`, `APP_ENV`, `LOG_LEVEL_APP`,
`FRAUD_DECISION_CHALLENGE_SCORE`, `FRAUD_DECISION_BLOCK_SCORE`,
`FRAUD_RULES_HIGH_VALUE_ENABLED`, `FRAUD_RULES_VELOCITY_ENABLED`,
`FRAUD_RULES_VELOCITY_MAX_TRANSACTIONS`, `FRAUD_RULES_VELOCITY_WINDOW_SECONDS`,
`FRAUD_RULES_LOCATION_DEVIATION_ENABLED`, `FRAUD_RULES_FAILED_THEN_SUCCESS_ENABLED`,
`FRAUD_RULES_SUSPICIOUS_TRANSFER_ENABLED`.

Change any of these in the GitHub UI and re-run — no code change, no image rebuild
needed for the ConfigMap-driven values.

---

## Adding a new rule

1. Create a class in `com.example.fraud.rules` implementing `FraudRule`:

```java
@Component
public class NewDeviceRule implements FraudRule {

    public String id()      { return "FDP-007"; }
    public String name()    { return "NEW_DEVICE"; }
    public boolean enabled() { return true; }          // back it with FraudProperties
    public int order()      { return 70; }

    public Optional<RuleOutcome> evaluate(FraudContext ctx) {
        // inspect ctx.transaction(), ctx.profile(), ctx.recentHistory()
        return Optional.empty();
    }
}
```

2. (Optional) add a config block to `FraudProperties` + `application.yml` with an
   `enabled` flag and thresholds so it stays environment-driven.

That's it — `RuleEngine` autowires every `FraudRule` bean, runs the enabled ones,
isolates failures, and `DecisionAggregator` folds the new outcome into the score and
final decision. No other file needs to change.

---

## Notes / not in scope

- Customer profiles and transaction history are **in-memory** (single instance).
  For multi-replica correctness move history to Redis and profiles to a real store.
- No auth on the API — front it with your API gateway / service mesh mTLS.
- Rules are heuristic samples for demonstration, not tuned fraud models.
