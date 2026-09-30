# payment-service — 결제 스펙

> **계약 방식**: Swagger (Code-first). REST 계약의 진실의 원천은 Controller/DTO 어노테이션이다.
> 이벤트 계약은 [order-spec.md §7](../order-service/order-spec.md#7-kafka와-saga)과 이 문서 §5가 함께 고정한다.

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [order-spec.md](../order-service/order-spec.md) | 주문 상태, `order-events`, 취소·환불 요청 |
| [order-pages-spec.md](../nuxt-app/order-pages-spec.md) | 결제 화면 |
| [error-handling.md §3](../../../error-handling.md#3-에러-코드-카탈로그) | 에러 코드 |
| [AGENTS.md §4](../../../AGENTS.md) | Outbox, Inbox |

---

## 1. 범위

### 1.1 구현하는 것

- `ORDER_CREATED`를 소비해 주문당 결제 1건 생성
- 고객의 승인 요청을 **Mock PG**로 처리
- 성공·실패·환불 결과를 `payment-events`로 발행
- `ORDER_CANCELLED` / `ORDER_REFUND_REQUESTED`에 따른 승인 차단과 환불

### 1.2 구현하지 않는 것

| 항목 | 이유 |
|---|---|
| 실 PG, 빌링키, 웹훅 | 개발 단계는 Mock PG만 쓴다 |
| 카드 번호 저장 | 승인 순간에만 읽고, 저장은 끝 4자리뿐이다 |
| 클라이언트가 금액을 정하는 승인 | 금액은 `ORDER_CREATED`의 `totalAmount`만 쓴다 |
| 결제 서비스에서 주문 이행 상태를 바꾸는 일 | 이행 전이는 order-service의 HTTP API다 |
| 부분 환불, 복수 결제 수단 | 주문 1건 = 결제 1건 = 전액 환불 |

---

## 2. 서비스 위치

| 항목 | 값 |
|---|---|
| 폴더 | `backend/payment-service/` |
| 베이스 패키지 | `com.rtcdelivery.payment` |
| 포트 | 8084 |
| DB | `rtc_payment` |
| Consumer Group | `payment-service-group` |

Gateway 경로 `/api/v1/payments/**`는 이미 이 서비스로 간다. JWT는 Gateway가 검증하고, 이 서비스는 `HeaderAuthenticationFilter`로 `X-User-*`를 읽는다. 결제 API는 `authenticated()`.

order-service를 동기 호출하지 않는다. 주문 금액과 회원 번호는 이벤트로만 받는다.

---

## 3. 상태

```
AWAITING → COMPLETED → REFUNDING → REFUNDED
    │          │
    └→ FAILED  └→ (환불 실패 시 COMPLETED로 복귀)
    │
    └→ CANCELLED   (돈을 빼기 전에 주문이 취소됨)
```

| 상태 | 의미 |
|---|---|
| `AWAITING` | `ORDER_CREATED`로 만들어짐. 고객 승인 대기 |
| `COMPLETED` | Mock 승인 성공 |
| `FAILED` | Mock 거절. 같은 결제로 재승인 가능 |
| `CANCELLED` | 승인 전에 주문이 취소됨. 재승인 불가 |
| `REFUNDING` | 환불 호출 중 |
| `REFUNDED` | 전액 환불 완료 |

---

## 4. 데이터 모델

### 4.1 `payments`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK | |
| `order_id` | BIGINT | NOT NULL, UNIQUE | 주문당 하나 |
| `member_id` | BIGINT | NOT NULL, INDEX | `ORDER_CREATED`의 회원 |
| `amount` | INT | NOT NULL | 원 |
| `currency` | CHAR(3) | NOT NULL | `KRW` |
| `status` | VARCHAR(30) | NOT NULL | §3 |
| `method` | VARCHAR(20) | NULL | 승인 시점. 현재 `CARD`만 |
| `card_last4` | CHAR(4) | NULL | |
| `pg_approval_code` | VARCHAR(40) | NULL | Mock이 만든 승인 번호 |

`outbox_event` / `inbox_event`는 [order-spec.md §5.3](../order-service/order-spec.md#53-outbox_event--inbox_event)과 같다. Inbox consumer group은 `payment-service-group`.

---

## 5. 이벤트

토픽·Envelope·키(`order:{orderId}`)는 [order-spec.md §7](../order-service/order-spec.md#7-kafka와-saga)를 따른다.

### 5.1 소비

**`ORDER_CREATED`**

`order_id`가 없으면 `AWAITING` 결제를 만든다. 있으면 ack만 한다. 금액·회원은 페이로드를 그대로 쓴다.

**`ORDER_CANCELLED`**

| 현재 상태 | 동작 |
|---|---|
| `AWAITING`, `FAILED` | `CANCELLED`. 이벤트 발행 없음 |
| `COMPLETED` | §5.3 환불 |
| `REFUNDING`, `REFUNDED`, `CANCELLED` | 무시 |

**`ORDER_REFUND_REQUESTED`**

`COMPLETED`면 §5.3 환불. `REFUNDING` / `REFUNDED`면 무시. 그 외는 로그 후 ack (주문이 아직 `PAID`가 아닌데 환불 요청이 온 경우).

### 5.2 발행 페이로드

공통 필드: `paymentId`, `orderId`, `memberId`, `amount`.

| eventType | 추가 필드 | 언제 |
|---|---|---|
| `PAYMENT_COMPLETED` | 없음 | Mock 승인 성공과 같은 트랜잭션 |
| `PAYMENT_FAILED` | `reason: MOCK_DECLINED` | Mock 거절과 같은 트랜잭션 |
| `REFUND_COMPLETED` | 없음 | Mock 환불 성공 |
| `REFUND_FAILED` | `reason: MOCK_REFUND_FAILED` | 환불 재시도 소진 |

### 5.3 환불

1. `COMPLETED`를 `REFUNDING`으로 바꾼다
2. Mock 환불을 호출한다
3. 성공이면 `REFUNDED` + `REFUND_COMPLETED`
4. 실패면 최대 3회까지 간격을 두고 다시 호출한다. 모두 실패하면 `COMPLETED`로 되돌리고 `REFUND_FAILED`를 발행한다

같은 `orderId`에 환불이 이미 `REFUNDED`면 두 번째 요청은 이벤트를 다시 보내지 않는다.

---

## 6. Mock PG

클래스 `client/MockPaymentGateway`. 외부 HTTP 호출이 아니다. Circuit Breaker를 걸지 않는다.

### 6.1 승인

입력은 카드번호(숫자 16자리), 유효기간 `MM/YY`, CVC 3자리다. **어느 것도 컬럼에 남기지 않는다.** `card_last4`만 저장한다.

| 카드번호 | 결과 |
|---|---|
| 16자리이고 끝이 `0000`이 아님 | 승인. `pg_approval_code`는 `MOCK-{uuid}` |
| 끝이 `0000` | 거절 |

유효기간이 달력상 지났는지는 보지 않는다. 형식만 검사한다.

### 6.2 환불

기본은 성공. 테스트에서만 실패를 주입할 수 있게 `MockPaymentGateway`를 인터페이스로 두고, 운영 빈은 항상 성공한다.

---

## 7. 엔드포인트

| # | Method | Path | 접근 | 설명 |
|---|---|---|---|---|
| 1 | GET | `/api/v1/payments?orderId={orderId}` | 그 결제의 `member_id` | 주문에 연결된 결제 1건 |
| 2 | POST | `/api/v1/payments/{id}/approve` | 그 결제의 회원 | Mock 승인 |

결제 생성 API는 없다. 행은 `ORDER_CREATED`만 만든다.

타인 결제는 존재 여부를 숨기기 위해 `PAYMENT_NOT_FOUND`(404).

### 7.1 조회

이벤트가 아직 없으면 404 `PAYMENT_NOT_FOUND`. 프론트 결제 화면이 짧게 다시 조회한다 ([order-pages-spec.md §5](../nuxt-app/order-pages-spec.md#5-결제-화면)).

```json
{
  "id": 9,
  "orderId": 100,
  "amount": 21000,
  "currency": "KRW",
  "status": "AWAITING",
  "method": null,
  "cardLast4": null
}
```

### 7.2 승인 — `POST /api/v1/payments/{id}/approve`

```json
{
  "method": "CARD",
  "cardNumber": "4242424242424242",
  "expiry": "12/30",
  "cvc": "123"
}
```

`method`는 `CARD`만. 금액 필드는 받지 않는다. 클라이언트가 금액을 실으면 `VALIDATION_ERROR`로 막을 필요까지는 없고, **무시**한다.

| 상태 | 결과 |
|---|---|
| `AWAITING`, `FAILED` | Mock 호출 |
| `COMPLETED` | 409 `PAYMENT_ALREADY_COMPLETED` |
| `CANCELLED`, `REFUNDING`, `REFUNDED` | 409 `PAYMENT_NOT_APPROVABLE` |

Mock 거절은 결제 행을 `FAILED`로 저장하고 422 `PAYMENT_FAILED`를 반환한다. 응답과 `PAYMENT_FAILED` 이벤트는 같은 트랜잭션의 Outbox로 맞춘다.

성공은 200과 `PaymentResponse` (`status: COMPLETED`, `cardLast4`).

---

## 8. 에러 코드

| 코드 | HTTP | 조건 |
|---|---|---|
| `PAYMENT_NOT_FOUND` | 404 | 없음, 또는 회원 불일치 |
| `PAYMENT_ALREADY_COMPLETED` | 409 | 재승인 |
| `PAYMENT_NOT_APPROVABLE` | 409 | 취소·환불된 결제 |
| `PAYMENT_FAILED` | 422 | Mock 거절 |
| `PAYMENT_AMOUNT_MISMATCH` | 400 | 소비 이벤트 금액이 0 이하. 정상 `ORDER_CREATED`에서는 발생하지 않는다 |
| `REFUND_NOT_ALLOWED` | 409 | enum에만 유지. 환불은 이벤트로만 받고, 대상이 `COMPLETED`가 아니면 예외 없이 ack 한다 (§5.1) |
| `PG_UNAVAILABLE` | 503 | 예비용. 현재 Mock은 이 코드를 던지지 않는다 |

---

## 9. 사용자 관점 흐름

```
POST /orders  →  ORDER_CREATED
                    → Payment(AWAITING)
고객 POST /payments/{id}/approve
    성공 → PAYMENT_COMPLETED → 주문 paymentStatus=PAID
    거절 → PAYMENT_FAILED    → 주문 paymentStatus=PAYMENT_FAILED
점주 접수 버튼은 PAID 이후에만 동작 (order-service)
취소(결제 후) → ORDER_CANCELLED → 환불 → REFUND_COMPLETED
배달 완료 후 운영자 환불 → ORDER_REFUND_REQUESTED → 같은 환불
```

결제 승인 HTTP가 200이어도, 주문 행의 `paymentStatus`는 이벤트 소비 뒤에 `PAID`가 된다. 프론트는 주문 상세를 몇 번 다시 읽는다.

---

## 10. 테스트

| 대상 | 케이스 |
|---|---|
| 소비 | 같은 `ORDER_CREATED` 두 번에도 결제 1건, 취소가 승인보다 먼저면 `CANCELLED` |
| 승인 | `0000` 거절과 이벤트, 그 외 승인, 완료 후 재승인 409, 카드번호 미저장 |
| 환불 | 전액 1회, 중복 환불 요청은 이벤트 재발행 없음 |
| 인가 | 다른 회원의 `orderId` 조회는 404 |

라인 커버리지 70% 이상.
