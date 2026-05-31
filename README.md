# PayCycle — Enterprise-Grade Multi-Tenant Subscription & Automated Billing Engine

PayCycle is a high-performance, resilient, and enterprise-ready multi-tenant subscription management and automated billing engine built using **Java 17, Spring Boot 3, and MySQL**.

The engine is architected to safely govern complex cyclic consumer payments, prevent duplicate financial charges using high-speed cryptographic idempotency layers, ensure event-driven data consistency via the **Transactional Outbox Pattern**, and enforce strict tenant-level data isolation.

---

# 🚀 Key Features

* Multi-tenant schema isolation
* Subscription lifecycle state machine
* Cryptographic idempotency protection
* Transactional Outbox Pattern
* Exponential backoff retry scheduler
* JWT-based authentication & authorization
* Fault-tolerant payment retry processing
* Production-grade architecture patterns
* Integration testing using Testcontainers

---

# 🏗️ Core Architectural Features & Design Patterns

## 1. Multi-Tenant Schema Isolation

To guarantee complete security and data privacy across enterprise boundaries, PayCycle employs a strict tenant partitioning strategy.

### Highlights

* Every business workspace is mapped to a unique tenant.
* All critical database entities are tenant-scoped.
* Cross-tenant data access is blocked at the persistence layer.
* Ensures strong enterprise-grade data isolation.

---

## 2. Deterministic Subscription State Machine

Subscription lifecycle transitions are governed by a strict internal state machine to prevent invalid billing states.

### Supported Lifecycle Flow

```text
TRIALING → ACTIVE → PAUSED → PAST_DUE → CANCELLED
```

### Safety Mechanisms

* Invalid state transitions are blocked automatically.
* Contract resurrection from `CANCELLED` requires a new billing setup.
* Transition guards maintain transactional consistency.

---

## 3. Cryptographic Idempotency Layer (Double-Charge Prevention)

PayCycle protects billing infrastructure from:

* Rapid frontend multi-clicks
* Network retries
* Duplicate payment requests

### Mechanism

The engine computes a deterministic SHA-256 signature using:

```text
Idempotency-Key + Tenant-ID + Payload JSON
```

### Benefits

* Duplicate requests return cached responses instantly.
* Prevents accidental financial double-charging.
* Improves API reliability under retry storms.

---

## 4. Transactional Outbox Pattern

Instead of directly publishing events during business transactions, PayCycle uses the **Transactional Outbox Pattern**.

### Workflow

1. Business state changes are committed.
2. Corresponding event records are stored in the outbox table.
3. Background workers asynchronously process pending events.

### Advantages

* Reliable event delivery
* Eliminates distributed transaction issues
* Prevents message loss
* Improves microservice consistency

---

## 5. Fault-Tolerant Exponential Backoff Scheduler

Failed payment retries use intelligent retry scheduling rather than fixed intervals.

### Retry Formula

[
\text{Next Retry Interval} = 2^{\text{retryCount}} \text{ Days}
]

### Example

| Retry Attempt | Delay   |
| ------------- | ------- |
| 1             | 2 Days  |
| 2             | 4 Days  |
| 3             | 8 Days  |
| 4             | 16 Days |

### Failure Handling

* Maximum retry limits are configurable per billing plan.
* Subscriptions exceeding retry thresholds transition safely to `CANCELLED`.

---

# 🗺️ High-Level Architecture

```text
Client Request
       │
       ▼
API Gateway / Controllers
       │
       ▼
Authentication & JWT Validation
       │
       ▼
Tenant Resolution Layer
       │
       ▼
Subscription Service Layer
       │
       ├── State Machine Validation
       ├── Idempotency Verification
       ├── Billing Processing
       └── Retry Scheduling
       │
       ▼
MySQL Transaction
       │
       ├── Domain Entity Persistence
       └── Outbox Event Persistence
       │
       ▼
Outbox Worker
       │
       ▼
Webhook / Queue / Notification Delivery
```

---

# 🛠️ Tech Stack

| Category            | Technology            |
| ------------------- | --------------------- |
| Language            | Java 17               |
| Framework           | Spring Boot 3         |
| ORM                 | Spring Data JPA       |
| Security            | Spring Security + JWT |
| Database            | MySQL 8               |
| Build Tool          | Maven 3               |
| Testing             | JUnit 5               |
| Mocking             | Mockito               |
| Assertions          | AssertJ               |
| Integration Testing | Testcontainers        |

---

# 📦 Project Structure

```text
src
├── main
│   ├── java
│   │   └── com.paycycle
│   │       ├── auth
│   │       ├── billing
│   │       ├── subscription
│   │       ├── tenant
│   │       ├── outbox
│   │       ├── scheduler
│   │       ├── idempotency
│   │       ├── security
│   │       └── common
│   └── resources
│       └── application.yml
│
└── test
    ├── integration
    └── unit
```

---

# ⚙️ Local Development Setup

## 1. Clone Repository

```bash
git clone https://github.com/yourusername/billing-engine.git
cd billing-engine
```

---

## 2. Start Dependencies via Docker

```bash
docker-compose up -d
```

---

## 3. Configure Environment Variables

Update `src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/paycycle_billing?useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: your_secure_password

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

---

## 4. Build the Project

```bash
mvn clean package -DskipTests
```

---

## 5. Run the Application

```bash
mvn spring-boot:run
```

---

# 🧪 Running Tests

## Execute Complete Test Suite

```bash
mvn test
```

## Run Specific Unit Tests

```bash
mvn test -Dtest="SubscriptionServiceTest,BillingCycleServiceTest,IdempotencyServiceTest"
```

---

# 🔐 Authentication

PayCycle uses JWT-based authentication.

### Authentication Flow

1. Register tenant admin
2. Login with credentials
3. Receive JWT token
4. Attach token in protected API requests

```http
Authorization: Bearer <jwt-token>
```

---

# 🛣️ REST API Endpoints

| Method | Endpoint                            | Description                | Auth Required |
| ------ | ----------------------------------- | -------------------------- | ------------- |
| POST   | `/api/v1/auth/register`             | Register new tenant admin  | ❌             |
| POST   | `/api/v1/auth/login`                | Generate JWT token         | ❌             |
| POST   | `/api/v1/subscriptions`             | Create new subscription    | ✅             |
| POST   | `/api/v1/subscriptions/{id}/pause`  | Pause active subscription  | ✅             |
| POST   | `/api/v1/subscriptions/{id}/resume` | Resume paused subscription | ✅             |
| DELETE | `/api/v1/subscriptions/{id}`        | Cancel subscription        | ✅             |

---

# 🔄 Idempotent Request Example

```http
POST /api/v1/subscriptions
Idempotency-Key: 91c7bcb2-24c7-45f2
Tenant-ID: tenant_alpha
```

Duplicate requests with identical payloads automatically return the cached response without reprocessing payment logic.

---

# 📈 Scalability Considerations

PayCycle is designed for horizontal scalability and cloud-native deployment.

### Supported Enterprise Patterns

* Stateless service instances
* Async event processing
* Background schedulers
* Retry-safe APIs
* Tenant-aware partitioning
* Containerized deployments

---

# 🧠 Engineering Principles

* Clean Architecture
* SOLID Principles
* Domain-Driven Design (DDD)
* Event-Driven Architecture
* Fail-Safe Distributed Systems
* Transactional Integrity
* High Cohesion & Loose Coupling

---

# 🚀 Future Enhancements

* Kafka/RabbitMQ integration
* Stripe/Razorpay adapters
* Distributed rate limiting
* Metrics & observability dashboards
* OpenTelemetry tracing
* Kubernetes deployment manifests
* GraphQL APIs
* Multi-region failover support

---

# 🤝 Contributing

Contributions are welcome.

### Development Workflow

1. Fork the repository
2. Create a feature branch
3. Commit changes
4. Push branch
5. Open pull request

---

# 📄 License

This project is licensed under the MIT License.

---

# 👨‍💻 Author

Developed with enterprise architecture principles using Spring Boot, distributed systems patterns, and resilient billing infrastructure concepts.
