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
k8s/                namespace, serviceaccount, configmap, deployment, hpa, service, targetgroupbinding  (envsubst templated)
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

| Object | Name | Notes |
|--------|------|-------|
| Namespace  | `${K8S_NAMESPACE}` (default `fraud`) | |
| Deployment | `fraud-service` | no `spec.replicas` (owned by the HPA), probes on `/actuator/health/*`, image `REPLACE_ME/fraud-service:${IMAGE_TAG}` (CI rewrites via `kubectl set image`) |
| HPA        | `fraud-service` | `autoscaling/v2`, CPU 70% / mem 80%, `minReplicas=${HPA_MIN}` (2) … `maxReplicas=${HPA_MAX}` (10); needs metrics-server |
| Service    | `fraud-service` | ClusterIP, `80 → 8080` |
| TargetGroupBinding | `fraud-service` | registers pod IPs into the **pre-provisioned** ALB target group (`${ALB_TARGET_GROUP_ARN}`) |

> The Deployment intentionally omits `spec.replicas` so the HPA is the sole owner of
> the count — otherwise every `kubectl apply` would reset it and fight the autoscaler.
> Pod count starts at 1 and the HPA scales to `minReplicas` within ~30s.
> Node capacity for scaled-up pods is handled by Cluster Autoscaler / Karpenter in
> `fdp-infra-compute`, not here.

### The ALB (`k8s/targetgroupbinding.yaml`)

`fdp-infra-compute` **pre-provisions the whole ALB** — load balancer, listener,
target group, security groups, health check — and the AWS Load Balancer Controller
(IRSA role `fdp-dev-euw2-eks-alb-controller`). This repo does not create an Ingress.

The `TargetGroupBinding` CRD points the existing target group
(`terraform output alb_target_group_arn`) at the `fraud-service` Service; the
controller then keeps that target group's IP targets in sync with the pods.

The target group's **health check** is defined in `fdp-infra-compute` — it must be
`HTTP /actuator/health/readiness` on the traffic port, or targets stay `unhealthy`.
The SG rule ALB → nodes/pods:8080 is also owned there.

### Manual apply

```bash
export AWS_REGION=eu-west-2
export EKS_CLUSTER_NAME=fdp-dev-euw2-eks
export K8S_NAMESPACE=fraud
export HPA_MIN=2 HPA_MAX=10
export APP_ENV=dev
export LOG_LEVEL_APP=INFO
export IMAGE_TAG=$(git rev-parse --short=12 HEAD)
export IMAGE=$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/fdp/fraud-detection-service-repo:$IMAGE_TAG
# from `terraform output` in fdp-infra-compute:
export ALB_TARGET_GROUP_ARN=arn:aws:elasticloadbalancing:eu-west-2:861477414666:targetgroup/fdp-dev-euw2-eks-fraud/12ab4788af4f738c
# ConfigMap tunables (override as needed)
export FRAUD_DECISION_CHALLENGE_SCORE=40 FRAUD_DECISION_BLOCK_SCORE=80
export FRAUD_RULES_HIGH_VALUE_ENABLED=true FRAUD_RULES_VELOCITY_ENABLED=true
export FRAUD_RULES_VELOCITY_MAX_TRANSACTIONS=4 FRAUD_RULES_VELOCITY_WINDOW_SECONDS=120
export FRAUD_RULES_LOCATION_DEVIATION_ENABLED=true
export FRAUD_RULES_FAILED_THEN_SUCCESS_ENABLED=true FRAUD_RULES_SUSPICIOUS_TRANSFER_ENABLED=true

aws eks update-kubeconfig --name "$EKS_CLUSTER_NAME" --region "$AWS_REGION"

for f in namespace configmap deployment hpa service targetgroupbinding; do
  envsubst < "k8s/$f.yaml" | kubectl apply -f -
done
kubectl -n "$K8S_NAMESPACE" set image deployment/fraud-service fraud-service="$IMAGE"
kubectl -n "$K8S_NAMESPACE" rollout status deployment/fraud-service --timeout=240s
kubectl -n "$K8S_NAMESPACE" get hpa fraud-service
```

### Validation

```bash
kubectl -n fraud rollout status deploy/fraud-service
kubectl -n fraud get targetgroupbinding fraud-service
kubectl -n fraud describe targetgroupbinding fraud-service   # controller events

# targets should become "healthy" within ~30s
aws elbv2 describe-target-health --region eu-west-2 \
  --target-group-arn "$ALB_TARGET_GROUP_ARN" \
  --query 'TargetHealthDescriptions[].{ip:Target.Id,port:Target.Port,state:TargetHealth.State}' --output table
```

Smoke test — the ALB is **internal**, so call it from a host/pod inside the VPC:

```bash
ALB=internal-fdp-dev-euw2-eks-int-430021111.eu-west-2.elb.amazonaws.com
kubectl -n fraud run curl --rm -it --image=curlimages/curl --restart=Never -- \
  sh -c "curl -s http://$ALB/api/v1/fraud/rules"
```

---

## Audit trail (DynamoDB, async)

Every evaluated transaction (request + decision) is written to DynamoDB on a
dedicated executor **after** the response is returned — it never adds latency to,
or can fail, the decision. Off by default (`AUDIT_DYNAMODB_ENABLED=false`), so
local runs and tests need no AWS.

**Table `fdp-audit-table`** — partition key **`transactionId`** (S); primary access
pattern is look-up by transaction. Optional GSI `customerId-evaluatedAt-index`
(HASH `customerId` S, RANGE `evaluatedAt` S) for "all evaluations for a customer,
time-ordered" — the code already writes those attributes; add the GSI when you
need it. Enable TTL on attribute **`ttl`** (epoch seconds, `AUDIT_DYNAMODB_TTL_DAYS`,
default 400).

Recreate the table with the transaction key:

```bash
aws dynamodb delete-table --region eu-west-2 --table-name fdp-audit-table   # if the old one exists
aws dynamodb create-table --region eu-west-2 --table-name fdp-audit-table \
  --billing-mode PAY_PER_REQUEST \
  --attribute-definitions AttributeName=transactionId,AttributeType=S \
  --key-schema AttributeName=transactionId,KeyType=HASH
aws dynamodb update-time-to-live --region eu-west-2 --table-name fdp-audit-table \
  --time-to-live-specification "Enabled=true,AttributeName=ttl"
```

**IRSA** — the pod runs as ServiceAccount `fraud-service` ([`k8s/serviceaccount.yaml`](k8s/serviceaccount.yaml)).
Create an IAM role trusting the cluster OIDC provider with this policy, and pass
its ARN as `AUDIT_IRSA_ROLE_ARN`:

```json
{ "Version": "2012-10-17", "Statement": [{
  "Effect": "Allow", "Action": "dynamodb:PutItem",
  "Resource": "arn:aws:dynamodb:eu-west-2:861477414666:table/fdp-audit-table" }] }
```

**Enable** (repo Variables): `AUDIT_DYNAMODB_ENABLED=true`,
`AUDIT_DYNAMODB_TABLE_NAME=fdp-audit-table`, `AUDIT_IRSA_ROLE_ARN=<role arn>`
(`AWS_REGION` already set).

### Testing the audit trail

```bash
# 1. send a few transactions through the ALB / API Gateway (see above), e.g.
curl -s -XPOST http://$ALB/api/v1/fraud/evaluate -H 'Content-Type: application/json' \
  -d '{"transactionId":"AUD-1","customerId":"CUST1002","type":"CREDIT_CARD","amount":1250000}'

# 2. read the row straight back by transactionId
aws dynamodb get-item --region eu-west-2 --table-name fdp-audit-table \
  --key '{"transactionId":{"S":"AUD-1"}}'

# 3. or scan recent rows
aws dynamodb scan --region eu-west-2 --table-name fdp-audit-table --max-items 10
```

Local test without EKS: `AUDIT_DYNAMODB_ENABLED=true AUDIT_DYNAMODB_TABLE_NAME=fdp-audit-table
AWS_REGION=eu-west-2 mvn spring-boot:run` with AWS creds in the environment, then
POST to `localhost:8080` and `get-item` as above.

If a write fails it is logged (`Audit write failed for txn …`) and the API response
is unaffected.

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
   - `envsubst` the `k8s/*.yaml` (namespace, serviceaccount, configmap, deployment, hpa, service, targetgroupbinding) and `kubectl apply`
   - `kubectl set image` to the pushed ECR tag, then `kubectl rollout status`

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

`AWS_REGION`, `ECR_REPOSITORY`, `EKS_CLUSTER_NAME`, `K8S_NAMESPACE`, `HPA_MIN`, `HPA_MAX`,
`ALB_TARGET_GROUP_ARN`, `APP_ENV`, `LOG_LEVEL_APP`,
`AUDIT_DYNAMODB_ENABLED`, `AUDIT_DYNAMODB_TABLE_NAME`, `AUDIT_IRSA_ROLE_ARN`,
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
