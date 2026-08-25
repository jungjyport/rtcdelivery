# RTC Delivery — Tasks

> 서비스별 기능 단위 작업 목록입니다. 각 태스크에 관련 스펙 문서를 연결해, 구현을 시작할 때 무엇을 읽어야 하는지 바로 찾을 수 있게 합니다.

**[development-roadmap.md](./development-roadmap.md)와의 역할 분담**

| 문서 | 다루는 것 |
|---|---|
| `development-roadmap.md` | **언제** — Phase 단위의 순서와 의존 관계 |
| `tasks.md` (이 문서) | **무엇을** — 서비스별 기능 단위 체크리스트와 스펙 링크 |

같은 작업이 양쪽에 나타날 수 있습니다. 진행 상태의 진실의 원천은 **이 문서**이며, 로드맵은 Phase 전체의 완료 여부만 갱신합니다.

---

## 작성 규칙

1. **그룹은 서비스 단위**로 나눈다. MSA 경계와 일치시켜야 어떤 저장소·패키지를 건드릴지 바로 드러난다.
2. **체크박스는 기능 단위**로 단다. `- [ ]` 미완료, `- [x]` 완료.
3. **백엔드와 프론트엔드가 모두 필요한 기능**은 하위 체크박스로 나눈다. 한쪽만 끝난 상태를 표현할 수 있어야 한다.
4. **스펙 링크는 하위 불릿으로** 붙인다. 별도 줄로 쓰면 리스트가 끊긴다.
   ```markdown
   - [ ] **로그인**
     - 스펙: [api-client-spec.md](./sdd-spec-docs/feature/nuxt-app/api-client-spec.md)
   ```
5. **스펙이 아직 없으면** `_(스펙 미작성)_`으로 표시한다. 링크 없는 태스크가 곧 "스펙부터 써야 할 항목"이다.
6. **진행 중**은 태스크명 뒤에 🟡, **막힌 항목**은 🔴와 사유를 적는다. 그 외에는 아무 표시도 하지 않는다.
7. 기능이 완료되면 관련 Phase가 모두 끝났는지 확인하고 로드맵을 함께 갱신한다.

**상태 범례**: 🟡 진행 중 · 🔴 블로킹 · 표시 없음 = 미착수 또는 완료(체크박스로 판단)

---

## 1. frontend / nuxt-app

- [x] **@nuxtjs/i18n 도입** (ko / ja)
  - 스펙: [internationalization.md](./internationalization.md)
- [x] **메인 페이지 i18n 적용 및 언어 전환 UI**
- [x] **API Client 전환 (axios → `$fetch`)**
  - 스펙: [api-client-spec.md](./sdd-spec-docs/feature/nuxt-app/api-client-spec.md)
  - [x] `types/api.ts` — `ApiError` / `ApiClient` / `ApiRequestOptions`
  - [x] `stores/auth.ts` — 메모리 토큰 + `isRestoring` + `restoreSession()`
  - [x] `utils/api-client.ts` — `$fetch` 래핑, `ApiResponse` 언랩, 401 갱신 큐
  - [x] `plugins/api.ts` — `$api` 등록
  - [x] `plugins/auth-restore.client.ts` — 전역 세션 복구 (await 없음)
  - [x] `middleware/auth.ts` — 인증 라우트 가드
  - [x] `composables/useApiFetch.ts` — SSR 프리페치 래퍼
  - [x] i18n `error.*` 클라이언트 에러 코드 추가
  - [x] `axios` 의존성 및 예제 파일 제거
- [ ] **공통 레이아웃**
  - [ ] 글로벌 헤더 (인증 상태 3-state 렌더링 적용)
  - [ ] 글로벌 푸터
- [ ] **인증 화면**
  - [ ] 로그인 페이지
  - [ ] 회원가입 페이지
  - [ ] 로그아웃 처리
  - [ ] OAuth 진입 (Google / Kakao)
- [ ] **음식점 · 메뉴 화면**
  - [ ] 카테고리 목록
  - [ ] 음식점 목록 (SSR 프리페치)
  - [ ] 음식점 상세 · 메뉴 목록
  - [ ] 검색
- [ ] **장바구니** — Pinia 스토어 + Drawer UI
- [ ] **주문 · 결제 화면**
  - [ ] 주문서 작성
  - [ ] 결제 진행
  - [ ] 주문 내역 / 주문 상세
- [ ] **주문 실시간 추적** — WebSocket 또는 SSE 구독
  - _(스펙 미작성)_

---

## 2. member-auth-service

> 계약 방식: **Swagger (Code-first)** — Controller/DTO 어노테이션이 진실의 원천

- [x] **스캐폴딩** — `ApiResponse`, `BusinessException`, `GlobalExceptionHandler`, `SecurityConfig`
- [ ] **Member 도메인** — 엔티티(`BaseTimeEntity` 상속), Repository, DTO
- [ ] **회원가입**
  - [ ] BE — `POST /api/v1/auth/signup`, 중복 검사, `PasswordEncoder`
  - [ ] FE — 회원가입 폼 + 검증
- [ ] **로그인 (JWT 발급)**
  - [ ] BE — `POST /api/v1/auth/login`, Access Token 발급 + Refresh Token 쿠키 세팅
  - [ ] FE — 로그인 폼 + 인증 스토어 반영
  - 스펙: [api-conventions.md §7](./api-conventions.md#7-인증-및-보안-authentication--security)
- [ ] **Refresh Token**
  - 스펙: [api-conventions.md §7.2](./api-conventions.md#72-토큰-갱신-refresh-계약) · [api-client-spec.md §10](./sdd-spec-docs/feature/nuxt-app/api-client-spec.md#10-백엔드-요구사항-member-auth-service--api-gateway)
  - [ ] Redis에 Refresh Token 저장 / 검증
  - [ ] `POST /api/v1/auth/refresh-token` — httpOnly 쿠키 기반, `permitAll()`
  - [ ] 쿠키 속성 (dev `SameSite=Lax` / prod `SameSite=None; Secure`)
  - [ ] 실패 에러 코드 정의 (`REFRESH_TOKEN_EXPIRED` 등)
- [ ] **로그아웃** — `POST /api/v1/auth/logout`, Refresh Token 무효화 + 쿠키 삭제
- [ ] **내 정보** — `GET /api/v1/auth/me` (세션 복구 시 사용)
- [ ] **OAuth 로그인** — Google / Kakao, `AuthProvider` 분기
  - _(스펙 미작성 — 소셜 로그인 플로우 스펙 선행 필요)_
- [ ] **단위 테스트 70% 이상**

---

## 3. food-catalog-service

> 계약 방식: **Swagger (Code-first)**

- [x] **스캐폴딩**
- [ ] **카테고리** — 엔티티 + CRUD API
- [ ] **음식점(Restaurant)** — 엔티티 + CRUD API + 페이지네이션/정렬
- [ ] **메뉴(Food)** — 엔티티 + CRUD API
- [ ] **검색** — 음식점/메뉴 검색
- [ ] **다국어 데이터 연동**
  - 스펙: [translation-system.md](./translation-system.md) · [internationalization.md §2-B](./internationalization.md#b-dynamic-domain-data--translation-table)
  - [ ] `restaurant_translation` / `menu_translation` 스키마
  - [ ] `Accept-Language` 기반 조회
  - [ ] 등록/수정 시 번역 요청 이벤트 발행
- [ ] **Redis 캐싱** — 메뉴/음식점 조회 캐시
- [ ] **단위 테스트 70% 이상**

---

## 4. order-service

> 계약 방식: **Swagger (Code-first)**

- [x] **스캐폴딩**
- [ ] **Order / OrderItem 도메인** — 엔티티 설계
- [ ] **주문 생성** — `POST /api/v1/orders`
- [ ] **주문 조회** — 내 주문 목록 / 주문 상세
- [ ] **주문 상태 관리** — `PENDING` → `ACCEPTED` → `PREPARING` → `READY` → `DELIVERING` → `DELIVERED` / `CANCELLED`
- [ ] **Transactional Outbox** — `outbox_event` 테이블 + Polling Publisher
  - 스펙: [AGENTS.md §4.1](./AGENTS.md)
- [ ] **Kafka 이벤트 발행** — `order-events`
- [ ] **결제 결과 소비** — `payment-events` Consumer + Inbox 멱등성
- [ ] **실시간 알림** — WebSocket(STOMP) 또는 SSE
  - _(스펙 미작성)_
- [ ] **단위 테스트 70% 이상**

---

## 5. payment-service

> 계약 방식: **Swagger (Code-first)**

- [x] **스캐폴딩**
- [ ] **Payment 도메인** — 엔티티 + 결제 상태 관리
- [ ] **결제 승인 / 취소** — Mock PG 연동
- [ ] **환불**
- [ ] **주문 이벤트 소비** — `order-events` Consumer + Inbox 멱등성
- [ ] **결제 결과 발행** — `payment-events` (Outbox 경유)
- [ ] **Saga 보상 트랜잭션** — 결제 실패 시 주문 취소 연동
- [ ] **단위 테스트 70% 이상**

---

## 6. api-gateway

- [x] **스캐폴딩**
- [ ] **서비스 라우팅** — Eureka 서비스명 기반 라우팅
- [ ] **JWT 인증 필터** — 검증 후 `X-User-Id` / `X-User-Role` 헤더 전달
- [ ] **CORS 설정**
  - 스펙: [api-conventions.md §7.3](./api-conventions.md#73-cors) · [api-client-spec.md §10.3](./sdd-spec-docs/feature/nuxt-app/api-client-spec.md#103-cors)
  - [ ] `allowCredentials: true` + 구체 오리진 명시 (와일드카드 금지)
  - [ ] `Authorization` / `Accept-Language` 헤더 허용, Preflight 처리
- [ ] **Rate Limiting** — Redis 기반
- [ ] **JWT 블랙리스트** — 로그아웃 토큰 차단

---

## 7. discovery-service

- [x] **스캐폴딩** — Eureka Server
- [ ] **전 서비스 등록 검증** — 각 서비스 Eureka Client 연동 확인

---

## 8. translation-service (Planned)

> 아직 생성되지 않은 서비스입니다. 착수 전 [AGENTS.md §10.3](./AGENTS.md) 절차를 따릅니다.

- [ ] **서비스 스캐폴딩** — `backend/translation-service/`
  - 스펙: [translation-system.md](./translation-system.md)
- [ ] **번역 요청 소비** — `translation-requests` Consumer
- [ ] **번역 결과 발행** — `translation-results` Producer
- [ ] **Translation DB** — 번역 결과 저장
- [ ] **AI API 연동** — OpenAI / DeepL
- [ ] **Redis 번역 캐싱** (7일)
- [ ] **UGC 실시간 번역 API** — 리뷰 등

---

## 9. 인프라 · 공통

- [x] **docker-compose-dev.yml** — MySQL, Redis, Kafka, Zookeeper
- [x] **설계 문서 세트** — architecture / api-conventions / i18n / translation-system / roadmap
- [ ] **서비스별 Dockerfile** — 6개 백엔드 + Nuxt 앱
- [ ] **docker-compose-prod.yml** — 풀스택 컨테이너화
- [ ] **모니터링** — Actuator + Prometheus + Grafana
- [ ] **분산 트레이싱** — Micrometer Tracing + Zipkin

---

## 스펙 문서 현황

착수 전에 읽어야 할 문서를 서비스별로 정리한 표입니다. AGENTS.md §2.1의 계약 방식 판별 결과를 반영합니다.

| 대상 | 계약 방식 | 스펙 문서 |
|---|---|---|
| frontend / nuxt-app | 문서 기반 | [api-client-spec.md](./sdd-spec-docs/feature/nuxt-app/api-client-spec.md) |
| member-auth-service | Swagger (Code-first) | 어노테이션 + [api-conventions.md §7](./api-conventions.md#7-인증-및-보안-authentication--security) |
| food-catalog-service | Swagger (Code-first) | 어노테이션 + [translation-system.md](./translation-system.md) |
| order-service | Swagger (Code-first) | 어노테이션 |
| payment-service | Swagger (Code-first) | 어노테이션 |
| api-gateway | — | [api-conventions.md §7.3](./api-conventions.md#73-cors) |
| translation-service | 미정 | [translation-system.md](./translation-system.md) |

> 새 스펙 문서는 `docs/sdd-spec-docs/feature/{service-name}/`에 두고, 이 표와 해당 태스크에 링크를 추가합니다.
