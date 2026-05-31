# PayCycle — Enterprise-Grade Multi-Tenant Subscription & Automated Billing Engine

<p align="center">
  <b>High-Performance Subscription Billing Infrastructure Built with Spring Boot</b>
</p>

<p align="center">
  Java 17 • Spring Boot 3.3.2 • MySQL 8 • Redis 7 • JWT • Flyway • Testcontainers
</p>

---

# 🚀 Overview

PayCycle is a high-performance, resilient, and enterprise-grade multi-tenant subscription management and automated recurring billing engine built using **Java 17, Spring Boot 3.3.2, MySQL 8.0, and Redis 7**.

The system is architected to safely manage complex recurring payment workflows, prevent duplicate financial transactions using cryptographic idempotency protection, guarantee distributed consistency through the **Transactional Outbox Pattern**, and enforce strict tenant-level data isolation for enterprise-scale SaaS platforms.

---

# ✨ Core Features

* 🔐 Zero-leak multi-tenant architecture
* 💳 Automated recurring subscription billing
* ⚡ Cryptographic idempotency protection
* 🔄 Transactional Outbox Pattern
* 🧠 Deterministic subscription state machine
* 🚦 Optimistic & pessimistic locking strategies
* 📦 Flyway-based schema migrations
* 🧪 Integration testing with Testcontainers
* 🔑 Stateless JWT authentication
* 📈 Exponential retry & billing recovery logic
* 🚀 Redis-backed distributed coordination

---

# 🏗️ Architecture & Design Patterns

## 1. Zero-Leak Multi-Tenant Isolation

PayCycle implements strict tenant-aware security boundaries across the entire request lifecycle.

### Key Highlights

* Every critical entity maps to a unique `Tenant`.
* Tenant context is resolved during JWT authentication.
* Cross-tenant data access is blocked globally.
* Prevents accidental data leakage across organizations.

---

## 2. Deterministic Subscription State Machine

Subscription lifecycle transitions are protected through a guarded finite-state machine.

### Supported Workflow

```text
TRIALING → ACTIVE → PAUSED → PAST_DUE → CANCELLED
```

### Protection Rules

* Invalid transitions are rejected instantly.
* Cancelled subscriptions cannot be reactivated directly.
* All state mutations pass transactional validation guards.

---

## 3. Cryptographic Idempotency Layer

To eliminate duplicate payment processing caused by:

* Frontend multi-clicks
* Retry storms
* Network instability

PayCycle introduces a SHA-256 based idempotency engine.

### Signature Composition

```text
X-Idempotency-Key + Tenant-ID + Request Payload
```

### Engine Behavior

* Duplicate requests return cached responses instantly.
* Payload mismatches with identical keys trigger:

```http
HTTP 422 Unprocessable Entity
```

* Prevents financial double-charging safely.

---

## 4. Transactional Outbox Pattern

To maintain distributed consistency between database transactions and external events:

### Atomic Workflow

1. Business changes are persisted
2. Matching outbox events are stored
3. Background workers asynchronously publish events

### Benefits

* Prevents lost events
* Guarantees eventual consistency
* Eliminates distributed transaction failures

---

## 5. Concurrency Control & Retry Resilience

### Optimistic Locking

Used heavily for:

* Subscription state updates
* Concurrent billing workflows

```java
@Version
private Long version;
```

### Pessimistic Locking

Used for:

* Wallet mutations
* Payment deduction flows
* High-contention financial operations

```sql
SELECT * FROM wallet WHERE id=? FOR UPDATE;
```

---

# 📈 Exponential Retry Strategy

Failed invoice retries are scheduled dynamically using exponential backoff.

\text{Next Retry Interval} = 2^{\text{retryCount}}\ \text{Days}

### Example

| Retry Attempt | Delay   |
| ------------- | ------- |
| 1             | 2 Days  |
| 2             | 4 Days  |
| 3             | 8 Days  |
| 4             | 16 Days |

Subscriptions exceeding retry thresholds automatically transition into a `CANCELLED` state.

---

# 🗺️ High-Level Request Processing Pipeline

```text
Incoming Request
       │
       ▼
JWT Security Filter
       │
       ▼
Tenant Context Resolution
       │
       ▼
Idempotency Filter
       │
 ┌─────┴───────────┐
 │                 │
 ▼                 ▼
Cache HIT      Cache MISS
 │                 │
 ▼                 ▼
Return Cached   Core Business Logic
Response             │
                     ▼
        Subscription State Machine
                     │
                     ▼
             Billing Engine
                     │
                     ▼
     Atomic Database Transaction
      ├── Domain Changes
      └── Outbox Event Insert
                     │
                     ▼
        Async Outbox Publisher
                     │
                     ▼
        External Queue/Webhook
```

---

# 🛠️ Tech Stack

| Layer               | Technology            |
| ------------------- | --------------------- |
| Language            | Java 17               |
| Framework           | Spring Boot 3.3.2     |
| Database            | MySQL 8.0             |
| Cache & Locks       | Redis 7 + Redisson    |
| ORM                 | Spring Data JPA       |
| Security            | Spring Security + JWT |
| Migration           | Flyway                |
| Testing             | JUnit 5               |
| Mocking             | Mockito               |
| Assertions          | AssertJ               |
| Integration Testing | Testcontainers        |
| Build Tool          | Maven                 |

---

# 📂 Project Structure

```text
src
├── main
│   ├── java/com/paycycle
│   │   ├── auth
│   │   ├── billing
│   │   ├── subscription
│   │   ├── tenant
│   │   ├── outbox
│   │   ├── idempotency
│   │   ├── scheduler
│   │   ├── security
│   │   └── common
│   │
│   └── resources
│       ├── application.yml
│       └── db/migration
│
└── test
    ├── integration
    └── unit
```

---

# ⚙️ Local Development Setup

## 1. Clone Repository

```bash
git clone https://github.com/yourusername/paycycle.git
cd paycycle
```

---

## 2. Start Infrastructure Containers

```bash
docker compose up -d
```

Verify running containers:

```bash
docker compose ps
```

---

## 3. Configure Application Properties

Update:

```text
src/main/resources/application.yml
```

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/paycycle_db?useSSL=false&allowPublicKeyRetrieval=true
    username: paycycle_user
    password: paycycle_pass

    hikari:
      pool-name: PayCycle-HikariPool-Dev
      maximum-pool-size: 15

  jpa:
    hibernate:
      ddl-auto: validate

    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

---

## 4. Build & Run Application

```bash
mvn clean compile spring-boot:run
```

Expected startup log:

```text
Flyway: Successfully applied migration to schema 'paycycle_db'
```

---

# 🧪 Testing

## Run Complete Test Suite

```bash
mvn test
```

## Run Specific Components

```bash
mvn test -Dtest="SubscriptionServiceTest,BillingCycleServiceTest,IdempotencyServiceTest"
```

---

# 🔐 Authentication

PayCycle uses stateless JWT authentication.

### Authorization Header

```http
Authorization: Bearer <jwt-token>
```

---

# 🌐 Core REST APIs

| Method | Endpoint                            | Description           | Security         |
| ------ | ----------------------------------- | --------------------- | ---------------- |
| POST   | `/api/v1/auth/register`             | Register tenant owner | Public           |
| POST   | `/api/v1/auth/login`                | Generate JWT token    | Public           |
| POST   | `/api/v1/subscriptions`             | Create subscription   | JWT + Idempotent |
| POST   | `/api/v1/subscriptions/{id}/pause`  | Pause subscription    | JWT              |
| POST   | `/api/v1/subscriptions/{id}/resume` | Resume subscription   | JWT              |
| DELETE | `/api/v1/subscriptions/{id}`        | Cancel subscription   | JWT              |

---

# 🔄 Idempotent API Example

```http
POST /api/v1/subscriptions

X-Idempotency-Key: 2f3f4c12-7ab2-49aa
Authorization: Bearer <jwt-token>
```

If the same request is retried with the same payload:

* Cached response is returned
* Billing engine is NOT triggered twice

---

# 🧠 Engineering Principles

* Clean Architecture
* SOLID Principles
* Event-Driven Design
* Domain-Oriented Service Boundaries
* Fail-Safe Distributed Systems
* High Cohesion & Low Coupling
* Infrastructure Resilience Patterns

---

# 🚀 Future Enhancements

* Kafka / RabbitMQ integration
* Stripe & Razorpay payment adapters
* OpenTelemetry distributed tracing
* Prometheus + Grafana monitoring
* Kubernetes deployment manifests
* API rate limiting
* Multi-region failover replication
* Saga orchestration support

---

# 🤝 Contributing

Contributions are welcome.

### Contribution Flow

1. Fork repository
2. Create feature branch
3. Commit changes
4. Push branch
5. Open pull request

---

# 🛡️ License

This project is licensed under the MIT License.

---

# 👨‍💻 Author

Built with enterprise-grade distributed systems architecture principles using Spring Boot, resilient billing workflows, and modern backend infrastructure patterns.
