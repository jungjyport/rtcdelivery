# order-service — 주문 스펙

> **계약 방식**: Swagger (Code-first). REST 계약의 진실의 원천은 Controller/DTO의 springdoc 어노테이션이다.
> 이 문서는 어노테이션으로 표현되지 않는 **상태 모델, 스냅샷, 인가, Kafka 계약**을 고정한다 ([AGENTS.md §2.3](../../../AGENTS.md)).
>
> **Kafka 이벤트는 Swagger로 표현되지 않으므로 §7의 페이로드가 계약이다.** payment-service도 이 절을 따른다.

**관련 문서**


| 문서                                                                              | 다루는 것                                |
| ------------------------------------------------------------------------------- | ------------------------------------ |
| [payment-spec.md](../payment-service/payment-spec.md)                           | 결제 승인, Mock PG, 환불, `payment-events` |
| [order-pages-spec.md](../nuxt-app/order-pages-spec.md)                          | 주문서, 결제 화면, 내 주문, 점주·운영자 상태 버튼       |
| [catalog-spec.md §10](../food-catalog-service/catalog-spec.md#10-주문-스냅샷-내부-api) | 주문 시점 가격·소유자 스냅샷                     |
| [api-conventions.md](../../../api-conventions.md)                               | 응답 래퍼, 페이지네이션, `X-User-*`            |
| [error-handling.md §3](../../../error-handling.md#3-에러-코드-카탈로그)                 | 에러 코드                                |
| [AGENTS.md §4](../../../AGENTS.md)                                              | Outbox, Inbox, Saga                  |


---



## 1. 범위



### 1.1 구현하는 것

- `Order` / `OrderItem` 과 이행 상태·결제 상태
- 주문 생성 시 food-catalog에서 **가격·품절·최소 주문·점주 ID를 스냅샷**
- 내 주문 목록·상세, 점주·운영자 주문 목록
- 점주·운영자가 **버튼으로 누르는 HTTP 상태 전이**
- 고객·점주·운영자의 취소, 운영자의 배달 완료 후 환불 요청
- `order-events` 발행(Outbox)과 `payment-events` 소비(Inbox)



### 1.2 구현하지 않는 것


| 항목                                                          | 이유                                                   |
| ----------------------------------------------------------- | ---------------------------------------------------- |
| WebSocket, STOMP, SSE, 푸시 알림                                | 주문 상태는 DB가 원본이다. 고객은 상세를 다시 조회하고, 점주·운영자는 버튼을 눌러 바꾼다 |
| 라이더 계정, 실시간 위치                                              | 배달 단계는 점주·운영자가 `DELIVERING` / `DELIVERED`로 넘긴다       |
| 주문 상태 Redis 캐시                                              | 조회는 `rtc_order` 한 번으로 끝난다                            |
| `/api/v1/delivery/**`                                       | 배달은 별도 리소스가 아니다. Gateway에 남아 있는 이 경로는 구현 시 제거한다      |
| `spring-boot-starter-websocket`, `SecurityConfig`의 `/ws/**` | 스캐폴딩 잔재. 구현 시 제거한다                                   |
| 장바구니 서버 저장                                                  | 장바구니는 브라우저 Pinia다. 서버는 주문 생성 요청만 받는다                 |
| 주문 내역 다국어                                                   | 스냅샷은 원본(`ko`) 이름만 저장한다. order DB에 번역 테이블을 두지 않는다     |


---



## 2. 서비스 위치


| 항목             | 값                        |
| -------------- | ------------------------ |
| 폴더             | `backend/order-service/` |
| 베이스 패키지        | `com.rtcdelivery.order`  |
| 포트             | 8083                     |
| DB             | `rtc_order`              |
| Consumer Group | `order-service-group`    |


Gateway는 이미 `/api/v1/orders/**`를 이 서비스로 보낸다. JWT는 Gateway가 검증한다. 이 서비스는 `X-User-Id` / `X-User-Role`만 신뢰하며, `jwt.secret` 같은 HS256 설정은 제거한다.

`HeaderAuthenticationFilter`와 `Actor`는 food-catalog와 같은 방식으로 둔다. 주문 API는 필터에서 `authenticated()`로 잠그고, 비로그인은 401, 역할 불일치는 403이 되게 한다. 특정 주문의 소유권 불일치는 404다 (§6).

---



## 3. 상태 모델

이행과 결제를 한 enum에 넣지 않는다. 결제가 끝나도 가게가 접수하기 전에는 `PENDING`이고, 접수 버튼은 결제가 `PAID`일 때만 허용한다.

### 3.1 이행 상태 `fulfillmentStatus`

```
PENDING → ACCEPTED → PREPARING → READY → DELIVERING → DELIVERED
   │          │            │         │          │
   └──────────┴────────────┴─────────┴──────────┴→ CANCELLED
```


| 상태           | 의미              |
| ------------ | --------------- |
| `PENDING`    | 주문 생성됨. 가게 접수 전 |
| `ACCEPTED`   | 점주·운영자가 접수      |
| `PREPARING`  | 조리 중            |
| `READY`      | 조리 완료, 배달 대기    |
| `DELIVERING` | 배달 중            |
| `DELIVERED`  | 배달 완료. 종료       |
| `CANCELLED`  | 취소. 종료          |


한 번에 한 단계만 앞으로 간다. `PENDING`에서 `PREPARING`으로 건너뛰면 `INVALID_ORDER_STATUS_TRANSITION`(409).

전이 주체:


| 전이                                                              | 누가                                                            |
| --------------------------------------------------------------- | ------------------------------------------------------------- |
| `PENDING` → `ACCEPTED`                                          | 해당 가게 점주 또는 `ROLE_ADMIN`. `paymentStatus`**가** `PAID`**일 때만** |
| `ACCEPTED` → `PREPARING` → `READY` → `DELIVERING` → `DELIVERED` | 해당 가게 점주 또는 `ROLE_ADMIN`                                      |
| → `CANCELLED`                                                   | 고객은 `PENDING`만. 점주·운영자는 `DELIVERED` / `CANCELLED`가 아닌 모든 상태   |


고객은 이행 상태를 앞으로 밀 수 없다.

### 3.2 결제 상태 `paymentStatus`

order-service가 결제 금액을 바꾸지 않는다. payment-service 이벤트가 이 값을 바꾼다.


| 상태               | 의미      | 진입                            |
| ---------------- | ------- | ----------------------------- |
| `UNPAID`         | 결제 전    | 주문 생성                         |
| `PAYMENT_FAILED` | Mock 거절 | `PAYMENT_FAILED`              |
| `PAID`           | 결제 완료   | `PAYMENT_COMPLETED`           |
| `REFUND_PENDING` | 환불 진행   | 취소 또는 환불 요청 시점, 이미 `PAID`였을 때 |
| `REFUNDED`       | 환불 완료   | `REFUND_COMPLETED`            |


`PAYMENT_FAILED` 이후 재승인이 성공하면 `PAID`가 된다.

취소와 결제가 겹치면 §7.4를 따른다.

---



## 4. 카탈로그 스냅샷

주문 생성 시에만 food-catalog를 **동기**로 호출한다. 이후 가격·품절·가게 비활성화는 이미 만들어진 주문을 바꾸지 않는다. 메뉴가 물리 삭제돼도 주문 품목의 이름과 단가는 남는다 ([catalog-spec.md](../food-catalog-service/catalog-spec.md) 삭제 정책).

### 4.1 호출


| 항목    | 값                                                                      |
| ----- | ---------------------------------------------------------------------- |
| 메서드   | `GET /internal/restaurants/{restaurantId}/order-snapshot`              |
| 호출 경로 | Eureka `food-catalog-service`로 직접. API Gateway를 거치지 않는다                |
| 클라이언트 | `client/FoodCatalogClient` + Resilience4j `@Retry` 후 `@CircuitBreaker` |
| 실패    | fallback에서 `UPSTREAM_SERVICE_ERROR`(503)                               |


공개 `GET /api/v1/restaurants/{id}`에는 `ownerId`가 없고, 비활성 가게는 404라서 접수 권한과 "영업 종료"를 구분할 수 없다. 그래서 스냅샷 전용 내부 API를 둔다. 계약은 [catalog-spec.md §10](../food-catalog-service/catalog-spec.md#10-주문-스냅샷-내부-api).

응답의 `name`은 원본(`ko`)이다. 주문 서비스는 `Accept-Language`로 번역명을 저장하지 않는다.

### 4.2 금액

클라이언트가 보낸 가격·배달비는 무시한다.

- 품목 금액 = 스냅샷 `price` × `quantity`
- `foodAmount` = 품목 금액 합
- `totalAmount` = `foodAmount` + 스냅샷 `deliveryFee`
- `foodAmount` < `minOrderAmount` 이면 `MIN_ORDER_AMOUNT_NOT_MET`
- 통화는 `KRW`, 단위는 원(정수)

검증:


| 조건                | 코드                                      |
| ----------------- | --------------------------------------- |
| 품목 0건, 또는 30건 초과  | `ORDER_ITEM_EMPTY` / `VALIDATION_ERROR` |
| 같은 `foodId` 중복    | `DUPLICATE_ORDER_ITEM`                  |
| 수량 1~99 밖         | `VALIDATION_ERROR`                      |
| 가게 없음             | `RESTAURANT_NOT_FOUND`                  |
| `active == false` | `RESTAURANT_CLOSED`                     |
| 메뉴 없음 또는 다른 가게 메뉴 | `FOOD_NOT_FOUND`                        |
| `soldOut == true` | `FOOD_UNAVAILABLE`                      |


`restaurantOwnerId`는 스냅샷의 `ownerId`를 주문에 복사한다. 이후 상태 변경은 이 값을 `X-User-Id`와 비교한다. 카탈로그를 다시 묻지 않는다.

---



## 5. 데이터 모델

모든 엔티티는 `BaseTimeEntity`를 상속한다. `@Setter`는 쓰지 않고 상태 전이는 도메인 메서드로만 한다.

### 5.1 `orders`


| 컬럼                    | 타입           | 제약                                      | 설명                     |
| --------------------- | ------------ | --------------------------------------- | ---------------------- |
| `id`                  | BIGINT       | PK                                      |                        |
| `member_id`           | BIGINT       | NOT NULL, INDEX                         | 주문자. member-auth FK 없음 |
| `restaurant_id`       | BIGINT       | NOT NULL, INDEX                         |                        |
| `restaurant_owner_id` | BIGINT       | NOT NULL, INDEX                         | 접수 권한                  |
| `restaurant_name`     | VARCHAR(255) | NOT NULL                                | 주문 시점 원본명              |
| `fulfillment_status`  | VARCHAR(30)  | NOT NULL                                | §3.1                   |
| `payment_status`      | VARCHAR(30)  | NOT NULL                                | §3.2                   |
| `food_amount`         | INT          | NOT NULL                                |                        |
| `delivery_fee`        | INT          | NOT NULL                                |                        |
| `total_amount`        | INT          | NOT NULL                                |                        |
| `recipient_name`      | VARCHAR(50)  | NOT NULL                                |                        |
| `recipient_phone`     | VARCHAR(30)  | NOT NULL                                |                        |
| `address`             | VARCHAR(255) | NOT NULL                                |                        |
| `request_note`        | VARCHAR(200) | NULL                                    |                        |
| `idempotency_key`     | CHAR(36)     | NOT NULL                                |                        |
| —                     |              | UNIQUE (`member_id`, `idempotency_key`) | 같은 회원의 재전송은 같은 주문      |




### 5.2 `order_items`


| 컬럼            | 타입           | 제약                  | 설명                      |
| ------------- | ------------ | ------------------- | ----------------------- |
| `id`          | BIGINT       | PK                  |                         |
| `order_id`    | BIGINT       | NOT NULL, FK, INDEX |                         |
| `food_id`     | BIGINT       | NOT NULL            | 삭제된 메뉴를 가리킬 수 있다. FK 없음 |
| `food_name`   | VARCHAR(255) | NOT NULL            |                         |
| `unit_price`  | INT          | NOT NULL            |                         |
| `quantity`    | INT          | NOT NULL            |                         |
| `line_amount` | INT          | NOT NULL            |                         |




### 5.3 `outbox_event` / `inbox_event`

[translation-pipeline-spec.md §4.6](../translation-service/translation-pipeline-spec.md)과 같은 컬럼을 쓴다. Outbox 재시도는 base 1초, cap 300초, 30회 후 `FAILED`.

`inbox_event.consumer_group`은 `order-service-group`.

---



## 6. 엔드포인트

1. 응답은 `ApiResponse<T>`. 목록은 `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`). 기본 정렬은 `createdAt,desc`.


| #   | Method | Path                         | 접근                             | 설명                               |
| --- | ------ | ---------------------------- | ------------------------------ | -------------------------------- |
| 1   | POST   | `/api/v1/orders`             | 로그인                            | 주문 생성                            |
| 2   | GET    | `/api/v1/orders`             | 로그인                            | 내 주문. `member_id = X-User-Id`    |
| 3   | GET    | `/api/v1/orders/{id}`        | 주문자, 그 가게 점주, ADMIN            | 상세                               |
| 4   | GET    | `/api/v1/orders/managed`     | `OWNER`, `ADMIN`               | 운영 목록                            |
| 5   | PATCH  | `/api/v1/orders/{id}/status` | 그 가게 점주, ADMIN                 | 다음 이행 상태                         |
| 6   | POST   | `/api/v1/orders/{id}/cancel` | 고객은 `PENDING`만. 점주·ADMIN은 종료 전 | 취소                               |
| 7   | POST   | `/api/v1/orders/{id}/refund` | `ADMIN`                        | `DELIVERED` 이면서 `PAID`인 주문 환불 요청 |


`GET /orders/managed`가 `GET /orders/{id}`보다 먼저 매핑되게 한다.

### 6.1 생성 — `POST /api/v1/orders`

헤더 `Idempotency-Key: {UUID}` 필수. 없거나 UUID가 아니면 `VALIDATION_ERROR`.

같은 회원이 같은 키로 다시 보내면 **기존 주문을 200으로 돌려주고** 카탈로그를 다시 호출하지 않는다. 새 생성이면 201.

```json
{
  "restaurantId": 1,
  "items": [{ "foodId": 10, "quantity": 2 }],
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678",
  "address": "서울시 강남구 테헤란로 1",
  "requestNote": "문 앞에 두세요"
}
```

같은 트랜잭션에서 주문 저장 + `ORDER_CREATED` Outbox INSERT.

`restaurantOwnerId`는 응답에 넣지 않는다.

```json
{
  "id": 100,
  "restaurantId": 1,
  "restaurantName": "서울 김치찌개",
  "fulfillmentStatus": "PENDING",
  "paymentStatus": "UNPAID",
  "foodAmount": 18000,
  "deliveryFee": 3000,
  "totalAmount": 21000,
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678",
  "address": "서울시 강남구 테헤란로 1",
  "requestNote": "문 앞에 두세요",
  "items": [
    { "foodId": 10, "foodName": "김치찌개", "unitPrice": 9000, "quantity": 2, "lineAmount": 18000 }
  ],
  "createdAt": "2026-09-29T02:00:00Z"
}
```



### 6.2 운영 목록 — `GET /api/v1/orders/managed`

쿼리: `restaurantId`, `fulfillmentStatus`, `page`, `size`.


| 역할           | 범위                                                                            |
| ------------ | ----------------------------------------------------------------------------- |
| `ROLE_OWNER` | `restaurant_owner_id = X-User-Id`. `restaurantId`가 있으면 그 가게만, 내 가게가 아니면 빈 페이지 |
| `ROLE_ADMIN` | 전체. `restaurantId`가 있으면 그 가게만                                                 |
| 그 외          | 403 `FORBIDDEN`                                                               |


목록 API는 컬렉션이라 403을 쓴다. 단건 조회·변경은 아래 404 규칙을 쓴다.

### 6.3 상태 변경 — `PATCH /api/v1/orders/{id}/status`

```json
{ "status": "ACCEPTED" }
```

`status`는 바로 다음 이행 상태만 허용한다. `CANCELLED`는 이 API가 아니라 취소 API다.


| 조건                                 | 코드                                    |
| ---------------------------------- | ------------------------------------- |
| 주문 없음, 또는 점주/운영자가 아님, 또는 고객 본인만 해당 | 404 `ORDER_NOT_FOUND`                 |
| 결제 전 접수                            | 409 `ORDER_NOT_PAID`                  |
| 건너뛰기, 종료 상태                        | 409 `INVALID_ORDER_STATUS_TRANSITION` |


`ROLE_ADMIN`은 `restaurant_owner_id` 일치를 보지 않는다.

성공 시 변경된 `OrderResponse`.

### 6.4 취소 — `POST /api/v1/orders/{id}/cancel`

body 없음.


| 호출자               | 허용                             |
| ----------------- | ------------------------------ |
| 주문자 (`member_id`) | `fulfillmentStatus == PENDING` |
| 가게 점주, ADMIN      | `DELIVERED` / `CANCELLED`가 아님  |


이미 `CANCELLED`면 `ORDER_ALREADY_CANCELLED`. 고객이 접수 이후를 취소하면 `ORDER_NOT_CANCELLABLE`. 권한 없는 단건은 `ORDER_NOT_FOUND`.

처리:

1. `fulfillmentStatus = CANCELLED`
2. `paymentStatus`가 `PAID`면 `REFUND_PENDING`으로 바꾸고 `ORDER_CANCELLED`를 같은 트랜잭션에서 Outbox에 넣는다
3. `UNPAID` 또는 `PAYMENT_FAILED`여도 `ORDER_CANCELLED`를 넣는다. payment-service가 승인 대기 건을 닫는다



### 6.5 환불 요청 — `POST /api/v1/orders/{id}/refund`

`ROLE_ADMIN`만. 조건은 `fulfillmentStatus == DELIVERED` 이고 `paymentStatus == PAID`. 아니면 `REFUND_NOT_ALLOWED`(409). 주문이 없으면 `ORDER_NOT_FOUND`.

`paymentStatus = REFUND_PENDING` + Outbox `ORDER_REFUND_REQUESTED`. 이행 상태는 `DELIVERED`로 남긴다.

`REFUND_PENDING`인 주문을 운영자가 다시 호출하면 같은 이벤트를 한 번 더 넣어도 된다. payment-service는 `orderId`당 환불을 한 번만 수행한다 (§ payment-spec 멱등).

### 6.6 조회 인가

`GET /orders/{id}`는 주문자, `restaurant_owner_id`와 같은 점주, ADMIN만 본다. 그 외는 `ORDER_NOT_FOUND`.

---



## 7. Kafka와 Saga

Choreography. 주문은 결제를 동기 호출하지 않는다.

### 7.1 토픽


| 토픽                   | Producer                 | Consumer        |
| -------------------- | ------------------------ | --------------- |
| `order-events`       | order-service (Outbox)   | payment-service |
| `order-events.DLT`   | payment-service          | 수동 re-drive     |
| `payment-events`     | payment-service (Outbox) | order-service   |
| `payment-events.DLT` | order-service            | 수동 re-drive     |


메시지 키는 `order:{orderId}`. `AckMode.RECORD`. Consumer Group은 `order-service-group` / `payment-service-group`.

### 7.2 Envelope

```json
{
  "eventId": "3f2a9c10-5b1e-4f8a-9d02-7c6b5e4a1d33",
  "eventType": "ORDER_CREATED",
  "aggregateType": "ORDER",
  "aggregateId": "100",
  "occurredAt": "2026-09-29T02:00:00Z",
  "payload": {}
}
```



### 7.3 `order-events` 페이로드

`ORDER_CREATED`

```json
{
  "orderId": 100,
  "memberId": 5,
  "restaurantId": 1,
  "totalAmount": 21000,
  "currency": "KRW"
}
```

`ORDER_CANCELLED`

```json
{
  "orderId": 100,
  "memberId": 5,
  "reason": "CUSTOMER"
}
```

`reason`은 `CUSTOMER` / `OWNER` / `ADMIN`.

`ORDER_REFUND_REQUESTED`

```json
{
  "orderId": 100,
  "memberId": 5
}
```



### 7.4 `payment-events` 소비


| eventType           | 주문에 하는 일                                                                                                                                                                |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `PAYMENT_COMPLETED` | `paymentStatus = PAID`. 이미 `PAID` / `REFUND_PENDING` / `REFUNDED`면 무시. **이행이 이미** `CANCELLED`**이면** `PAID`로 두지 않고 `REFUND_PENDING`으로 둔 뒤 `ORDER_REFUND_REQUESTED`를 발행한다 |
| `PAYMENT_FAILED`    | `UNPAID`일 때만 `PAYMENT_FAILED`. 취소·환불 중이면 무시                                                                                                                             |
| `REFUND_COMPLETED`  | `paymentStatus = REFUNDED`                                                                                                                                              |
| `REFUND_FAILED`     | `REFUND_PENDING`을 `PAID`로 되돌린다. 이행이 `CANCELLED`여도 결제 상태만 되돌린다. 운영자가 환불 API로 다시 요청한다                                                                                     |


Inbox `(eventId, consumerGroup)`이 같으면 두 번째 소비는 ack만 하고 끝낸다.

금액이 주문의 `totalAmount`와 다르면 상태를 바꾸지 않고 로그 후 ack한다. 결제 금액의 기준은 주문 생성 이벤트이고, 고객 입력 금액이 아니기 때문이다.

---



## 8. 에러 코드

`ErrorCode`에 추가하고 [error-handling.md §3.3](../../../error-handling.md#33-도메인별-코드)과 프론트 `error.*`에 함께 등록한다.


| 코드                                | HTTP | 조건              |
| --------------------------------- | ---- | --------------- |
| `ORDER_NOT_FOUND`                 | 404  | 없음, 또는 단건 권한 없음 |
| `ORDER_ITEM_EMPTY`                | 400  | 품목 없음           |
| `DUPLICATE_ORDER_ITEM`            | 400  | 같은 메뉴 두 줄       |
| `RESTAURANT_NOT_FOUND`            | 404  | 스냅샷 404         |
| `RESTAURANT_CLOSED`               | 409  | 비활성 가게          |
| `FOOD_NOT_FOUND`                  | 404  | 메뉴 없음           |
| `FOOD_UNAVAILABLE`                | 409  | 품절              |
| `MIN_ORDER_AMOUNT_NOT_MET`        | 400  | 음식 금액이 최소 주문 미만 |
| `ORDER_NOT_PAID`                  | 409  | 결제 전 접수         |
| `ORDER_ALREADY_CANCELLED`         | 409  | 재취소             |
| `ORDER_NOT_CANCELLABLE`           | 409  | 고객이 접수 후 취소     |
| `INVALID_ORDER_STATUS_TRANSITION` | 409  | 허용되지 않은 전이      |
| `REFUND_NOT_ALLOWED`              | 409  | 환불 조건 불일치       |
| `UPSTREAM_SERVICE_ERROR`          | 503  | 카탈로그 호출 실패      |


---



## 9. 테스트

라인 커버리지 70% 이상. 메서드명 `{메서드}_{시나리오}_{기대}`.


| 대상  | 케이스                                                                                 |
| --- | ----------------------------------------------------------------------------------- |
| 도메인 | 한 단계 전이, 건너뛰기 거부, 미결제 접수 거부, 고객 취소는 `PENDING`만                                      |
| 생성  | 스냅샷 가격 사용, 품절, 최소 금액, 멱등 키 재전송은 같은 주문                                               |
| 인가  | 타인 주문 404, 점주 본인 가게만, ADMIN 우회, 운영 목록의 일반 회원 403                                    |
| 이벤트 | 생성과 `ORDER_CREATED`가 같은 트랜잭션, `PAYMENT_COMPLETED`로 `PAID`, 취소 후 늦게 도착한 결제 완료는 환불 요청 |


---



## 10. 구현 순서

1. food-catalog 내부 스냅샷 API ([catalog-spec.md §10](../food-catalog-service/catalog-spec.md#10-주문-스냅샷-내부-api))
2. 엔티티, 생성, 내 주문 조회
3. `HeaderAuthenticationFilter` + 주문 API `authenticated()`
4. Outbox + `ORDER_CREATED`
5. payment-service 소비·승인 ([payment-spec.md](../payment-service/payment-spec.md))
6. `payment-events` 소비
7. 운영 목록, `PATCH` 상태, 취소, 환불 요청
8. 프론트 ([order-pages-spec.md](../nuxt-app/order-pages-spec.md))

