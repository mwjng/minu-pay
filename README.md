# minu-pay


추후 주식 트레이딩 등 다른 도메인에서 호출할 수 있는 결제 인프라를 모듈러 모놀리스로 구축한다.

---

## 기술 스택

**Language / Framework**
- Java 21, Spring Boot 3.5

**Storage**
- MySQL 8 — Wallet, Payment, Settlement, Outbox, Idempotency (Source of Truth)
- MongoDB 7 — PG Logs, Audit Logs (보조 저장소, append-only)
- Redis 7 — 분산락, 캐시

**Messaging**
- Apache Kafka 3.9 (KRaft mode) — 모듈 간 도메인 이벤트 전달

**Persistence / Query**
- Spring Data JPA, QueryDSL 5, Spring Data MongoDB

**Auth / Docs / Observability**
- Spring Security + JWT (jjwt 0.12)
- Swagger
- Actuator + Micrometer Prometheus

**Test**
- JUnit 5, Mockito, AssertJ, TestContainers (MySQL / MongoDB / Kafka / Redis)

---

## 아키텍처

### 모듈러 모놀리스

```
com.minupay
├── common/        공통 코드 (event, outbox, exception)
├── auth/          인증/인가 (JWT)
├── user/          사용자
├── wallet/        지갑 — 충전/차감/환불
├── payment/       결제 — PG 연동, 승인/취소
├── settlement/    정산 — 일별 집계, 수수료 계산
├── notification/  알림
└── audit/         감사 로그 (Kafka 구독 전용)
```

각 모듈은 `domain` / `application` / `infrastructure` / `presentation` 레이어로 나뉘며, 모듈 간 직접 의존은 금지된다. 협력은 아래 세 가지 방식으로만 허용된다.

1. **Facade 패턴** — 모듈 간 조율이 필요할 때 상위 Facade 가 중재
2. **도메인 이벤트** — Outbox → Kafka → 다른 모듈 Consumer
3. **공개 인터페이스** — 모듈이 명시적으로 공개한 API 만 호출

`audit` 모듈은 어떤 모듈도 알지 않고, 오직 Kafka 이벤트만 구독한다.



- **잔액/결제 상태는 MySQL 에만 저장**한다. MongoDB 에 핵심 비즈니스 데이터를 저장하지 않는다.
- **PG 로그는 동기 적재** (try-finally 로 성공/실패 무관하게 기록, 단 결제 트랜잭션과는 분리)
- **Audit 로그는 비동기 적재** (Kafka Consumer 를 통해서만)
- MongoDB 적재 실패는 결제 트랜잭션을 깨뜨리지 않는다.

### 이벤트 파이프라인

```
Aggregate 상태 변경
   └─ 같은 트랜잭션에서 Outbox 테이블 insert
         └─ OutboxPublisher (스케줄러) → Kafka
               └─ SettlementConsumer / AuditConsumer / ...
```

- **Transactional Outbox Pattern** 으로 DB 커밋과 Kafka 발행의 원자성 보장
- 모든 이벤트에 `eventId`, `traceId` 포함
- Consumer 는 `consumed_events` 테이블 또는 Mongo unique index 로 **멱등성 보장**
- 이벤트 타입은 `EventType` enum 으로 관리, 외부 wire 포맷(`"PaymentApproved"`)은 `wireName()` 으로 분리

---

## 핵심 설계 원칙

- Service에 로직 집중 금지
- Aggregate 간 참조는 ID 로만 하고, 하나의 트랜잭션은 하나의 Aggregate 만 수정
- Idempotency Key 로 결제 중복 실행 방지 (클라이언트 지정)
- SELECT FOR UPDATE + Redis 분산락으로 잔액 차감 동시성 제어

---

## 문서

- [`CLAUDE.md`](./CLAUDE.md) — 작업 규칙 / 레이어 / 테스트 전략
- [`DESIGN.md`](./DESIGN.md) — Aggregate, 플로우, 이벤트, 스키마 상세 설계
- Swagger UI: http://localhost:8081/swagger-ui.html