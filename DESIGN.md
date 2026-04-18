# DESIGN.md

pay-service 도메인 설계 문서. 모든 설계 결정과 합의 사항은 이 문서에 기록합니다.

---

## 1. 유비쿼터스 언어 (Ubiquitous Language)

| 용어 | 영문 | 정의 |
|------|------|------|
| 지갑 | Wallet | 사용자가 보유한 선불 잔액 계정 |
| 거래 | Transaction | 잔액 변동의 원자적 기록 (충전/차감/환불) |
| 결제 | Payment | 외부 가맹점에 대한 단건 결제 행위 |
| PG 결제 | PgPayment | 외부 PG사와 주고받은 승인/취소 이력 (요약) |
| PG 로그 | PgPaymentLog | PG사와 주고받은 원본 요청/응답 (MongoDB) |
| 정산 | Settlement | 가맹점에게 정산금을 지급하는 프로세스 |
| 감사 로그 | AuditLog | 모든 도메인 행위의 before/after 스냅샷 (MongoDB) |
| 멱등키 | Idempotency Key | 동일 요청의 중복 실행 방지를 위한 클라이언트 지정 식별자 |
| 가맹점 ID | Merchant ID | 결제를 받는 주체 식별자 (Phase 2에서 Merchant Aggregate로 확장) |

---

## 2. 모듈 구성

```
pay-service
├── wallet         # 지갑 - 잔액, 충전, 차감
├── payment        # 결제 - 승인, 취소, PG 연동
├── settlement     # 정산 - Kafka Consumer, 정산 집계 (Phase 2 확장 지점)
├── notification   # 알림 - 이벤트 수신 후 알림 발송 (스켈레톤)
├── audit          # 감사 로그 - 도메인 이벤트 → MongoDB 적재
├── auth           # 인증/인가 (JWT)
└── user           # 사용자 (최소 구현)
```

### 모듈 의존 방향
```
presentation → application → domain ← infrastructure
```
모듈 간 직접 의존 없음. 조율은 상위 Facade + 도메인 이벤트로.

---

## 3. 데이터 저장소 전략 (Polyglot Persistence)

데이터 특성에 따라 저장소를 분리한다.

| 저장소 | 용도 | 이유 |
|--------|------|------|
| **MySQL 8** | Wallet, Payment, Settlement, Outbox, Idempotency | ACID 트랜잭션, 락, 정합성 |
| **MongoDB 7** | PG Payment Logs, Audit Logs | 가변 스키마, Append-only, 대용량 비정형 |
| **Redis 7** | 분산락, 캐시, Rate Limit | 빠른 임시 저장 |

### 절대 원칙
- **잔액/결제 상태는 항상 MySQL이 Source of Truth**. MongoDB는 보조 저장소.
- MongoDB 적재 실패가 결제 트랜잭션을 막아서는 안 된다 (비동기 처리).
- MySQL ↔ MongoDB 사이 정합성은 **이벤트 기반**으로 수렴한다 (Outbox → Kafka → MongoDB).

---

## 4. Aggregate 설계

### 4.1 Wallet Aggregate (MySQL)

**책임**: 사용자의 잔액을 관리하고 모든 변동 이력을 보존한다.

```
Wallet (Aggregate Root)
├── id: WalletId
├── userId: Long
├── balance: Money
├── status: WalletStatus (ACTIVE, FROZEN, CLOSED)
├── version: Long (낙관적 락용)
└── createdAt, updatedAt

Transaction (Entity, Wallet 내부)
├── id: TransactionId
├── walletId: WalletId
├── type: TransactionType (CHARGE, DEDUCT, REFUND)
├── amount: Money
├── referenceId: String (Payment.id 등 외부 ID)
├── referenceType: String (PAYMENT, CHARGE_REQUEST 등)
└── createdAt
```

**불변식**
- `balance >= 0`
- `status == ACTIVE`일 때만 차감 가능
- 모든 잔액 변경은 Transaction 기록을 동반한다

**주요 메서드**
```java
Transaction charge(Money amount, String referenceId)
Transaction deduct(Money amount, String referenceId)
Transaction refund(Money amount, String referenceId)
void freeze()
void close()
```

**발행 이벤트**
- `WalletCharged`
- `WalletDeducted`
- `WalletRefunded`

---

### 4.2 Payment Aggregate (MySQL)

**책임**: 가맹점에 대한 결제 요청의 전체 생명주기를 관리한다.

```
Payment (Aggregate Root)
├── id: PaymentId
├── userId: Long
├── merchantId: String
├── amount: Money
├── status: PaymentStatus (PENDING, APPROVED, FAILED, CANCELLED)
├── idempotencyKey: String (unique)
├── walletTransactionId: TransactionId (잔액 차감 Transaction 참조)
├── failureReason: String
└── createdAt, approvedAt, cancelledAt

PgPayment (Entity, Payment 내부) ── MySQL에는 요약만
├── id: PgPaymentId
├── pgProvider: PgProvider (TOSS)
├── pgTxId: String (PG사가 발급한 거래 ID)
├── status: PgStatus (REQUESTED, APPROVED, FAILED)
├── pgLogId: String  ← MongoDB PgPaymentLog 참조
└── createdAt
```

**상태 전이**
```
PENDING ─→ APPROVED ─→ CANCELLED
   │
   └─→ FAILED
```

**불변식**
- `idempotencyKey`는 unique
- `APPROVED` 상태에서만 취소 가능
- `FAILED`, `CANCELLED`는 최종 상태

**발행 이벤트**
- `PaymentApproved`
- `PaymentFailed`
- `PaymentCancelled`

---

### 4.3 Settlement Aggregate (MySQL, 스켈레톤)

**책임**: 가맹점 단위로 결제를 집계하여 정산 대상을 관리한다.

```
Settlement (Aggregate Root)
├── id: SettlementId
├── merchantId: String
├── targetDate: LocalDate
├── totalAmount: Money
├── feeAmount: Money
├── netAmount: Money
├── status: SettlementStatus (PENDING, COMPLETED)
└── createdAt, completedAt

SettlementItem (Entity)
├── paymentId: String
├── amount: Money
├── fee: Money
└── netAmount: Money
```

Phase 1에서는 `PaymentApproved` 이벤트 수신 후 `SettlementItem`만 적재. 실제 정산 지급 로직은 Phase 2.

---

### 4.4 PgPaymentLog (MongoDB)

**책임**: PG사와 주고받은 모든 요청/응답 원본을 보존한다.

**컬렉션**: `pg_payment_logs`

```javascript
{
  _id: ObjectId(...),
  paymentId: "payment-123",         // Payment.id (인덱스)
  pgPaymentId: 456,                 // MySQL PgPayment.id
  pgProvider: "TOSS",
  requestType: "APPROVE",           // APPROVE | CANCEL | INQUIRY
  request: {
    headers: { ... },
    body: { ... }
  },
  response: {                       // PG 원본 응답 (스키마 가변)
    httpStatus: 200,
    headers: { ... },
    body: { ... }
  },
  durationMs: 234,
  traceId: "abc-123",
  createdAt: ISODate(...)
}
```

**인덱스**
- `paymentId` (단일)
- `createdAt` (TTL 옵션 — 90일 후 cold storage 이전 가능)
- `(pgProvider, createdAt)` 복합

**적재 방식**
- PG 호출 직후 **동기 저장**. PG 호출 실패 시에도 시도 기록은 남겨야 하므로 try-finally로 보장.
- 단, MongoDB 저장 실패가 결제 트랜잭션을 깨뜨려선 안 됨 → **별도 트랜잭션, 실패는 로그만 남기고 swallow**.

---

### 4.5 AuditLog (MongoDB)

**책임**: 모든 도메인 행위의 before/after 스냅샷을 보존한다 (감사/규제 대응).

**컬렉션**: `audit_logs`

```javascript
{
  _id: ObjectId(...),
  traceId: "abc-123",               // 분산 추적
  eventId: "evt-uuid",              // 멱등성 (중복 적재 방지)
  action: "PAYMENT_APPROVED",       // 도메인 행위명
  resource: {
    type: "Payment",
    id: "payment-123"
  },
  actor: {
    type: "USER",                   // USER | SYSTEM | ADMIN
    id: 1001,
    ip: "1.2.3.4"
  },
  before: { status: "PENDING" },    // 변경 전 스냅샷 (자유 형식)
  after: { status: "APPROVED" },    // 변경 후 스냅샷
  metadata: { ... },                // 추가 컨텍스트
  occurredAt: ISODate(...)
}
```

**인덱스**
- `eventId` (unique — 멱등성)
- `(resource.type, resource.id, occurredAt)`
- `(actor.id, occurredAt)`

**적재 방식**
- **Kafka 이벤트 기반**. 모든 도메인 이벤트를 audit Consumer가 수신하여 적재.
- Consumer는 `eventId` 기반 멱등성 보장.
- 결제 트랜잭션과 완전히 분리 → 적재 실패가 비즈니스에 영향 없음.

---

## 5. 핵심 플로우

### 5.1 지갑 충전 (가상)

```
[Client]
   │ POST /wallets/{id}/charge { amount, idempotencyKey }
   ▼
[WalletService.charge]  ─── @Transactional (MySQL)
   ├─ idempotencyKey 중복 확인
   ├─ Wallet 조회 (SELECT FOR UPDATE)
   ├─ wallet.charge(amount, referenceId)
   │     └─ Transaction 생성 + balance += amount
   ├─ Outbox에 WalletCharged 이벤트 저장
   └─ 응답 반환

[OutboxPublisher] → Kafka: wallet.charged
[AuditConsumer] → MongoDB audit_logs 적재
```

---

### 5.2 결제 승인 (핵심 플로우)

```
[Client]
   │ POST /payments { userId, merchantId, amount, idempotencyKey }
   ▼
[PaymentFacade.request]  ─── @Transactional (MySQL)
   ├─ 1. idempotency 체크
   ├─ 2. Payment.request() → PENDING 저장
   ├─ 3. Wallet 잔액 차감 (SELECT FOR UPDATE)
   │     └─ Transaction(DEDUCT) 기록
   ├─ 4. [트랜잭션 커밋]
   │
   ├─ 5. PG 승인 요청 (외부 API, 트랜잭션 외부)
   │     │
   │     ├─ [요청/응답을 MongoDB pg_payment_logs에 저장]
   │     │   └─ (성공/실패 무관, try-finally)
   │     │
   │     ├─ 성공 → PaymentFacade.approve()  ─── @Transactional (MySQL)
   │     │         ├─ Payment → APPROVED
   │     │         ├─ PgPayment 저장 (pgLogId 포함)
   │     │         └─ Outbox: PaymentApproved 저장
   │     │
   │     └─ 실패 → PaymentFacade.fail()      ─── @Transactional (MySQL)
   │               ├─ Payment → FAILED
   │               ├─ Wallet 환불 (Transaction REFUND)
   │               └─ Outbox: PaymentFailed 저장
   │
   └─ 응답 반환

[OutboxPublisher] → Kafka 발행

[Consumers]
   ├─ SettlementConsumer → SettlementItem 적재 (MySQL)
   ├─ NotificationConsumer → 알림 발송
   └─ AuditConsumer → audit_logs 적재 (MongoDB)
```

**주요 설계 결정**
- **선차감 방식**: PG 승인 전 잔액 차감, 실패 시 환불로 복구
- **트랜잭션 분리**: PG 호출은 트랜잭션 외부 (외부 호출이 DB 커넥션 점유 방지)
- **Outbox 패턴**: DB 커밋과 Kafka 발행의 원자성 보장
- **PG 로그는 동기 / Audit 로그는 비동기**: PG 로그는 장애 분석에 즉시 필요, Audit은 결제 성능에 영향 주면 안 됨

---

### 5.3 결제 취소

```
[Client]
   │ POST /payments/{id}/cancel
   ▼
[PaymentFacade.cancel]  ─── @Transactional (MySQL)
   ├─ Payment 조회 (APPROVED 상태만 가능)
   ├─ PG 취소 요청 (외부 API)
   │   └─ MongoDB pg_payment_logs 적재 (CANCEL 타입)
   ├─ Payment → CANCELLED
   ├─ Wallet 환불 (Transaction REFUND)
   └─ Outbox: PaymentCancelled 저장
```

---

## 6. 이벤트 카탈로그

모든 이벤트는 Outbox를 거쳐 Kafka로 발행된다.

### Kafka Topics
| Topic | Partition | Key | 발행 시점 |
|-------|-----------|-----|----------|
| `wallet.charged` | 3 | userId | 충전 완료 |
| `wallet.deducted` | 3 | userId | 차감 완료 |
| `payment.approved` | 3 | userId | 결제 승인 완료 |
| `payment.failed` | 3 | userId | 결제 실패 |
| `payment.cancelled` | 3 | userId | 결제 취소 완료 |
| `settlement.completed` | 3 | merchantId | 정산 완료 (Phase 2) |

### 이벤트 공통 필드
```json
{
  "eventId": "uuid",
  "eventType": "PaymentApproved",
  "occurredAt": "2026-04-18T10:00:00Z",
  "aggregateId": "payment-123",
  "aggregateType": "Payment",
  "traceId": "abc-123",
  "payload": { /* 이벤트별 고유 필드 */ },
  "snapshot": {                       /* AuditLog용 before/after */
    "before": { ... },
    "after": { ... }
  }
}
```

### Consumer별 구독 토픽
| Consumer | 구독 토픽 | 처리 | 저장소 |
|----------|----------|------|--------|
| SettlementConsumer | `payment.approved`, `payment.cancelled` | SettlementItem 적재/취소 | MySQL |
| NotificationConsumer | 전체 | 알림 발송 | MySQL (이력) |
| **AuditConsumer** | **전체** | **audit_logs 적재** | **MongoDB** |

---

## 7. 주요 테이블/컬렉션 스키마 (요약)

### MySQL

#### wallets
```sql
id BIGINT PK
user_id BIGINT UNIQUE
balance BIGINT NOT NULL
status VARCHAR(20)
version BIGINT
created_at, updated_at
INDEX idx_user_id
```

#### wallet_transactions
```sql
id BIGINT PK
wallet_id BIGINT FK
type VARCHAR(20)
amount BIGINT
reference_id VARCHAR(100)
reference_type VARCHAR(50)
created_at
INDEX idx_wallet_created (wallet_id, created_at)
```

#### payments
```sql
id VARCHAR(40) PK
user_id BIGINT
merchant_id VARCHAR(50)
amount BIGINT
status VARCHAR(20)
idempotency_key VARCHAR(100) UNIQUE
wallet_transaction_id BIGINT
failure_reason TEXT NULL
created_at, approved_at, cancelled_at
INDEX idx_user_status (user_id, status)
```

#### pg_payments
```sql
id BIGINT PK
payment_id VARCHAR(40) FK
pg_provider VARCHAR(20)
pg_tx_id VARCHAR(100)
status VARCHAR(20)
pg_log_id VARCHAR(40)  -- MongoDB ObjectId 참조
created_at
```

#### idempotency_keys
```sql
id BIGINT PK
key_value VARCHAR(100) UNIQUE
request_hash VARCHAR(64)
response_body TEXT
status VARCHAR(20)
created_at
expires_at
```

#### outbox
```sql
id BIGINT PK
aggregate_id VARCHAR(100)
aggregate_type VARCHAR(50)
event_type VARCHAR(50)
topic VARCHAR(100)
partition_key VARCHAR(100)
payload JSON
status VARCHAR(20)
retry_count INT
created_at, published_at
INDEX idx_status_created (status, created_at)
```

#### consumed_events
```sql
event_id VARCHAR(40) PK
topic VARCHAR(100)
consumer_group VARCHAR(100)
consumed_at
```

---

### MongoDB

#### pg_payment_logs
```javascript
{
  _id, paymentId, pgPaymentId, pgProvider,
  requestType, request, response,
  durationMs, traceId, createdAt
}
// 인덱스: paymentId, createdAt, (pgProvider, createdAt)
```

#### audit_logs
```javascript
{
  _id, traceId, eventId,
  action, resource: { type, id },
  actor: { type, id, ip },
  before, after, metadata,
  occurredAt
}
// 인덱스: eventId(unique), (resource.type, resource.id, occurredAt), (actor.id, occurredAt)
```

---

## 8. 동시성 처리 상세

### 8.1 잔액 차감 동시성
- DB 비관적 락 (`SELECT ... FOR UPDATE`)
- Redis 분산락 (사용자 단위, 옵션)
- 낙관적 락 (`version`, 2차 방어선)

### 8.2 결제 멱등성
- `idempotency_keys` 테이블에 unique constraint
- 중복 요청은 기존 응답 반환
- PROCESSING 상태면 409

### 8.3 Outbox 발행 멱등성
- Consumer 측 `consumed_events` 테이블로 `eventId` 중복 처리 방지
- AuditConsumer는 MongoDB `eventId` unique 인덱스로 추가 보장

---

## 9. 모듈 간 책임 구분 (MongoDB 적재)

| 데이터 | 적재 주체 | 시점 | 트랜잭션 |
|--------|----------|------|---------|
| `pg_payment_logs` | `payment` 모듈 (PgClient 내부) | PG 호출 직후 (동기) | MySQL 트랜잭션과 분리 |
| `audit_logs` | `audit` 모듈 (Kafka Consumer) | 이벤트 수신 시 (비동기) | 완전 분리 |

**audit 모듈은 다른 모듈을 알지 않는다.** 오직 Kafka 이벤트만 구독한다.

---

## 10. Phase별 로드맵

### Phase 1: 코어 도메인
- [ ] 프로젝트 세팅 (build.gradle, docker-compose: MySQL + Mongo + Redis + Kafka)
- [ ] 공통 코드 (Money VO, DomainEvent, Outbox)
- [ ] User, Auth (JWT)
- [ ] Wallet Aggregate + 충전/차감
- [ ] Wallet 단위 테스트 + 동시성 테스트

### Phase 2: 결제 + PG 로그
- [ ] Payment Aggregate
- [ ] PG Client 추상화 + 토스페이먼츠 구현
- [ ] **PgPaymentLog MongoDB 적재**
- [ ] PaymentFacade (선차감 → PG → 확정)
- [ ] Idempotency 처리
- [ ] Outbox Publisher

### Phase 3: 이벤트 파이프라인 + Audit
- [ ] Kafka 프로듀서/컨슈머 설정
- [ ] SettlementConsumer (Item 적재)
- [ ] **AuditConsumer (MongoDB audit_logs 적재)**
- [ ] NotificationConsumer (스켈레톤)
- [ ] Consumer 멱등성 (consumed_events + Mongo unique index)

### Phase 4: 테스트 강화
- [ ] TestContainers (MySQL + MongoDB + Kafka + Redis)
- [ ] 결제 E2E 시나리오
- [ ] 장애 시나리오 (PG 실패, MongoDB 다운, 중복 요청)

### Phase 5: 운영 관점
- [ ] 로깅 (MDC traceId)
- [ ] 모니터링 (Micrometer)
- [ ] API 문서화 (Swagger)
- [ ] AuditLog 조회 어드민 API

### Phase 6+ (확장)
- Merchant Aggregate 도입
- 실제 카드/계좌 충전 (PG Charge)
- 트레이딩 서비스 연동

---

## 11. Architecture Decision Records

| # | 결정 | 이유 |
|---|------|------|
| 001 | Modular Monolith 채택 | 초기 복잡도 최소화 + 추후 MSA 분리 가능 |
| 002 | 충전은 가상 구현으로 시작 | PG 연동 범위를 결제에 집중 |
| 003 | Merchant Aggregate는 Phase 2로 | 초기 범위 최소화 |
| 004 | Transactional Outbox 채택 | DB+Kafka 원자성 보장 |
| 005 | 결제 시 선차감 방식 | 승인됐는데 잔액 없는 상황 방지 |
| 006 | PG 호출은 트랜잭션 외부 | 외부 호출로 DB 커넥션 장기 점유 방지 |
| 007 | **Polyglot Persistence (MySQL + MongoDB)** | **데이터 특성에 맞는 저장소 선택. 코어는 ACID, 로그성은 가변 스키마** |
| 008 | **PG 로그는 동기 적재, Audit 로그는 비동기 적재** | **PG 로그는 장애 분석에 즉시 필요. Audit은 결제 성능에 영향 주면 안 됨** |
| 009 | **AuditConsumer는 다른 모듈을 알지 않는다** | **Kafka 이벤트만 구독하여 결합도 최소화** |

---

## 12. 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2026-04-18 | 초기 설계 확정 (Wallet/Payment/Settlement Aggregate, Outbox, Kafka 토픽) |
| 2026-04-18 | MongoDB 도입 — PgPaymentLog, AuditLog. Polyglot Persistence 적용 (ADR 007~009 추가) |
