# nuxt-app — 주문 · 결제 화면 스펙

> **상태**: 스펙만 작성됨. 구현 전.
> **대상**: `frontend/nuxt-app`
> **계약 방식**: 문서 기반. API는 [order-spec.md](../order-service/order-spec.md), [payment-spec.md](../payment-service/payment-spec.md)를 따른다.

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [order-spec.md](../order-service/order-spec.md) | 주문 생성, 조회, 상태 버튼 API |
| [payment-spec.md](../payment-service/payment-spec.md) | 결제 조회·승인 |
| [api-client-spec.md](./api-client-spec.md) | `$api`, 401 갱신, `ApiError` |
| [owner-store-spec.md](./owner-store-spec.md) | `owner` 미들웨어, 점주 헤더 |
| [layout-spec.md](./layout-spec.md) | 헤더 네비. 주문내역 링크가 `#`인 상태를 이 스펙에서 교체한다 |

---

## 1. 범위

### 1.1 구현하는 것

- 장바구니 Pinia (주문서의 입력). Drawer UI는 §2의 최소 동작만 포함한다
- 주문서 `/checkout`
- 결제 `/orders/:id/pay`
- 내 주문 목록 `/orders`, 상세 `/orders/:id`
- 점주·운영자 주문 운영 `/owner/orders` — **상태 버튼은 HTTP `PATCH`**
- 상세·운영 화면의 새로고침 버튼

주문 상태를 구독하지 않는다. WebSocket, SSE, STOMP 클라이언트는 추가하지 않는다.

### 1.2 구현하지 않는 것

| 항목 | 이유 |
|---|---|
| 실시간 푸시, 폴링 루프 | 고객은 새로고침으로 조회한다. 결제 직후 몇 번 다시 읽는 것만 예외 (§5) |
| 라이더 지도 | 백엔드에 위치 채널이 없다 |
| 서버 장바구니, 주소록 | 장바구니와 배송지는 이번 주문의 입력이다 |
| 실 카드 결제창 | Mock. 테스트 카드 안내 문구만 보여 준다 |

---

## 2. 장바구니

파일 `app/stores/cart.ts`. 글로벌 상태라 Pinia를 쓴다.

한 장바구니는 **가게 하나**만 담는다. 다른 `restaurantId`를 담으면 기존 품목을 비울지 확인하고, 확인 시에만 교체한다.

| 필드 | 설명 |
|---|---|
| `restaurantId`, `restaurantName` | 현재 가게 |
| `items[]` | `foodId`, `name`, `unitPrice`, `quantity` |

`unitPrice`는 화면 표시용이다. 결제 금액의 기준은 주문 생성 응답의 `totalAmount`다.

동작:

- 음식점 상세의 메뉴에서 담기. 품절 메뉴는 담기 버튼을 막는다
- 수량 변경, 한 줄 삭제, 비우기
- 헤더에 수량 합계 배지. 클릭하면 Drawer
- Drawer의 "주문하기"는 로그인 상태면 `/checkout`, 아니면 `/auth/login?redirect=/checkout`
- 비어 있으면 `/checkout` 진입 시 `/restaurants`로 보낸다

메뉴 이름·가격은 담는 시점의 카탈로그 응답을 복사한다. 언어를 바꿔도 이미 담긴 줄의 이름은 다시 번역하지 않는다.

---

## 3. 라우트

| 경로 | 파일 | 미들웨어 | 설명 |
|---|---|---|---|
| `/checkout` | `app/pages/checkout.vue` | `auth` | 주문서 |
| `/orders` | `app/pages/orders/index.vue` | `auth` | 내 주문 |
| `/orders/:id` | `app/pages/orders/[id].vue` | `auth` | 상세 + 고객 취소 + 새로고침 |
| `/orders/:id/pay` | `app/pages/orders/[id]/pay.vue` | `auth` | Mock 결제 |
| `/owner/orders` | `app/pages/owner/orders/index.vue` | `owner` | 접수·조리·배달 버튼 |

인증이 필요한 페이지는 기존 `auth` / `owner` 미들웨어를 그대로 쓴다. `owner`는 `ROLE_OWNER`와 `ROLE_ADMIN`을 모두 통과시킨다.

헤더:

| 조건 | 링크 |
|---|---|
| 로그인 | `nav.orders` → `/orders` (기존 `#` 제거) |
| `ROLE_OWNER` 또는 `ROLE_ADMIN` | `nav.manageOrders` → `/owner/orders` |

주문 목록·상세는 사용자 데이터라 `useApiFetch`의 `server: false`로 클라이언트에서만 조회한다.

---

## 4. 타입과 composable

`app/types/order.ts`

- `FulfillmentStatus`, `PaymentStatus` — 백엔드 enum 문자열과 동일
- `Order`, `OrderItem`, `Payment`
- `CreateOrderRequest`

| 파일 | 역할 |
|---|---|
| `app/composables/useCheckout.ts` | 주문 생성 |
| `app/composables/usePayment.ts` | 결제 조회·승인 |
| `app/composables/useMyOrders.ts` | 내 목록·상세·취소·새로고침 |
| `app/composables/useOwnerOrders.ts` | 운영 목록·상태 변경·점주 취소 |

각 composable은 `isLoading`과 `errorMessage`를 둔다. 에러는 `ApiError`의 `error.{CODE}`로 번역한다.

---

## 5. 결제 화면

`/checkout`에서 `POST /orders`.

- 헤더 `Idempotency-Key`는 주문서에 들어온 뒤 한 번 만든 UUID를 재사용한다. 제출 버튼 연타가 주문을 두 개 만들지 않게 한다
- body의 품목은 장바구니의 `foodId`와 `quantity`만 보낸다. 가격은 보내지 않는다
- 수령인, 전화, 주소 필수. 요청사항은 200자
- 201 또는 멱등 재전송 200이면 장바구니를 비우고 `/orders/{id}/pay`로 이동한다

`/orders/:id/pay`

1. `GET /payments?orderId={id}`
2. 404 `PAYMENT_NOT_FOUND`면 500ms 간격으로 최대 10번 다시 조회한다. 이벤트 도착 전 공백이다
3. 10번 후에도 없으면 `error.PAYMENT_NOT_READY`를 보여 주고 다시 시도 버튼을 둔다
4. 카드번호, 유효기간, CVC를 받아 `POST /payments/{id}/approve`
5. 화면 안내: 끝이 `0000`인 16자리 카드는 거절 테스트용이다. 그 외 16자리는 승인된다
6. 200이면 `/orders/{id}`로 이동한 뒤, `paymentStatus`가 `PAID`가 될 때까지 500ms 간격으로 주문 상세를 최대 5번 다시 읽는다. 5번 안에 안 바뀌면 새로고침 버튼만 남긴다
7. 422 `PAYMENT_FAILED`면 결제 화면에 머물고 카드를 다시 받게 한다

이 재조회는 결제 직후 한 번뿐이다. 상세 화면에 상시 타이머를 두지 않는다.

---

## 6. 내 주문

`/orders`는 `GET /orders?page&size`. 가게명, 총액, 이행 상태, 결제 상태, 생성 시각.

`/orders/:id`

- `GET /orders/{id}`로 품목·배송지·두 상태를 그린다
- **새로고침** 버튼이 같은 GET을 다시 호출한다
- `fulfillmentStatus === 'PENDING'`일 때만 주문 취소. `POST /orders/{id}/cancel` 후 응답으로 화면을 갱신한다
- 취소 확인은 `window.confirm`으로 충분하다

상태 문구는 `order.status.{FULFILLMENT}` / `order.paymentStatus.{PAYMENT}` 키다. 백엔드 한글 문장을 그대로 보여 주지 않는다.

---

## 7. 점주 운영 화면

`/owner/orders`. `GET /orders/managed`.

- 점주는 자기 `restaurant_owner_id` 주문만 온다
- 운영자(`ROLE_ADMIN`)는 전체가 온다. 가게 ID 필터 입력은 선택
- 이행 상태 필터 셀렉트
- 각 행에 다음 버튼 **하나**와, 종료 상태가 아니면 취소 버튼

| 현재 이행 | 버튼 | 호출 |
|---|---|---|
| `PENDING` 이고 결제 `PAID` | 접수 | `PATCH /orders/{id}/status` `{ "status": "ACCEPTED" }` |
| `PENDING` 이고 결제 `PAID`가 아님 | 접수 비활성, `order.waitingPayment` 표시 | 호출하지 않음 |
| `ACCEPTED` | 조리 시작 | `PREPARING` |
| `PREPARING` | 조리 완료 | `READY` |
| `READY` | 배달 시작 | `DELIVERING` |
| `DELIVERING` | 배달 완료 | `DELIVERED` |
| `DELIVERED` / `CANCELLED` | 다음 버튼 없음 | |
| 종료 전 | 주문 취소 | `POST /orders/{id}/cancel` |

성공 응답의 주문으로 그 행만 바꾼다. 목록 전체를 다시 받을 필요는 없다. 화면 상단 새로고침은 `GET /orders/managed`를 다시 호출한다.

배달 완료 주문의 환불은 이 화면의 일이 아니다. `POST /orders/{id}/refund`는 운영자 전용이며, 주문 상세를 운영자가 열었을 때만 버튼으로 둔다 (`/orders/:id`, `role === 'ROLE_ADMIN'`, `DELIVERED` + `PAID`).

---

## 8. i18n

`ko.json` / `ja.json`에 추가한다. 에러 코드는 `error.*`.

| 키 | ko | ja |
|---|---|---|
| `nav.orders` | 주문내역 | 注文履歴 |
| `nav.manageOrders` | 주문 관리 | 注文管理 |
| `order.status.PENDING` | 접수 대기 | 受付待ち |
| `order.status.ACCEPTED` | 접수됨 | 受付済み |
| `order.status.PREPARING` | 조리 중 | 調理中 |
| `order.status.READY` | 조리 완료 | 調理完了 |
| `order.status.DELIVERING` | 배달 중 | 配達中 |
| `order.status.DELIVERED` | 배달 완료 | 配達完了 |
| `order.status.CANCELLED` | 취소됨 | キャンセル |
| `order.paymentStatus.UNPAID` | 결제 대기 | 支払い待ち |
| `order.paymentStatus.PAYMENT_FAILED` | 결제 실패 | 支払い失敗 |
| `order.paymentStatus.PAID` | 결제 완료 | 支払い完了 |
| `order.paymentStatus.REFUND_PENDING` | 환불 진행 | 返金中 |
| `order.paymentStatus.REFUNDED` | 환불 완료 | 返金完了 |
| `order.action.accept` | 접수 | 受け付ける |
| `order.action.prepare` | 조리 시작 | 調理開始 |
| `order.action.ready` | 조리 완료 | 調理完了 |
| `order.action.deliver` | 배달 시작 | 配達開始 |
| `order.action.delivered` | 배달 완료 | 配達完了 |
| `order.action.cancel` | 주문 취소 | 注文キャンセル |
| `order.action.refresh` | 새로고침 | 更新 |
| `order.waitingPayment` | 결제 완료 후 접수할 수 있습니다 | 支払い完了後に受付できます |
| `error.PAYMENT_NOT_READY` | 결제 정보를 아직 만들지 못했습니다. 다시 시도해 주세요 | 支払い情報をまだ準備できていません。もう一度お試しください |

백엔드 코드 `ORDER_NOT_FOUND`, `ORDER_NOT_PAID`, `MIN_ORDER_AMOUNT_NOT_MET`, `FOOD_UNAVAILABLE`, `PAYMENT_FAILED`, `PAYMENT_ALREADY_COMPLETED`도 `error.*`에 한국어·일본어 문장을 둔다.

---

## 9. 화면 상태

- 로딩: 목록·상세는 스켈레톤, 버튼 제출 중에는 해당 버튼만 비활성
- 빈 목록: `order.empty`
- 운영 목록이 403이면 홈으로 보낸다 (`owner` 미들웨어를 통과한 뒤에 발생하면 안 된다)
- 상세 404: `error.ORDER_NOT_FOUND`와 목록으로 돌아가는 링크
