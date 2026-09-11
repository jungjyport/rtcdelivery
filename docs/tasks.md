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
- [x] **공통 레이아웃**
  - 스펙: [layout-spec.md](./sdd-spec-docs/feature/nuxt-app/layout-spec.md)
  - [x] 글로벌 헤더 (인증 상태 3-state 렌더링 적용)
  - [x] 글로벌 푸터
- [ ] **인증 화면**
  - 스펙: [auth-pages-spec.md](./sdd-spec-docs/feature/nuxt-app/auth-pages-spec.md) — 화면 · 검증 · i18n
  - API 계약: [auth-jwt-spec.md §3](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md#3-엔드포인트)
  - [x] 로그인 페이지 — `POST /auth/login` → `authStore.setAuth(accessToken, user)`
  - [x] 회원가입 페이지 — `POST /auth/signup`, 백엔드와 동일한 검증 규칙 적용
  - [x] 로그아웃 처리 — `POST /auth/logout` → `clearAuth()`
  - [x] i18n `error.*` 인증 에러 코드 추가 ([error-handling.md §3.2](./error-handling.md#32-인증--회원-member-auth-service--api-gateway))
  - [ ] OAuth 진입 (Google / Kakao)
    - _(스펙 미작성)_
- [x] **음식점 · 메뉴 화면**
  - 스펙: [catalog-pages-spec.md](./sdd-spec-docs/feature/nuxt-app/catalog-pages-spec.md)
  - API 계약: [catalog-spec.md](./sdd-spec-docs/feature/food-catalog-service/catalog-spec.md)
  - [x] `types/catalog.ts` 타입 정의 및 `useCatalog.ts` composable
  - [x] 카테고리 목록 컴포넌트 (`CategoryResponse`, `$t('category.' + code)`)
  - [x] 음식점 목록 페이지 (`/restaurants`, `useApiFetch` SSR 프리페치, 카테고리 필터, 정렬, 페이지네이션)
  - [x] 음식점 상세 및 메뉴 목록 페이지 (`/restaurants/:id`, 매장 정보, 메뉴 카드 목록, 품절 표시)
  - [x] 음식점 & 메뉴 통합 검색 페이지 (`/search`, 음식점/메뉴 탭 분리)
  - [x] `SearchBar` 공통 컴포넌트 분리 (`components/common/SearchBar.vue`, size=md/sm, clear 버튼, `@search` 이벤트) — 홈 히어로 & `/restaurants` 인라인 적용
- [x] **점주 매장 · 메뉴 관리 화면**
  - 스펙: [owner-store-spec.md](./sdd-spec-docs/feature/nuxt-app/owner-store-spec.md)
  - API 계약: [catalog-spec.md §3](./sdd-spec-docs/feature/food-catalog-service/catalog-spec.md#3-엔드포인트) · [role-management-spec.md](./sdd-spec-docs/feature/member-auth-service/role-management-spec.md)
  - [x] 점주 권한 가드 미들웨어 (`owner.ts` — `ROLE_OWNER` / `ROLE_ADMIN`)
  - [x] 글로벌 헤더 내 점주 네비게이션 ('매장 관리') 연동
  - [x] 점주 매장 목록 및 신규 등록 (`/owner/restaurants`, `POST /api/v1/restaurants`)
  - [x] 점주 매장 수정/비활성화 및 메뉴 관리 (`/owner/restaurants/:id`, `PATCH/DELETE`, 메뉴 등록/수정/삭제/품절 토글)
  - [x] `useOwnerStore.ts` composable
- [x] **관리자 역할 관리 화면**
  - 스펙: [admin-role-spec.md](./sdd-spec-docs/feature/nuxt-app/admin-role-spec.md)
  - API 계약: [role-management-spec.md §3](./sdd-spec-docs/feature/member-auth-service/role-management-spec.md#3-역할-변경-api)
  - [x] 관리자 권한 가드 미들웨어 (`admin.ts` — `ROLE_ADMIN`)
  - [x] 글로벌 헤더 내 관리자 네비게이션 ('역할 관리') 연동
  - [x] 회원 역할 승격/강등 UI (`/admin/members`, `PATCH /api/v1/members/:memberId/role`)
  - [x] `useRoleManagement.ts` composable
- [x] **마이페이지**
  - [x] 내 프로필 정보 조회 (`GET /api/v1/auth/me`) 연동
  - [x] 인증 라우트 가드 (`middleware/auth.ts`) 적용
  - [x] 헤더 연동 및 i18n (ko/ja) 적용
- [x] **음식점 · 메뉴 화면**
  - [x] 카테고리 목록
  - [x] 음식점 목록 (SSR 프리페치)
  - [x] 음식점 상세 · 메뉴 목록
  - [x] 검색
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
> 스펙: [auth-jwt-spec.md](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md) — 이 서비스의 모든 인증 태스크가 이 문서를 따릅니다.

- [x] **스캐폴딩** — `ApiResponse`, `BusinessException`, `GlobalExceptionHandler`, `SecurityConfig`
- [x] **에러 처리 정비** — `ErrorCode` enum, `BusinessException` 재작성, `GlobalExceptionHandler` 확장
  - 스펙: [error-handling.md §4](./error-handling.md#4-mvc-서비스-구현-member-auth--food-catalog--order--payment)
  - [x] `ApiResponse`의 `@JsonInclude(NON_NULL)` 제거 (에러 응답에서 `data` 키가 사라져 프론트 파싱이 깨짐)
  - [x] `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` — 필터 단계 401/403도 `ApiResponse` 포맷으로
- [x] **공통 JPA 기반** — `BaseTimeEntity` + `JpaAuditingConfig`
  - 스펙: [auth-jwt-spec.md §2.2](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md#22-basetimeentity)
- [x] **Member 도메인** — `Member` 엔티티, `Role` / `AuthProvider` enum, `MemberRepository`
  - 스펙: [auth-jwt-spec.md §2](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md#2-데이터-모델)
- [x] **JWT 발급 기반 (RS256)** — RSA 키 생성, `JwtProperties`, `JwtProvider`
  - 스펙: [auth-jwt-spec.md §4](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md#4-jwt-설계)
  - [x] 키 쌍 생성 + `.gitignore` 등록 (개인키는 절대 커밋 금지)
  - [x] `jwt.secret` / `jwt.expiration` 등 HS256 잔재 설정 제거
  - [x] Access Token 클레임 (`sub`, `userId`, `role`, `typ=access`), 수명 30분
- [x] **회원가입**
  - 스펙: [auth-jwt-spec.md §3.1](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md#31-회원가입--post-apiv1authsignup) · FE [auth-pages-spec.md](./sdd-spec-docs/feature/nuxt-app/auth-pages-spec.md)
  - [x] BE — `POST /api/v1/auth/signup`, 중복 검사, `PasswordEncoder`
  - [x] FE — 회원가입 폼 + 검증
- [x] **로그인 (JWT 발급)**
  - 스펙: [auth-jwt-spec.md §3.2](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md#32-로그인--post-apiv1authlogin) · FE [auth-pages-spec.md](./sdd-spec-docs/feature/nuxt-app/auth-pages-spec.md)
  - [x] BE — `POST /api/v1/auth/login`, Access Token 발급 + Refresh Token 쿠키 세팅
  - [x] FE — 로그인 폼 + 인증 스토어 반영
- [x] **Refresh Token Rotation (Redis)**
  - 스펙: [auth-jwt-spec.md §5](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md#5-refresh-token-rotation-rtr) · [api-conventions.md §7.2](./api-conventions.md#72-토큰-갱신-refresh-계약)
  - [x] `RedisConfig` + `RefreshTokenService` — 키 `RT:{username}`, TTL 7일, **단일 세션 정책**
  - [x] `POST /api/v1/auth/refresh-token` — httpOnly 쿠키 기반, `permitAll()`, 매 갱신마다 RT 회전
  - [x] 재사용 탐지 — 저장값 불일치 시 세션 전체 무효화
  - [x] 쿠키 속성 (dev `SameSite=Lax` / prod `SameSite=None; Secure`), `Path=/api/v1/auth`
  - [x] 실패 시 쿠키 삭제 헤더 동반 + 에러 코드 (`REFRESH_TOKEN_EXPIRED` 등)
- [x] **로그아웃** — `POST /api/v1/auth/logout`, Redis `DEL` + 쿠키 삭제, **항상 200 반환**
- [x] **내 정보** — `GET /api/v1/auth/me` (세션 복구 시 사용)
- [x] **Gateway 헤더 인증** — `HeaderAuthenticationFilter` + `SecurityConfig`를 `authenticated()`로 잠금
  - 스펙: [auth-jwt-spec.md §6](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md#6-security-구성)
  - 선행: api-gateway의 JWT 검증 필터. 헤더가 주입되기 전에는 `/auth/me`가 401만 반환한다
- [x] **점주 역할(`ROLE_OWNER`) 추가**
  - 스펙: [role-management-spec.md](./sdd-spec-docs/feature/member-auth-service/role-management-spec.md) _(구현 후 작성)_
  - `RoleHierarchy`는 쓰지 않는다. 회원은 역할을 하나만 갖고, 로그인 사용자 전체를 뜻하는 자리에는 `isAuthenticated()`를, 역할이 특정되는 자리에는 `hasAnyRole(...)`을 명시한다
  - [x] `Role` enum에 `ROLE_OWNER` 추가 (`ROLE_USER` / `ROLE_OWNER` / `ROLE_ADMIN`)
  - [x] `Member.changeRole()` 도메인 메서드 (`@Setter` 금지 규칙 준수)
  - [x] 승격 API — `PATCH /api/v1/members/{memberId}/role`, `@PreAuthorize("hasRole('ADMIN')")`
  - [x] `@EnableMethodSecurity` + 자기 자신 강등 차단 (`CANNOT_CHANGE_OWN_ROLE`)
  - [x] 역할 변경 시 Redis `RT:{username}` 삭제 — 기존 Access Token은 최대 30분간 옛 역할을 유지하므로, 강등이 다음 갱신에서 반영되게 한다
- [ ] **OAuth 로그인** — Google / Kakao, `AuthProvider` 분기
  - _(스펙 미작성 — 소셜 로그인 플로우 스펙 선행 필요)_
- [x] **단위 테스트 70% 이상**
  - 스펙: [auth-jwt-spec.md §9](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md#9-테스트-계획)

---

## 3. food-catalog-service

> 계약 방식: **Swagger (Code-first)**
> 설계 스펙: [catalog-spec.md](./sdd-spec-docs/feature/food-catalog-service/catalog-spec.md) _(구현 후 작성)_

- [x] **스캐폴딩**
- [x] **에러 처리 정비** — `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`, `ApiResponse`의 `@JsonInclude` 제거
  - 스펙: [error-handling.md §4](./error-handling.md#4-mvc-서비스-구현-member-auth--food-catalog--order--payment)
- [x] **Gateway 헤더 인증** — `HeaderAuthenticationFilter` + `@EnableMethodSecurity`
  - 스펙: [gateway-auth-spec.md §4](./sdd-spec-docs/feature/api-gateway/gateway-auth-spec.md#4-jwtverificationfilter)
  - member-auth의 필터를 그대로 옮겼다. 이 필터가 없으면 `SecurityContext`가 비어 `@PreAuthorize`가 전부 거부한다
  - 비로그인 401과 역할 불일치 403을 구분하려면 **필터 레벨에서 `authenticated()`로 먼저 거른다.** `GlobalExceptionHandler`가 `AccessDeniedException`을 잡기 때문에, 메서드 시큐리티에만 의존하면 익명 사용자도 403을 받는다
- [x] **카테고리** — 엔티티 + CRUD API
  - 번역 테이블을 두지 않는다. 프론트 i18n `category.{code}` 키가 담당하므로 `code`가 프론트와의 계약이다
- [x] **음식점(Restaurant)** — 엔티티 + CRUD API + 페이지네이션/정렬
  - [x] `ownerId` 필드 — 등록 시 `X-User-Id`로 채운다
  - [x] 쓰기 API에 `hasAnyRole('OWNER','ADMIN')`
  - [x] 소유권 검증 — 수정/삭제 시 `ownerId`와 현재 사용자 비교. 불일치는 404 (403은 리소스 존재를 노출한다)
- [x] **메뉴(Food)** — 엔티티 + CRUD API
  - [x] 소유권 검증 — 소속 음식점의 `ownerId` 경유 + `(foodId, restaurantId)` 동시 조회
- [x] **검색** — 음식점/메뉴 검색
  - 원본명과 요청 locale의 번역명을 함께 검색한다. 컬렉션 join 대신 `EXISTS` 서브쿼리를 쓴 이유는 페이지네이션 정확성이다
- [ ] **다국어 데이터 연동** 🟡
  - 스펙: [translation-system.md](./translation-system.md) · [internationalization.md §2-B](./internationalization.md#b-dynamic-domain-data--translation-table) · [architecture.md §8.1](./architecture.md#81-번역-데이터-소유권)
  - [x] `restaurant_translation` / `menu_translation` 스키마 — **food-catalog DB가 소유**한다 (조회 JOIN 때문)
  - [x] `Accept-Language` 기반 조회 + 번역 부재 시 `ko` 폴백
  - [x] 원본 텍스트 수정 시 기존 번역 폐기 (옛 원문을 가리키는 번역을 남기지 않는다)
  - [ ] 등록/수정 시 번역 요청 이벤트 발행 (Outbox 경유)
  - [ ] `translation-results` 소비 + Inbox 멱등성 → 번역 테이블 upsert
- [x] **개발용 시드 데이터** — `ddl-auto` + `data.sql` (카테고리 12 · 음식점 4 · 메뉴 10 · 일본어 번역 일부)
- [ ] **Redis 캐싱** — 메뉴/음식점 조회 캐시
- [x] **단위 테스트 70% 이상** — JaCoCo 기준 라인 80.6% / 브랜치 73.8% (`gradle check`에 70% 게이트 연결)

---

## 4. order-service

> 계약 방식: **Swagger (Code-first)**

- [x] **스캐폴딩**
- [x] **에러 처리 정비** — `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`, `ApiResponse`의 `@JsonInclude` 제거
  - 스펙: [error-handling.md §4](./error-handling.md#4-mvc-서비스-구현-member-auth--food-catalog--order--payment)
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
- [x] **에러 처리 정비** — `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`, `ApiResponse`의 `@JsonInclude` 제거
  - 스펙: [error-handling.md §4](./error-handling.md#4-mvc-서비스-구현-member-auth--food-catalog--order--payment)
- [ ] **Payment 도메인** — 엔티티 + 결제 상태 관리
- [ ] **결제 승인 / 취소** — Mock PG 연동
- [ ] **환불**
- [ ] **주문 이벤트 소비** — `order-events` Consumer + Inbox 멱등성
- [ ] **결제 결과 발행** — `payment-events` (Outbox 경유)
- [ ] **Saga 보상 트랜잭션** — 결제 실패 시 주문 취소 연동
- [ ] **단위 테스트 70% 이상**

---

## 6. api-gateway

> 스택: **WebFlux (Reactive)** — `spring-cloud-starter-gateway` + Netty. 이미 Reactive이므로 WebFlux 마이그레이션은 불필요합니다.
> 스펙: [gateway-auth-spec.md](./sdd-spec-docs/feature/api-gateway/gateway-auth-spec.md)

- [x] **스캐폴딩**
- [x] **서비스 라우팅** — Eureka `lb://` 기반 라우팅 (`application.properties`에 선언)
- [x] **에러 처리** — `ApiResponse`, `ErrorCode`, `BusinessException`, `ErrorResponseWriter`, `GatewayErrorWebExceptionHandler`
  - 스펙: [error-handling.md §5](./error-handling.md#5-gateway-구현-webflux) · [gateway-auth-spec.md §5](./sdd-spec-docs/feature/api-gateway/gateway-auth-spec.md#5-gatewayerrorwebexceptionhandler)
  - [x] `ErrorWebExceptionHandler` + `@Order(-2)` (`@RestControllerAdvice`는 Gateway 프록시 예외를 잡지 못함)
  - [x] 응답을 `ApiResponse` 포맷으로 통일 (참고 코드의 `{code,message,status,timestamp}` 형식은 프론트 파싱과 불일치)
  - [x] 커밋 가드 (`response.isCommitted()`) + `ObjectMapper` 직렬화
- [x] **CORS 설정**
  - 스펙: [gateway-auth-spec.md §6](./sdd-spec-docs/feature/api-gateway/gateway-auth-spec.md#6-cors) · [api-conventions.md §7.3](./api-conventions.md#73-cors)
  - [x] `SecurityConfig`로 일원화하고 `spring.cloud.gateway.globalcors.*` 제거 (헤더 중복 부착 방지)
  - [x] `allowCredentials: true` + 구체 오리진 명시 (와일드카드 금지)
  - [x] `Authorization` / `Content-Type` / `Accept-Language` 헤더 **명시적으로 나열**, Preflight 처리
- [x] **JWT 검증 (RS256 공개키)** — `JwtProperties` + `JwtValidator`
  - 스펙: [gateway-auth-spec.md §3](./sdd-spec-docs/feature/api-gateway/gateway-auth-spec.md#3-jwtvalidator)
  - [x] `jwt.secret`(HS256 대칭키) 제거 — Gateway가 서명 능력을 갖지 않게 한다
  - [x] 서명 · 만료 · `iss` · `typ=access` 검증, 실패 사유 구분
- [x] **JWT 인증 필터** — `JwtVerificationFilter implements GlobalFilter, Ordered` (order `-10`)
  - 스펙: [gateway-auth-spec.md §4](./sdd-spec-docs/feature/api-gateway/gateway-auth-spec.md#4-jwtverificationfilter)
  - [x] **클라이언트가 보낸 `X-User-*` 헤더 제거를 최우선 수행** (제외 경로에서도)
  - [x] 검증 후 `X-User-Id` / `X-User-Name` / `X-User-Role` 주입
  - [x] 제외 경로 — `/auth/{signup,login,refresh-token,logout}`, actuator, swagger, **`OPTIONS` 전체**
  - [x] 401 응답을 `ApiResponse` 포맷으로
- [ ] **Rate Limiting** — Redis 기반
- [ ] **JWT 블랙리스트** — 로그아웃 토큰 차단
  - _(스펙 미작성 — 현재는 AT 수명 30분으로 노출 창을 제한하는 것으로 갈음)_

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
- [ ] **Mock 번역기** — 사전/에코 기반. AI 키 없이 파이프라인을 E2E로 검증한 뒤 실제 API로 교체한다
- [ ] **번역 결과 발행** — `translation-results` Producer
- [ ] **Translation DB (`rtc_translation`)** — 번역 이력 · 고유명사 사전 · UGC 번역
  - 조회용 `restaurant_translation` / `menu_translation`은 이 서비스가 아니라 **food-catalog가 소유**한다 ([architecture.md §8.1](./architecture.md#81-번역-데이터-소유권))
- [ ] **AI API 연동** — OpenAI / DeepL
- [ ] **Redis 번역 캐싱** (7일)
- [ ] **DLQ + Exponential Backoff** — 재시도 소진 시 격리
- [ ] **UGC 실시간 번역 API** — 리뷰 등

---

## 9. 인프라 · 공통

- [x] **docker-compose-dev.yml** — Redis, Kafka, Zookeeper + 프론트/백엔드 서비스. MySQL은 호스트 PC를 remote DB로 사용
- [x] **설계 문서 세트** — architecture / api-conventions / error-handling / i18n / translation-system / roadmap
- [x] **JWT 키 관리** — RSA 키 쌍 생성 절차 문서화, 개인키 `.gitignore` 등록, prod 환경변수 주입
  - 스펙: [auth-jwt-spec.md §4.1](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md#41-알고리즘-및-키-관리)
- [x] **서비스별 Dockerfile** — 6개 백엔드 (`docker/backend/Dockerfile`) + Nuxt (`docker/frontend/Dockerfile.dev`)
- [ ] **docker-compose-prod.yml** — 풀스택 컨테이너화
- [ ] **모니터링** — Actuator + Prometheus + Grafana
- [ ] **분산 트레이싱** — Micrometer Tracing + Zipkin

---

## 스펙 문서 현황

착수 전에 읽어야 할 문서를 서비스별로 정리한 표입니다. AGENTS.md §2.1의 계약 방식 판별 결과를 반영합니다.

| 대상 | 계약 방식 | 스펙 문서 |
|---|---|---|
| frontend / nuxt-app | 문서 기반 | [api-client-spec.md](./sdd-spec-docs/feature/nuxt-app/api-client-spec.md) · [auth-pages-spec.md](./sdd-spec-docs/feature/nuxt-app/auth-pages-spec.md) · [layout-spec.md](./sdd-spec-docs/feature/nuxt-app/layout-spec.md) · [catalog-pages-spec.md](./sdd-spec-docs/feature/nuxt-app/catalog-pages-spec.md) · [owner-store-spec.md](./sdd-spec-docs/feature/nuxt-app/owner-store-spec.md) · [admin-role-spec.md](./sdd-spec-docs/feature/nuxt-app/admin-role-spec.md) |
| member-auth-service | Swagger (Code-first) | 어노테이션 + [auth-jwt-spec.md](./sdd-spec-docs/feature/member-auth-service/auth-jwt-spec.md) · [role-management-spec.md](./sdd-spec-docs/feature/member-auth-service/role-management-spec.md) |
| food-catalog-service | Swagger (Code-first) | 어노테이션 + [catalog-spec.md](./sdd-spec-docs/feature/food-catalog-service/catalog-spec.md) · [translation-system.md](./translation-system.md) |
| order-service | Swagger (Code-first) | 어노테이션 |
| payment-service | Swagger (Code-first) | 어노테이션 |
| api-gateway | — | [gateway-auth-spec.md](./sdd-spec-docs/feature/api-gateway/gateway-auth-spec.md) |
| translation-service | 미정 | [translation-system.md](./translation-system.md) |

**전 서비스 공통**

| 문서 | 다루는 것 |
|---|---|
| [api-conventions.md](./api-conventions.md) | URI 명명, 응답 래퍼, 페이지네이션, 인증 헤더 |
| [error-handling.md](./error-handling.md) | 에러 응답 구현 방법, 예외 매핑, 에러 코드 카탈로그 |

> 새 스펙 문서는 `docs/sdd-spec-docs/feature/{service-name}/`에 두고, 이 표와 해당 태스크에 링크를 추가합니다.

---

## 다음 작업 (인증 기능)

`auth-jwt-spec.md §10` · `gateway-auth-spec.md §10`의 구현 순서를 요약한 것입니다.

| 순서 | 작업 | 대상 | 상태 |
|---|---|---|---|
| 1 | 에러 처리 정비 (`ErrorCode` / `BusinessException` / `GlobalExceptionHandler`) | MVC 4개 서비스 | 완료 |
| 2 | `GatewayErrorWebExceptionHandler` | api-gateway | 완료 |
| 2-1 | CORS 일원화 (`SecurityConfig`로 이전, `globalcors` 제거) | api-gateway | 완료 |
| 3 | `BaseTimeEntity` + `Member` 도메인 | member-auth-service | 완료 |
| 4 | RSA 키 생성 + `JwtProvider` | member-auth-service | 완료 |
| 5 | 회원가입 → 로그인 (AT 발급 + RT 쿠키) | member-auth-service | 완료 |
| 6 | `RefreshTokenService` (RTR) + `/refresh-token` + `/logout` | member-auth-service | 완료 |
| 7 | `JwtValidator` + `JwtVerificationFilter` | api-gateway | 완료 |
| 8 | `HeaderAuthenticationFilter` + `/auth/me` + Security 잠금 | member-auth-service | 완료 |
| 9 | 로그인 · 회원가입 화면 | nuxt-app | 완료 |

7과 8은 한 묶음입니다. Gateway가 헤더를 주입하기 전에는 `/auth/me`가 401만 반환하기 때문입니다.
