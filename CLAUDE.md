# CLAUDE.md

이 파일은 Claude Code가 이 레포지토리에서 작업할 때 참고하는 프로젝트 가이드입니다.
**모든 작업 전에 이 문서를 먼저 읽고, 원칙을 따라야 합니다.**

---

## 1. 프로젝트 개요

- **이름**: minu-pay
- **목적**: 프로덕션급 페이(결제) 서비스. 추후 주식 트레이딩 등 다른 서비스에서 호출할 수 있는 결제 인프라.
- **아키텍처**: Modular Monolith (모듈 경계를 명확히 유지하여 추후 MSA 분리 가능)

---

## 2. 기술 스택

| 분류 | 스택 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Primary DB | MySQL 8 (Wallet, Payment, Settlement, Outbox, Idempotency) |
| Secondary DB | MongoDB 7 (PG Logs, Audit Logs) |
| Cache/Lock | Redis 7 |
| Messaging | Apache Kafka |
| ORM | Spring Data JPA, QueryDSL, Spring Data MongoDB |
| Auth | Spring Security + JWT |
| PG | 토스페이먼츠 (Sandbox) |
| Test | JUnit 5, Mockito, TestContainers (MySQL/Mongo/Kafka/Redis), AssertJ |
| Build | Gradle (Kotlin DSL) |

---

## 3. 패키지 구조 규칙

```
com.minu.pay
├── common/          # 전역 공통 코드 (도메인에 속하지 않는 것만)
│   ├── exception/
│   ├── event/       # DomainEvent 기반 인터페이스
│   └── outbox/      # Outbox 공통 구현
├── auth/            # 인증/인가
├── user/            # 사용자
├── wallet/          # 지갑 모듈 (MySQL)
│   ├── domain/         # 순수 도메인 (Spring/JPA 의존성 없음)
│   ├── application/    # UseCase, Service
│   ├── infrastructure/ # JPA, 외부 API 구현체
│   └── presentation/   # Controller, DTO
├── payment/         # 결제 모듈 (MySQL + MongoDB PG Logs)
├── settlement/
├── notification/
└── audit/           # 감사 로그 모듈 (MongoDB)
    ├── domain/
    ├── application/    # AuditConsumer
    └── infrastructure/ # MongoRepository
```

### 모듈 규칙
- **모듈 간 직접 의존 금지**. 다른 모듈의 `domain`, `application` 내부를 직접 import하지 않는다.
- 모듈 간 협력은 다음 방식으로만 허용:
  1. **Facade 패턴**: 모듈 간 조율이 필요한 경우 상위 Facade가 중재
  2. **도메인 이벤트**: Outbox → Kafka → 다른 모듈 Consumer
  3. **공개 API (public interface)**: 모듈이 명시적으로 공개한 인터페이스만 호출
- **audit 모듈은 다른 어떤 모듈도 알지 않는다.** 오직 Kafka 이벤트만 구독한다.

### 레이어 규칙
- `domain`은 **Spring, JPA, MongoDB, 외부 라이브러리에 의존하지 않는다** (순수 Java)
- `application`은 `domain`과 `infrastructure` interface에만 의존
- `infrastructure`는 `domain`이 정의한 interface의 구현체를 제공
- `presentation`은 `application`만 호출하고, `domain`을 직접 반환하지 않는다 (DTO 변환 필수)

---

## 4. 도메인 설계 원칙

### Aggregate
- Aggregate Root는 **불변식을 스스로 검증**한다. Service가 아닌 도메인 객체가 로직을 가진다 (Rich Domain Model).
- Aggregate 간 참조는 **ID로만** 한다. 객체 참조 금지.
- 하나의 트랜잭션은 **하나의 Aggregate만** 수정하는 것을 원칙으로 한다 (예외: Wallet+Payment 조율은 Facade + Outbox 패턴).

### 예시 — 잘못된 / 올바른 사용
```java
// ❌ 잘못됨: Service가 로직을 가짐
walletService.deduct(wallet, amount);
if (wallet.getBalance() < 0) throw new Exception();

// ✅ 올바름: 도메인이 스스로 검증
wallet.deduct(amount); // 내부에서 불변식 검증
```

### 도메인 이벤트
- Aggregate는 상태 변경 시 도메인 이벤트를 생성한다.
- 이벤트는 **같은 트랜잭션에서 Outbox 테이블에 저장**한다 (Transactional Outbox Pattern).
- 별도 Publisher가 Outbox → Kafka로 relay한다.
- 모든 이벤트는 `eventId`, `traceId`를 포함한다 (Audit/추적용).

---

## 5. 데이터 저장소 사용 규칙 (중요)

### 저장소별 역할
| 저장소 | 용도 | 절대 원칙 |
|--------|------|----------|
| **MySQL** | Wallet, Payment, Settlement, Outbox, Idempotency, ConsumedEvents | **Source of Truth**. 트랜잭션 필수. |
| **MongoDB** | PG Logs, Audit Logs | **보조 저장소**. Append-only. |
| **Redis** | 분산락, 캐시 | TTL 필수. |

### 절대 지킬 것
- **잔액/결제 상태는 MySQL에만 저장한다**. MongoDB에 핵심 비즈니스 데이터 저장 금지.
- **MongoDB 적재 실패가 결제 트랜잭션을 깨뜨려서는 안 된다.** 별도 트랜잭션으로 처리하고 실패 시 로그만 남기고 swallow.
- **PG 로그는 동기 적재** (try-finally로 성공/실패 무관하게 기록).
- **Audit 로그는 비동기 적재** (Kafka Consumer 통해서만).

### 잘못된 / 올바른 사용
```java
// ❌ 잘못됨: 결제 트랜잭션 안에서 Mongo 호출
@Transactional
public void approvePayment(...) {
    payment.approve();
    paymentRepository.save(payment);
    mongoTemplate.save(pgLog);  // Mongo 실패 시 결제도 롤백됨!
}

// ✅ 올바름: Mongo 적재는 분리
@Transactional
public void approvePayment(...) {
    payment.approve();
    paymentRepository.save(payment);
}
// 별도 호출, 실패해도 swallow
private void savePgLog(...) {
    try {
        pgLogRepository.save(pgLog);
    } catch (Exception e) {
        log.error("PG log save failed", e);
    }
}
```

---

## 6. 동시성/정합성 원칙

### 잔액 차감 (Wallet)
- `SELECT FOR UPDATE` (비관적 락) 사용
- Redis 분산락을 추가 레이어로 고려 (사용자 단위 락)

### Idempotency
- 모든 결제 요청은 `idempotencyKey` 필수
- `idempotency_keys` 테이블로 중복 실행 방지
- Key는 요청 바디 기준이 아닌 **클라이언트가 명시적으로 지정**

### Kafka Consumer
- 모든 Consumer는 **멱등성을 보장**해야 한다
- `consumed_events` 테이블 (MySQL) 또는 MongoDB unique index로 중복 수신 방지
- AuditConsumer는 MongoDB `eventId` unique index 사용

---

## 7. 테스트 규칙

### 레이어별 전략
| 레이어 | 전략 |
|--------|------|
| `domain` | 순수 단위 테스트 (Spring 없이, Mockito 최소화) |
| `application` | Mockito로 Repository/외부 의존성 mock |
| `infrastructure` (JPA) | `@DataJpaTest` 또는 TestContainers MySQL |
| `infrastructure` (Mongo) | `@DataMongoTest` 또는 TestContainers MongoDB |
| 통합 | `@SpringBootTest` + TestContainers (MySQL, MongoDB, Kafka, Redis) |
| 동시성 | `CountDownLatch` + `ExecutorService` 기반 병렬 실행 |

### 필수 테스트 케이스
- 모든 Aggregate 도메인 로직에는 **단위 테스트 필수**
- 잔액 차감/충전에는 **동시성 테스트 필수** (동시에 N번 실행 시 최종 잔액 검증)
- Outbox 발행에는 **롤백 시 이벤트 미발행 테스트 필수**
- AuditConsumer에는 **중복 이벤트 수신 시 단일 적재 테스트 필수**
- PG 로그에는 **Mongo 다운 시 결제 정상 처리 테스트 필수**

### 네이밍
- 한글 테스트 메서드명 허용: `@DisplayName` 또는 메서드명에 직접 사용
- 형식: `[상황]_[동작]_[기대결과]` (예: `잔액_부족시_차감_예외발생`)

---

## 8. 코드 컨벤션

### Java
- Record를 DTO/Value Object에 적극 활용
- `Optional`은 반환값에만 사용, 필드/파라미터에는 사용 금지
- Lombok 사용: `@Getter`, `@RequiredArgsConstructor`만 권장. `@Data`, `@Setter` 금지
- 엔티티는 `@Entity` + 별도 `*Entity.java`로 도메인과 분리 (JPA 세부사항이 도메인에 새지 않게)
- MongoDB 도큐먼트도 `@Document` + 별도 `*Document.java`로 도메인과 분리

### 예외
- 도메인 예외는 `DomainException`을 상속
- HTTP 응답으로 변환은 `@RestControllerAdvice`에서 일괄 처리
- 에러 코드는 `ErrorCode` enum으로 관리

### 커밋 메시지
- `feat: 지갑 충전 기능 추가`
- `fix: 동시 차감 시 음수 잔액 발생 이슈 수정`
- `refactor: PaymentService를 Facade로 분리`
- `test: Wallet 동시성 테스트 추가`

---

## 9. 작업 시 지켜야 할 절차

Claude Code가 이 레포에서 코드를 작성할 때 반드시 따라야 할 순서:

1. **작업 전**: `docs/DESIGN.md`에서 관련 Aggregate와 플로우 확인
2. **구현 시**: 위 `4. 도메인 설계 원칙`, `5. 데이터 저장소 사용 규칙`, `6. 동시성/정합성 원칙` 준수
3. **완료 후**: 해당 로직에 대한 테스트 작성 (테스트 없는 커밋 금지)
4. **문서 갱신**: 새 이벤트/API/저장소가 생기면 `docs/DESIGN.md`에 반영

---

## 10. 하지 말 것 (Anti-patterns)

- ❌ Service에 도메인 로직 집중시키기 (Anemic Domain Model)
- ❌ 여러 Aggregate를 한 트랜잭션에서 수정
- ❌ Entity/Document를 그대로 Controller에서 반환
- ❌ 테스트 없이 도메인 로직 작성
- ❌ 잔액 관련 로직에 락 없이 구현
- ❌ `@Transactional(readOnly = true)` 누락
- ❌ Kafka 발행을 DB 커밋과 같은 코드 블록에서 직접 호출 (Outbox 우회 금지)
- ❌ **MongoDB 호출을 MySQL 트랜잭션 안에서 수행** (저장소 분리 원칙 위반)
- ❌ **잔액/결제 상태를 MongoDB에 저장** (Source of Truth 위반)
- ❌ **audit 모듈에서 다른 모듈 import** (Kafka 이벤트만 구독해야 함)

---

## 11. 참고 문서

- `docs/DESIGN.md` — Aggregate, 플로우, 이벤트, 저장소 전략 상세 설계
- `docs/API.md` — (추후 작성) REST API 명세
- `docs/ADR/` — (추후 작성) Architecture Decision Records
