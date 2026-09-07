# AGENTS.md — MSA Project Rules

> 이 문서는 마이크로서비스 아키텍처(MSA) 기반 프로젝트에서 AI 에이전트가 준수해야 할 개발 규칙과 컨벤션을 정의합니다.
> 범용적으로 유사한 MSA 프로젝트에도 적용할 수 있도록 설계되었습니다.
> `{prefix}`, `{service-name}`, `{domain}` 등은 프로젝트마다 치환하는 플레이스홀더입니다.

---

## 1. 아키텍처 원칙

### 1.1 마이크로서비스 경계

- **Database-per-Service**: 각 서비스는 자신만의 독립 DB를 소유한다. 타 서비스의 DB에 직접 접근(Cross-DB Query)을 **절대 금지**한다.
- **단일 진입점(API Gateway)**: 모든 클라이언트 요청은 API Gateway를 통해 라우팅된다. 프론트엔드가 백엔드 서비스를 직접 호출해서는 안 된다.
- **Service Discovery**: 백엔드 서비스는 Eureka 등 Discovery Server에 등록하고, Gateway는 서비스명으로 라우팅한다.
- **서비스 간 통신**:
  - **비동기(선호)**: Kafka 등 메시지 브로커를 통한 이벤트 기반 통신을 기본으로 한다.
  - **동기(예외적)**: 불가피한 경우에만 REST API를 사용하며, 반드시 Circuit Breaker + Retry를 적용한다.
- **Stateless**: 서버에 세션을 저장하지 않는다. JWT 기반 인가를 사용한다.

### 1.2 프로젝트 디렉토리 구조 컨벤션

```
project-root/
├── frontend/
│   └── {frontend-app}/              # 프론트엔드 앱 (예: nuxt-app)
├── backend/
│   ├── {prefix}-discovery-service/  # Service Discovery (Eureka 등). prefix는 생략 가능
│   ├── {prefix}-api-gateway/        # API 게이트웨이
│   └── {prefix}-{domain}-service/   # 도메인별 백엔드 서비스
├── docs/
│   ├── AGENTS.md                    # 이 문서
│   ├── architecture.md              # 아키텍처 헌법
│   ├── api-conventions.md           # API 규약
│   ├── tasks.md                     # 서비스별 작업 체크리스트 (진행 상태의 원천)
│   └── sdd-spec-docs/
│       └── feature/
│           └── {service-name}/      # 백엔드 서비스명 또는 프론트엔드 앱명
│               ├── openapi.yaml     # OpenAPI 스펙일때만 존재함. Swagger라면 X
│               └── {topic}-spec.md  # 계약/설계 스펙 문서 (프론트엔드 포함)
├── infra/                           # 인프라 초기화 스크립트 (DB init 등). 없을 수도 있음
│   └── mysql/init/
├── docker/                          # Dockerfile 등 (없을 수도 있음)
└── docker-compose-dev.yml           # 로컬 개발 인프라 정의
```

- 백엔드 서비스 폴더는 **반드시 `backend/` 하위**에 둔다. 루트에 서비스를 직접 두지 않는다.
- 서비스 폴더명의 `{prefix}-`는 프로젝트에 따라 생략할 수 있다 (예: `api-gateway`, `food-catalog-service`).
- Discovery Service와 API Gateway는 자체 도메인 DB를 두지 않는다.

---

## 2. 개발 워크플로우 (API 계약 방식)

API 변경의 **진실의 원천(Source of Truth)** 은 서비스가 채택한 계약 방식에 따라 다르다.
에이전트는 작업 전에 대상 서비스의 방식을 판별하고, **한 서비스에서 두 방식을 혼용하지 않는다.**

### 2.1 계약 방식 판별

아래 순서로 판별한다.

1. `docs/sdd-spec-docs/feature/{service}/openapi.yaml`이 존재하면 → **OpenAPI Spec (Spec-first)**
2. 해당 서비스 `build.gradle`에 `springdoc-openapi` / Swagger 의존성이 있으면 → **Swagger (Code-first)**
3. 둘 다 없으면 같은 저장소의 다른 도메인 서비스 관례를 따른다. 관례도 없으면 **Swagger**를 기본으로 한다.

### 2.2 방식 A — OpenAPI Spec (Spec-first)

별도 OAS 문서가 진실의 원천이다. 구현은 스펙을 엄격히 따른다.

**개발 흐름**

```
① OpenAPI Spec 작성/수정
   docs/sdd-spec-docs/feature/{service}/openapi.yaml
    ↓
② 비즈니스 코드 구현
    ↓
③ 테스트 코드 작성
   - Happy path (정상 흐름)
   - Boundary / Exception cases (경계값 · 예외)
   - 스펙 누락 발견 시 → 스펙 및 비즈니스 코드 보완
    ↓
④ 테스트가 회귀 방어막 역할 수행
```

**필수 산출물**

1. `docs/sdd-spec-docs/feature/{service}/openapi.yaml` 업데이트
2. 비즈니스 코드 (Controller → Service → Repository)
3. 단위 테스트 (커버리지 70% 이상 유지)

### 2.3 방식 B — Swagger (Code-first)

Controller / DTO의 springdoc(Swagger) 어노테이션이 진실의 원천이다.
`/v3/api-docs`, `/swagger-ui`로 스펙이 생성되므로 **별도 `openapi.yaml`을 작성·유지하지 않는다.**

**개발 흐름**

```
① Controller + Request/Response DTO 정의
   + @Tag / @Operation / @Schema 등 Swagger 어노테이션으로 계약 명시
    ↓
② 비즈니스 코드 구현 (Service → Repository)
    ↓
③ 테스트 코드 작성
   - Happy path (정상 흐름)
   - Boundary / Exception cases (경계값 · 예외)
   - 계약 누락 발견 시 → 어노테이션 및 비즈니스 코드 보완
    ↓
④ Swagger UI로 계약 검증 + 테스트가 회귀 방어막 역할 수행
```

**필수 산출물**

1. Swagger 어노테이션이 반영된 Controller / DTO (`openapi.yaml` 불필요)
2. 비즈니스 코드 (Controller → Service → Repository)
3. 단위 테스트 (커버리지 70% 이상 유지)

### 2.4 공통 원칙

- API 경로, 메서드, 요청/응답 스키마, 에러 코드는 선택한 계약 방식의 산출물에 먼저(또는 동시에) 반영한다.
- 테스트 중 계약 누락을 발견하면 계약(스펙 또는 어노테이션)과 비즈니스 코드를 함께 보완한다.

---

## 3. 백엔드 규칙 (Spring Boot / Java)

### 3.1 기술 스택

- **Java 21**, **Spring Boot 3.x**, **Gradle** (Groovy DSL)
- **Spring Cloud**: Gateway, Netflix Eureka (2024.x BOM)
- **ORM**: JPA (기본). 복잡한 R 쿼리가 필요하면 MyBatis, 동적 쿼리가 필요하면 QueryDSL을 추가한다.
- **MapStruct**: Entity ↔ DTO 변환
- **Lombok**: 보일러플레이트 제거에 **필수 사용**
- **Validation**: `spring-boot-starter-validation` 기반 Bean Validation
- **Redis**: 캐싱, Rate Limiting, Refresh Token 등
- **Kafka**: 서비스 간 비동기 이벤트
- **Swagger**: `springdoc-openapi` 사용 시 `/swagger-ui/**`, `/v3/api-docs/**` 제공

### 3.2 패키지 구조

각 도메인 서비스의 Java 소스는 아래를 기본으로 한다. 이벤트/동기 호출이 없는 서비스는 해당 패키지를 생략한다.

```
{base-package}/
├── {ServiceName}Application.java   # @SpringBootApplication
├── common/
│   └── ApiResponse.java            # 공통 응답 래퍼
├── config/                         # Security, Kafka, JPA, Redis 등 설정
├── controller/                     # REST 컨트롤러
├── domain/                         # 도메인 모델 (엔티티 등)
│   ├── entity/                     # JPA 엔티티 (BaseTimeEntity 상속)
│   └── event/                      # 도메인 이벤트 클래스 (필요 시)
├── dto/
│   ├── request/                    # 요청 DTO
│   └── response/                   # 응답 DTO
├── exception/
│   ├── BusinessException.java      # 비즈니스 예외
│   └── GlobalExceptionHandler.java # @RestControllerAdvice
├── mapper/                         # MapStruct 매퍼 (필요 시)
├── repository/                     # JPA Repository
├── service/                        # 비즈니스 로직
├── client/                         # 타 서비스 REST 호출 (Circuit Breaker 적용)
├── event/
│   ├── common/                     # 공통 이벤트 클래스 (EventEnvelope 등)
│   ├── outbox/                     # Transactional Outbox 패턴
│   ├── inbox/                      # Inbox 멱등성 보장
│   ├── consumer/                   # Kafka Consumer
│   ├── publisher/                  # Outbox Polling Publisher
│   └── payload/                    # 이벤트 페이로드 DTO
├── filter/                         # Gateway 인증 필터 등
├── listener/                       # Domain Event Listener
└── scheduler/                      # @Scheduled 작업
```

- Discovery Service, API Gateway는 도메인 패키지 전체를 강제하지 않는다. Gateway는 `config/`, `filter/` 중심으로 구성한다.

### 3.3 엔티티 작성 규칙

- **모든 엔티티는 `BaseTimeEntity`를 상속**한다 (`@CreatedDate`, `@LastModifiedDate` 자동 관리).
- **`@NoArgsConstructor(access = AccessLevel.PROTECTED)`** + **`@Builder`** + **`@AllArgsConstructor`** 조합을 사용한다.
- **`@Getter`만 사용**하고 `@Setter`는 사용하지 않는다. 상태 변경은 도메인 메서드를 통해서만 수행한다.
- 엔티티를 API 응답으로 직접 노출하지 않는다. 반드시 **DTO로 변환**한다.

### 3.4 DTO 규칙

- 요청 DTO: `dto/request/` 패키지에 위치. Bean Validation 어노테이션(`@NotBlank`, `@Min` 등) 적용.
- 응답 DTO: `dto/response/` 패키지에 위치. **Builder 패턴** 또는 **record** 사용을 권장.
- **DTO ↔ Entity 변환**은 MapStruct 매퍼 또는 Service 계층에 둔다. Controller에서 변환하지 않는다.

### 3.5 예외 처리 (Global Error Handling)

- 비즈니스 예외는 **`BusinessException(message, httpStatus)`** 을 throw한다. `message`에는 사용자 문장이 아니라 **에러 코드**를 넣는다.
- `@RestControllerAdvice`로 구현된 `GlobalExceptionHandler`가 `ApiResponse` 형식의 통합 에러 응답을 생성한다.
- 성공/실패 모두 `ApiResponse<T>` 래퍼를 사용한다.

성공 응답:

```json
{
  "status": 200,
  "message": "Success",
  "data": {}
}
```

에러 응답:

```json
{
  "status": 404,
  "message": "RESTAURANT_NOT_FOUND",
  "data": null
}
```

- **백엔드는 하드코딩된 한국어/일본어 에러 문장을 반환하지 않는다.** `message`는 `SCREAMING_SNAKE_CASE` 에러 코드이며, 프론트엔드가 i18n으로 표출한다.
- 에러 코드 예: `RESTAURANT_NOT_FOUND`, `VALIDATION_ERROR`, `UNAUTHORIZED`

### 3.6 Security 설정

- CSRF 비활성화, 세션 STATELESS 모드.
- Swagger/OpenAPI 엔드포인트(`/swagger-ui/**`, `/v3/api-docs/**`), Health 체크(`/actuator/health`, `/actuator/info`)는 `permitAll()`.
- 공개 API(조회성 카탈로그, 로그인/회원가입 등)만 `permitAll()`, 그 외는 `authenticated()`.
- API Gateway가 검증 후 전달하는 `X-User-Id`, `X-User-Role` 헤더를 하위 서비스가 신뢰한다. 서비스 측 필터는 Gateway 뒤에서만 동작한다고 가정한다.

### 3.7 RESTful API 규칙

- **REST 성숙도 Level 2/3** 준수.
- URL: `/api/v{version}/{resource}` 형식. 소문자 **kebab-case**, **복수형 명사**.
  - 예: `/api/v1/restaurants`, `/api/v1/orders`
- HTTP 메서드를 정확히 사용 (GET=조회, POST=생성, PUT=전체 수정, PATCH=부분 수정, DELETE=삭제).
- 응답 코드를 의미에 맞게 사용 (200, 201, 204, 400, 401, 403, 404, 409, 500 등).
- 클라이언트는 `Accept-Language` 헤더로 locale을 전달한다 (`ko` / `ja`). 누락 시 기본값 `ko`.
- 상세 규약은 `docs/api-conventions.md`를 따른다.

### 3.8 설정 파일

- `application.properties`에서 `spring.profiles.active=dev`로 프로파일 분리.
- Compose 네트워크용 Redis/Eureka/Kafka 호스트명은 `application-dev.properties`에 둔다. DB URL은 호스트 PC MySQL의 실제 주소(원격 IP)를 그대로 쓴다. 이 파일은 계정 정보가 들어가므로 gitignore 한다.
- `application-prod.properties` / `application-local.properties` / `application-secret.properties`는 gitignore. 운영 비밀값은 환경변수로 주입한다.

---

## 4. 이벤트 기반 아키텍처 규칙 (Kafka)

### 4.1 Transactional Outbox 패턴

- **Dual-Write 문제 방지**: DB 저장과 Kafka 발행을 동시에 하지 않는다. `outbox_event` 테이블에 같은 트랜잭션으로 INSERT한 뒤, Polling Publisher가 Kafka로 발행한다.
- Outbox 엔티티: `eventId`(UUID), `aggregateType`, `aggregateId`, `topic`, `eventType`, `payload`(JSON), `status`, `retryCount`, `nextRetryAt` 필드를 포함한다.
- 실패 시 **Exponential Backoff** 재시도 적용 (base=1초, cap=300초, 최대 30회 후 FAILED 격리).

### 4.2 Inbox 멱등성 패턴

- Consumer 측은 `inbox_event` 테이블의 `(eventId, consumerGroup)` unique 제약으로 중복 소비를 차단한다.
- **Effectively exactly-once** 처리를 보장한다.

### 4.3 Saga 패턴 (Choreography)

- 분산 트랜잭션의 데이터 일관성은 Choreography 방식의 Saga로 보장한다.
- 각 서비스는 자신의 작업 완료/실패 이벤트를 발행하고, 관련 서비스가 이를 소비하여 보상 트랜잭션을 실행한다.
- 이벤트 명명: `{DOMAIN}_{ACTION}` 형식 (예: `ORDER_CREATED`, `STOCK_RESERVED`, `PAYMENT_COMPLETED`, `PAYMENT_FAILED`)

### 4.4 Kafka 설정 규칙

- DLT(Dead Letter Topic) 활용: 처리 불가 메시지는 DLT로 격리.
- 역직렬화 실패 시 해당 레코드를 건너뛰는 에러 핸들러 구현.
- `AckMode.RECORD` 사용 (레코드 단위 커밋).
- Consumer Group 네이밍: `{service-name}-group`

### 4.5 Circuit Breaker & Retry (Resilience4j)

- 동기 서비스 간 REST 호출에 `@CircuitBreaker`와 `@Retry`를 적용한다.
- **적용 순서**: `@Retry`가 먼저 실행되고, 재시도 소진 후 `@CircuitBreaker`가 카운트한다.
- Fallback 메서드에서는 명확한 에러 코드와 함께 `BusinessException`을 throw한다.
- 타 서비스 호출 클래스는 `client/` 패키지에 위치하며, `RestClient`를 사용한다.

---

## 5. 프론트엔드 규칙 (Nuxt 4 / Vue 3)

### 5.1 기술 스택

- **Nuxt 4** (Vue 3, TypeScript, Nitro engine)
- **Tailwind CSS** (유틸리티 클래스 기반 스타일링 필수, scoped `<style>` 블록 금지)
- **Pinia** (상태 관리)
- **Nuxt 내장 `$fetch` (ofetch)** (API 통신 — 별도 HTTP 클라이언트 라이브러리를 추가하지 않는다. axios 사용 금지.)
- **@nuxtjs/i18n** (정적 UI · 에러 메시지 다국어)

### 5.2 디렉토리 구조

```
frontend/{frontend-app}/
├── app/
│   ├── app.vue                     # 루트 컴포넌트
│   ├── assets/css/                 # Tailwind 진입점 CSS
│   ├── components/
│   │   ├── common/                 # 공통 UI 컴포넌트
│   │   └── {feature}/              # 기능별 컴포넌트 그룹
│   ├── composables/
│   │   ├── useApiFetch.ts          # useAsyncData + API Client 결합 (SSR)
│   │   └── use{Feature}.ts         # 기능별 Composable (API 호출 + 상태)
│   ├── layouts/
│   │   ├── default.vue
│   │   └── admin.vue               # 필요 시
│   ├── middleware/
│   ├── pages/                      # 파일 기반 라우팅
│   ├── plugins/
│   │   └── api.ts                  # API Client를 $api로 등록
│   ├── stores/
│   │   └── {feature}.ts            # Pinia 스토어
│   ├── types/
│   │   └── api.ts                  # ApiResponse / ApiError / ApiClient 타입
│   └── utils/
│       └── api-client.ts           # $fetch 래핑 + 토큰 갱신 큐
├── i18n/
│   ├── i18n.config.ts
│   └── locales/
│       ├── ko.json
│       └── ja.json
├── nuxt.config.ts
└── package.json
```

### 5.3 컴포넌트 작성 규칙

- 반드시 **`<script setup lang="ts">`** 를 사용한다.
- Options API, `<script>` (non-setup) 사용을 금지한다.
- Props / Emits에 TypeScript 타입을 명시한다.
- 사용자에게 보이는 문자열은 하드코딩하지 않고 `$t('...')` / `t('...')`를 사용한다.

### 5.4 API 통신 규칙

- **HTTP 호출은 `$fetch`를 래핑한 자체 API Client 하나로만 한다.** `axios` 등 외부 HTTP 클라이언트를 추가하지 않으며, 컴포넌트에서 전역 `fetch` / `$fetch`를 직접 호출하지 않는다.
- API Client는 `app/utils/api-client.ts`에 구현하고, `app/plugins/api.ts`가 이를 `$api`로 등록한다. 접근은 `useNuxtApp().$api`.
- 요청 시 Access Token(`Authorization: Bearer {token}`)과 현재 locale(`Accept-Language`)을 자동 주입한다.
- 401 응답 시 **토큰 갱신 큐(Token Refresh Queue)** 를 적용한다.
  - 갱신은 single-flight로 **동시에 한 번만** 수행하고, 대기하던 요청은 갱신 완료 후 일괄 재시도한다.
  - 재시도는 요청당 1회로 제한한다. 재시도가 다시 401이면 그대로 실패시킨다.
  - `/auth/login`, `/auth/signup`, `/auth/refresh-token`의 401은 갱신 대상에서 제외한다.
  - 갱신 실패 시 `clearAuth()` 후 로그인 페이지로 리디렉트한다.
- **토큰 저장 위치**: Access Token은 Pinia 메모리에만 둔다(localStorage 금지). Refresh Token은 백엔드가 httpOnly 쿠키로 관리하며, 요청은 `credentials: 'include'`로 보낸다.
- SSR 중에는 토큰 주입과 토큰 갱신을 하지 않는다. 인증이 필요한 데이터는 클라이언트에서 조회한다.
- **인증 라우트 미들웨어**는 `import.meta.server`에서 즉시 반환하고, 판정 전에 `authStore.restoreSession()`을 `await`한다. 이를 지키지 않으면 로그인 사용자가 새로고침만으로 로그인 페이지로 튕긴다.
- 전역 세션 복구(플러그인/`app.vue`)는 `await`하지 않는다. 대기는 인증이 필요한 라우트에서만 발생해야 한다.
- 헤더 등 **개인화 UI는 `isLoggedIn` 단독으로 분기하지 않는다.** `isRestoring`(판정 중) → `isLoggedIn`(로그인) → 그 외(비로그인) 3단계로 렌더링해 복구 중 깜빡임을 막는다.
- 백엔드 `ApiResponse<T>` 래퍼는 API Client가 자동으로 벗긴다. 호출부는 `T`만 다룬다.
- 모든 실패는 `ApiError`(`status`, `code`, `data`)로 정규화되어 throw된다. 에러 코드는 `error.{CODE}` i18n 키로 매핑한다.
- API Base URL은 `runtimeConfig.public.apiBaseUrl`로 관리하며, Gateway의 `/api/v1`을 가리킨다.
- SSR 프리페치가 필요하면 `useApiFetch`(내부적으로 `useAsyncData` + API Client)를 사용한다.
- 상세 계약은 `docs/sdd-spec-docs/feature/nuxt-app/api-client-spec.md`를 따른다.

### 5.5 상태 관리 (Pinia)

- Composition API 스타일의 `defineStore`를 사용한다.
- Store 파일 명명: `{feature}.ts` (예: `auth.ts`, `cart.ts`)
- 글로벌 상태가 필요하지 않은 경우 Composable로 대체한다.

### 5.6 Composable 작성 규칙

- 파일 명명: `use{Feature}.ts` (예: `useCatalog.ts`, `useMyOrders.ts`)
- 내부에서 `useState`로 SSR-safe한 반응형 상태를 관리한다.
- API 호출 + 상태 관리 + 에러 처리를 캡슐화한다.
- 로딩 상태(`isLoading`)와 에러 메시지(`errorMessage`)를 항상 포함한다.

### 5.7 TypeScript 타입 관리

- API 요청/응답 타입은 `types/{feature}.ts` 또는 `types/index.ts`에 정의한다. API Client 자체의 타입(`ApiResponse`, `ApiError`, `ApiClient`)은 `types/api.ts`에 둔다.
- 타입은 `interface` 또는 `type`으로 작성하고, 모든 API 호출 시 제네릭으로 적용한다.
- 제네릭에는 **언랩된 타입**을 넣는다. `ApiResponse<T>`가 아니라 `T`다.

```typescript
const { $api } = useNuxtApp()

const restaurant = await $api.get<Restaurant>(`/restaurants/${id}`)

const { data: restaurants } = await useApiFetch<Restaurant[]>('/restaurants')
```

- `$api`의 `baseURL`이 이미 `/api/v1`을 포함하므로 경로에 `/api/v1`을 중복하지 않는다.
- 에러는 `try / catch`로 받고 `ApiError`로 좁혀서 처리한다.

```typescript
try {
  await $api.post<Order>('/orders', payload)
} catch (e) {
  const err = e as ApiError
  errorMessage.value = t(err.i18nKey)
}
```

### 5.8 i18n 규칙

- 지원 locale: `ko`(기본), `ja`. URL prefix 없이 cookie로 유지한다 (`strategy: 'no_prefix'`).
- 정적 UI, 버튼, 네비게이션, 에러 메시지는 프론트 i18n JSON으로만 관리한다.
- 음식점명·메뉴명 등 동적 도메인 데이터는 백엔드 Translation 테이블 / Translation Service를 사용한다. 프론트 JSON에 넣지 않는다.
- 상세 정책은 `docs/internationalization.md`, `docs/translation-system.md`를 따른다.

---

## 6. 인프라 & Docker 규칙

### 6.1 Docker Compose 구조

- `docker-compose-dev.yml`: 로컬 개발 환경을 정의한다. Redis, Kafka, Zookeeper와 프론트/백엔드 서비스를 포함한다. **MySQL 컨테이너는 두지 않고** 호스트 PC MySQL에 원격 접속한다.
- 개발 단계에서 각 Spring Boot 서비스와 Nuxt 앱은 IDE에서 직접 실행할 수도 있다. 그때는 인프라 서비스만 compose로 올린다.
- 백엔드 이미지는 `docker/backend/Dockerfile`, Nuxt 개발 이미지는 `docker/frontend/Dockerfile.dev`를 사용한다.
- 컨테이너 이름: `{project-prefix}-{service-name}` (예: `rtc-mysql`, `{prefix}-order-service`).

### 6.2 네트워크 & 포트 컨벤션

- 모든 컨테이너는 공통 Docker 네트워크(`{prefix}-network` 또는 `{project}-network`, bridge)에 연결한다.
- 포트 할당 예시:

| 서비스                | 호스트 포트 |
| --------------------- | ----------- |
| Frontend              | 3000        |
| API Gateway           | 8080        |
| Backend Service 1     | 8081        |
| Backend Service 2     | 8082        |
| Backend Service N     | 808N        |
| Discovery (Eureka)    | 8761        |
| MySQL                 | 3306        |
| Redis                 | 6379        |
| Kafka                 | 9092        |
| Kafka UI (개발, 선택) | 8090        |

### 6.3 설정 파일과 Docker 프로파일

- 공통 기본값(`localhost`)은 `application.properties`에 둔다. IDE에서 직접 실행할 때 이 값을 쓴다.
- Compose로 띄울 때는 `SPRING_PROFILES_ACTIVE=dev` 만 지정한다. Docker 네트워크용 Redis/Eureka/Kafka 호스트명은 `application-dev.properties`가 덮어쓴다. DB는 호스트 PC의 실제 JDBC URL을 그대로 사용하며 `host.docker.internal`은 필요 없다.

---

## 7. 테스트 규칙

### 7.1 기본 원칙

- **단위 테스트 커버리지 70% 이상** 유지 필수.
- JUnit 5 + Mockito + Spring Boot Test 사용.
- 테스트 DB: H2 (인메모리) 또는 테스트 프로파일의 격리된 DB.

### 7.2 테스트 구조

```
src/test/java/{base-package}/
├── controller/     # MockMvc 기반 컨트롤러 슬라이스 테스트
├── service/        # 서비스 계층 단위 테스트 (Mocking)
├── domain/         # 엔티티 도메인 로직 단위 테스트
└── event/          # Kafka 이벤트 발행/소비 테스트 (해당 시)
```

### 7.3 테스트 작성 지침

- 각 테스트 메서드명은 `{메서드명}_{시나리오}_{기대결과}` 형식을 따른다.
- Happy path + Edge case + Error case를 모두 커버한다.
- 테스트 중 계약 누락을 발견하면 → **2절의 해당 방식**(OpenAPI Spec 또는 Swagger 어노테이션)과 비즈니스 코드를 함께 보완한다.

---

## 8. 코드 스타일 & 컨벤션

### 8.1 공통

- **사용자 대면 메시지**: 백엔드는 에러 코드만 반환하고, 프론트 i18n이 언어별 문장을 담당한다.
- **로그**: 개발자용 로그는 한국어로 작성할 수 있다.
- **영어 코드**: 클래스명, 메서드명, 변수명, 패키지명은 모두 영어로 작성한다.
- **문서**: 아키텍처/규약 문서는 한국어를 기본으로 한다. 필요 시 다국어 버전을 유지할 수 있다.

### 8.2 Java

- **Lombok 어노테이션 순서**: `@Entity` → `@Table` → `@Getter` → `@NoArgsConstructor` → `@Builder` → `@AllArgsConstructor`
- **생성자 주입**: `@RequiredArgsConstructor` + `private final` 필드 (필드 주입 금지).
- **로깅**: `@Slf4j` (Lombok) 사용. `System.out.println()` 금지.
- **Java 21 기능 활용**: Switch Expression, Record, Pattern Matching 등 적극 활용.

### 8.3 TypeScript / Vue

- **세미콜론**: 생략 (Prettier 기본 설정 따름).
- **따옴표**: 싱글 쿼트 사용 (`'`).
- **들여쓰기**: 2 spaces.
- **import 순서**: 외부 라이브러리 → Nuxt/Vue 내장 → 프로젝트 내부 (`~/` prefix).

---

## 9. Git & 협업 규칙

### 9.1 커밋 메시지

- Conventional Commits 형식을 따른다:

```
feat(order): 주문 생성 API 구현
fix(catalog): 재고 차감 동시성 이슈 해결
docs(sdd): 결제 API 스펙 추가
refactor(auth): JWT 토큰 갱신 로직 개선
test(order): 주문 상태 전이 단위 테스트 추가
```

### 9.2 .gitignore

- 빌드 산출물, IDE 설정, 로컬 환경파일, `node_modules`, `.gradle`, `.nuxt` 등은 반드시 무시한다.
- 민감 정보 포함 파일(`.env`, `application-dev.properties`, `application-prod.properties`, `application-secret.properties` 등)은 절대 커밋하지 않는다.

---

## 10. AI 에이전트 행동 규칙

### 10.1 코드 변경 시

- 기존 코드의 **주석과 Javadoc을 보존**한다 (변경 대상이 아닌 한).
- 새 코드 작성 시 해당 서비스의 **기존 패턴과 컨벤션을 따른다** (패키지 구조, 네이밍, 에러 처리 등).
- 한 서비스의 변경이 다른 서비스에 영향을 줄 경우, **영향받는 모든 서비스를 함께 수정**한다.
- 애플리케이션 구조·규약이 바뀌는 경우(JWT/캐시 TTL, 타 서비스 의존, API 계약 등)에는 아래를 반드시 갱신한다.
  - **OpenAPI Spec 방식**: `docs/sdd-spec-docs/` 스펙 문서
  - **Swagger 방식**: Controller/DTO의 Swagger 어노테이션
  - 공통: 해당 시 `docs/architecture.md`, `docs/api-conventions.md`

### 10.2 API 변경 시

작업 대상 서비스의 계약 방식(2.1)을 먼저 판별한 뒤, 해당 분기만 따른다.

**OpenAPI Spec 방식**

- `docs/sdd-spec-docs/feature/{service}/openapi.yaml`을 **먼저 업데이트**한다.
- 스펙 변경 후 비즈니스 코드 → 테스트 코드 순서로 작업한다.

**Swagger 방식**

- 별도 `openapi.yaml`은 작성하지 않는다.
- Controller / DTO와 `@Operation`, `@Schema` 등 어노테이션으로 계약을 **코드와 함께** 반영한다.
- 그다음 비즈니스 로직 → 테스트 코드 순서로 작업한다.
- 작업 후 `/swagger-ui` 또는 `/v3/api-docs` 기준으로 계약이 맞는지 확인한다.

### 10.3 새 서비스 추가 시

- 폴더는 `backend/{prefix}-{domain}-service/` (또는 `backend/{domain}-service/`)에 생성한다.
- 위 섹션 3.2의 패키지 구조를 따른다.
- `ApiResponse` + `BusinessException` + `GlobalExceptionHandler`를 반드시 포함한다.
- `BaseTimeEntity`를 도메인 엔티티의 기본 상속 클래스로 사용한다.
- `SecurityConfig`에 Stateless 패턴을 적용한다. Gateway 하위 서비스는 Gateway가 넣은 사용자 헤더를 검증한다.
- Eureka Client로 Discovery에 등록하고, API Gateway 라우팅을 업데이트한다.
- 자체 DB가 필요하면 Database-per-Service를 지키고, `infra/mysql/init/`에 스키마/유저 초기화를 추가한다.
- 서비스까지 compose로 띄우는 단계라면 `docker-compose-dev.yml`(또는 prod compose)에도 추가한다.

### 10.4 참고 문서 우선순위

1. `docs/architecture.md` — 아키텍처 헌법 (최우선)
2. `docs/api-conventions.md` — API 응답/에러/URI 규약
3. API 계약
   - OpenAPI Spec 방식: `docs/sdd-spec-docs/feature/{service}/openapi.yaml`
   - Swagger 방식: 해당 서비스 Controller 어노테이션 및 `/v3/api-docs`
   - 프론트엔드: `docs/sdd-spec-docs/feature/nuxt-app/api-client-spec.md`
4. `docs/internationalization.md`, `docs/translation-system.md` — 다국어
5. 각 서비스의 기존 코드 패턴 — 컨벤션 참고
6. 이 `AGENTS.md` — 범용 규칙

### 10.5 작업 목록 관리 (`docs/tasks.md`)

`docs/tasks.md`는 서비스별 기능 단위 체크리스트이며, **작업 진행 상태의 진실의 원천**이다.

- 작업을 시작하기 전에 `docs/tasks.md`에서 해당 태스크와 연결된 스펙 문서를 먼저 읽는다.
- 태스크에 `_(스펙 미작성)_`이 붙어 있으면 **구현보다 스펙 작성이 선행**한다.
- 작업을 완료하면 해당 체크박스를 `[x]`로 갱신한다. 진행 중이면 🟡, 막혔으면 🔴와 사유를 표기한다.
- 새 기능·새 스펙 문서를 추가하면 `docs/tasks.md`의 태스크 목록과 하단 "스펙 문서 현황" 표에 함께 반영한다.
- `docs/development-roadmap.md`는 Phase 단위의 순서·의존 관계만 다룬다. 세부 진행 상태는 `tasks.md`를 따르며, Phase 전체가 끝났을 때 로드맵을 갱신한다.
