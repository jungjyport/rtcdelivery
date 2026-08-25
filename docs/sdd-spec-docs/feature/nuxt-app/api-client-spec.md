# RTC Delivery — Frontend API Client Spec (`$fetch` 기반)

> **상태**: Implemented
> **대상**: `frontend/nuxt-app`
> **관련 문서**: [architecture.md](../../../architecture.md), [api-conventions.md](../../../api-conventions.md), [AGENTS.md](../../../AGENTS.md)

---

## 1. 배경 및 목적

현재 프론트엔드는 `axios` 인스턴스를 Nuxt 플러그인으로 등록해 API를 호출하고 있다.
Nuxt 4는 `ofetch` 기반의 `$fetch`를 내장하고 있으며, `useFetch` / `useAsyncData`와의 SSR 통합(페이로드 직렬화, 중복 요청 제거)이 `$fetch`를 전제로 설계되어 있다.
axios를 그대로 두면 아래 문제가 남는다.

- 번들에 불필요한 HTTP 클라이언트가 중복 포함된다 (`$fetch`는 Nuxt 런타임에 이미 포함).
- SSR 컴포넌트에서 `useAsyncData`로 axios를 감싸야 하는 우회 패턴이 필요하다.
- Nitro 서버 라우트에서 동일한 클라이언트를 재사용할 수 없다.

따라서 **axios를 완전히 제거하고, `$fetch`를 래핑한 자체 API Client를 도입한다.**
이 문서는 그 API Client의 계약(contract)을 정의한다. 구현 코드는 이 스펙 승인 이후에 작성한다.

### 1.1 목표

1. `$fetch` 기반의 단일 API Client를 제공하고 앱 전역에서 이것만 사용한다.
2. Access Token 자동 주입, `Accept-Language` 자동 주입을 클라이언트가 책임진다.
3. 401 응답 시 **Token Refresh Queue(단일 비행, single-flight)** 로 토큰을 1회만 갱신하고, 대기 중이던 요청을 일괄 재시도한다.
4. 백엔드 `ApiResponse<T>` 래퍼를 자동으로 벗겨 호출부가 `T`만 다루게 한다.
5. 모든 실패를 `ApiError` 한 가지 타입으로 정규화해 i18n 에러 표출을 단순화한다.
6. SSR(`useAsyncData` / `useFetch`)과 자연스럽게 결합한다.

### 1.2 비목표 (Out of Scope)

- SSR 중 인증이 필요한 요청 처리 (→ 6.2 절, 향후 확장)
- 요청 캐싱/중복 제거 레이어 (`useAsyncData`의 key 기능으로 충분)
- 파일 업로드 진행률(progress) 추적 (Fetch API 한계. 필요 시 별도 스펙)
- 요청 취소(AbortController)의 앱 레벨 추상화 (ofetch `signal` 옵션을 그대로 노출)

### 1.3 용어

| 용어 | 의미 |
|---|---|
| Access Token | 짧은 수명의 JWT. **Pinia 메모리에만 저장**. `Authorization: Bearer` 헤더로 전송 |
| Refresh Token | 긴 수명의 토큰. **httpOnly 쿠키**로 저장. JS에서 읽지 않음 |
| Refresh Queue | 토큰 갱신 중 도착한 401 요청들을 대기시켰다가 갱신 완료 후 일괄 재시도하는 패턴 |
| Single-flight | 동시 다발 갱신 요청을 하나의 Promise로 합치는 기법 |
| `ApiResponse<T>` | 백엔드 공통 응답 래퍼 `{ status, message, data }` |

---

## 2. 아키텍처 개요

```
┌──────────────────────────────────────────────────────────┐
│ Component / Page / Composable                            │
│   const restaurant = await $api.get<Restaurant>('/restaurants/1')
│   const { data } = await useApiFetch<Restaurant[]>('/restaurants')
└───────────────┬──────────────────────────────────────────┘
                │
        ┌───────▼────────┐
        │   ApiClient    │  utils/api-client.ts
        │  (create...)   │
        └───────┬────────┘
                │
   ┌────────────┼──────────────────────────┐
   │            │                          │
┌──▼────────┐ ┌─▼──────────────┐ ┌─────────▼──────────┐
│ onRequest │ │ Response Unwrap│ │ 401 Refresh Queue  │
│  - Bearer │ │  ApiResponse<T>│ │  - single-flight   │
│  - Locale │ │      → T       │ │  - 대기/일괄 재시도 │
└──┬────────┘ └─┬──────────────┘ └─────────┬──────────┘
   │            │                          │
   └────────────┼──────────────────────────┘
                │
        ┌───────▼────────┐
        │  $fetch.create │  (ofetch)
        └───────┬────────┘
                │ HTTP + httpOnly cookie
        ┌───────▼────────┐
        │  API Gateway   │  :8080 /api/v1
        └────────────────┘
```

### 2.1 왜 인터셉터만으로는 안 되는가

ofetch의 `onResponseError` 훅은 **반환값이 무시되며 요청을 재시도할 수 없다.**
axios의 응답 인터셉터가 `return instance(originalRequest)`로 재시도를 이어붙일 수 있었던 것과 다르다.

따라서 재시도 로직은 인터셉터가 아니라 **`$fetch`를 감싸는 래퍼 함수 안에서 `try / catch`로 구현한다.**
인터셉터(`onRequest`, `onResponse`)는 헤더 주입과 응답 정규화 같은 부수효과 없는 작업에만 사용한다.

---

## 3. 산출물 (파일 구조)

```
frontend/nuxt-app/app/
├── types/
│   └── api.ts               # [NEW] ApiResponse, ApiError, ApiClient, ApiRequestOptions
├── utils/
│   └── api-client.ts        # [NEW] createApiClient() — fetch 래핑 + refresh queue
├── plugins/
│   ├── api.ts               # [MODIFY] axios 제거 → createApiClient() 등록 ($api)
│   └── auth-restore.client.ts # [NEW] 전역 세션 복구 (await 하지 않음, 6.6)
├── middleware/
│   └── auth.ts              # [NEW] 인증 라우트 가드 (6.2.1)
├── composables/
│   └── useApiFetch.ts       # [NEW] useAsyncData + $api 결합 (SSR용)
└── stores/
    └── auth.ts              # [NEW] Pinia auth 스토어 (auth-example.ts 기반)
```

**삭제 대상**

| 파일 | 처리 |
|---|---|
| `app/plugins/01.axios-example.ts` | 삭제 (내용은 이 스펙으로 이관 완료) |
| `app/stores/auth-example.ts` | `app/stores/auth.ts`로 승격 후 삭제 |
| `package.json`의 `axios` 의존성 | 제거 |

> `utils/api-client.ts`는 Nuxt/Pinia에 직접 의존하지 않는 **팩토리 함수**로 작성한다.
> 토큰 읽기/쓰기, 로그아웃, 리다이렉트는 모두 주입받는다. 이렇게 하면 Nuxt 런타임 없이 단위 테스트가 가능하다.

---

## 4. 타입 계약

### 4.1 `ApiResponse<T>` (기존 유지)

```typescript
export interface ApiResponse<T> {
  status: number
  message: string   // 성공 시 'Success', 실패 시 SCREAMING_SNAKE_CASE 에러 코드
  data: T
}
```

### 4.2 `ApiError`

모든 실패는 이 타입으로 정규화된다. HTTP 에러, 네트워크 단절, 타임아웃, JSON 파싱 실패를 구분 없이 감싼다.

```typescript
export class ApiError extends Error {
  /** HTTP 상태 코드. 네트워크 오류 등 응답이 없으면 0 */
  readonly status: number
  /** 백엔드 에러 코드(ApiResponse.message). 응답이 없으면 클라이언트 자체 코드 */
  readonly code: string
  /** 백엔드 응답 원본(ApiResponse.data). 검증 오류 상세 등 */
  readonly data: unknown
  readonly url: string
  readonly method: string

  /** i18n 조회용 키. 예) 'error.RESTAURANT_NOT_FOUND' */
  get i18nKey(): string   // `error.${this.code}`
}
```

**클라이언트 자체 에러 코드**

| `code` | `status` | 발생 조건 |
|---|---|---|
| `NETWORK_ERROR` | 0 | fetch 자체가 실패 (오프라인, DNS, CORS 차단 등) |
| `REQUEST_TIMEOUT` | 0 | 타임아웃 초과 |
| `INVALID_RESPONSE` | 실제 상태 | 응답 본문이 `ApiResponse` 형태가 아님 |
| `UNKNOWN_ERROR` | 실제 상태 | 위 어디에도 해당하지 않음 |

> 이 코드들은 `i18n/locales/{ko,ja}.json`의 `error.*` 하위에 **반드시 번역이 존재해야 한다.**
> 번역 키가 없으면 `error.UNKNOWN_ERROR`로 fallback 한다.

### 4.3 `ApiRequestOptions`

ofetch 옵션을 확장한다.

```typescript
export interface ApiRequestOptions extends Omit<FetchOptions, 'method' | 'baseURL'> {
  /** true면 Authorization 헤더를 주입하지 않는다. 기본값 false */
  skipAuth?: boolean
  /** true면 401이어도 토큰 갱신/재시도를 하지 않는다. 기본값 false */
  skipRefresh?: boolean
}
```

### 4.4 `ApiClient`

```typescript
export interface ApiClient {
  get<T>(url: string, options?: ApiRequestOptions): Promise<T>
  post<T>(url: string, body?: unknown, options?: ApiRequestOptions): Promise<T>
  put<T>(url: string, body?: unknown, options?: ApiRequestOptions): Promise<T>
  patch<T>(url: string, body?: unknown, options?: ApiRequestOptions): Promise<T>
  delete<T>(url: string, options?: ApiRequestOptions): Promise<T>

  /** ApiResponse 래퍼 전체가 필요할 때 (page 메타 등) */
  raw<T>(url: string, options?: ApiRequestOptions & { method?: string }): Promise<ApiResponse<T>>

  /** useAsyncData / useFetch에 넘길 raw ofetch 인스턴스 */
  readonly fetcher: $Fetch
}
```

**제네릭은 언랩된 타입을 받는다.** `ApiResponse<T>`가 아니라 `T`다.

```typescript
// ✅ 올바름
const restaurant = await $api.get<Restaurant>(`/restaurants/${id}`)
console.log(restaurant.name)

// ❌ 틀림 (언랩 후에는 래퍼가 없다)
const res = await $api.get<ApiResponse<Restaurant>>(`/restaurants/${id}`)
```

---

## 5. 동작 스펙

### 5.1 인스턴스 생성

| 항목 | 값 |
|---|---|
| `baseURL` | `runtimeConfig.public.apiBaseUrl` (기본 `http://localhost:8080/api/v1`) |
| `credentials` | `'include'` — Refresh Token httpOnly 쿠키 전송을 위해 필수 |
| `timeout` | 10000 (ms) |
| `retry` | `false` — ofetch 내장 재시도를 끈다. 재시도는 이 스펙이 단독으로 관리한다 |
| `headers` | `Content-Type: application/json` (body가 `FormData`면 미설정) |

> `baseURL`이 이미 `/api/v1`을 포함하므로, 호출 경로에 `/api/v1`을 다시 쓰지 않는다.
> 예: `$api.get('/restaurants')` → `http://localhost:8080/api/v1/restaurants`

### 5.2 요청 파이프라인 (`onRequest`)

순서대로 수행한다.

1. **Authorization 주입** — `skipAuth`가 아니고 클라이언트 사이드이며 `authStore.accessToken`이 있으면
   `Authorization: Bearer {accessToken}`을 설정한다.
2. **Accept-Language 주입** — 현재 i18n locale(`ko` | `ja`)을 `Accept-Language` 헤더로 설정한다.
   호출부가 명시적으로 지정한 값이 있으면 덮어쓰지 않는다.
3. 서버 사이드(`import.meta.server`)에서는 1번을 건너뛴다 (→ 6.1 절).

### 5.3 응답 파이프라인 (언랩)

1. HTTP 2xx 응답의 본문을 `ApiResponse<T>`로 간주하고 `data` 필드를 반환한다.
2. 본문이 `{ status, message, data }` 형태가 아니면 `ApiError(code: 'INVALID_RESPONSE')`를 throw 한다.
3. `204 No Content`는 `undefined`를 반환한다.
4. `raw()`로 호출한 경우에만 `ApiResponse<T>` 전체를 반환한다.

**예외**: 백엔드가 `ApiResponse`로 감싸지 않는 엔드포인트(예: `/actuator/health`)를 호출해야 하면
`$api.fetcher`를 직접 사용한다. `$api.get()`은 항상 래퍼를 가정한다.

### 5.4 에러 파이프라인

1. HTTP 4xx/5xx → 응답 본문에서 `message`를 `code`로, `data`를 `data`로 채워 `ApiError`를 throw.
2. 본문 파싱 실패 → `code: 'UNKNOWN_ERROR'`, `status`는 실제 HTTP 상태.
3. 네트워크/타임아웃 → `code: 'NETWORK_ERROR'` / `'REQUEST_TIMEOUT'`, `status: 0`.
4. `ApiError`는 **삼키지 않고 항상 호출부로 전파**한다. 401 갱신 흐름만 예외적으로 내부 처리한다.

---

## 6. JWT Refresh Queue 스펙

### 6.1 전제

| 항목 | 정책 |
|---|---|
| Access Token 저장 | Pinia `useAuthStore().accessToken` (**메모리만**, localStorage 사용 금지) |
| Refresh Token 저장 | httpOnly + Secure 쿠키. JS에서 접근 불가 |
| 갱신 엔드포인트 | `POST /auth/refresh-token` (body 없음, 쿠키로 인증) |
| 갱신 응답 | `ApiResponse<{ accessToken: string }>` |
| SSR | 서버 렌더링 중에는 토큰 주입도 갱신도 하지 않는다 |

> **localStorage 사용 금지 근거**: 현행 `plugins/api.ts`는 `localStorage.getItem('accessToken')`을 쓰고 있으나,
> XSS 시 토큰이 그대로 탈취된다. 메모리 저장 + httpOnly refresh 쿠키 조합으로 전환한다.
> 새로고침 시 Access Token은 사라지며, 앱 초기화 시 refresh 1회로 복구한다 (→ 6.6).

### 6.2 SSR 정책 (결정 사항)

**인증이 필요한 요청은 클라이언트 사이드에서만 수행한다.**

근거: Access Token은 메모리에만 존재하고 Pinia 스토어는 SSR 요청마다 새로 생성되므로, 서버 렌더링 시점에는 항상 비어 있다.
기존 axios 플러그인도 `if (import.meta.server) return config`로 동일하게 동작하고 있었으므로 **동작상의 변화는 없다.**

| 데이터 종류 | 처리 |
|---|---|
| 공개 데이터 (음식점 목록/상세, 카테고리) | `useApiFetch`로 SSR 프리페치 |
| 인증 필요 데이터 (내 주문, 장바구니, 프로필) | `onMounted` 또는 `useApiFetch(..., { server: false })`로 클라이언트에서 호출 |

**향후 확장 (이번 범위 아님)**: 요청 쿠키를 Nitro 서버 라우트로 포워딩해 SSR 중 Access Token을 재발급받는 방식.
도입 시 이 스펙의 6.2를 개정한다.

#### 6.2.1 인증 라우트 미들웨어 규칙

라우트 미들웨어는 **서버 렌더링 시 한 번, 하이드레이션 시 클라이언트에서 한 번, 총 두 번 실행된다.**
그런데 Access Token은 메모리에만 있어서 두 실행 모두 스토어가 비어 있을 수 있다.
아래 미들웨어는 **refresh 쿠키가 살아 있는 로그인 사용자를 새로고침만으로 로그인 페이지로 튕겨낸다.**

```typescript
// ❌ 금지 — F5 시 로그인 사용자가 튕긴다
export default defineNuxtRouteMiddleware(() => {
  const auth = useAuthStore()
  if (!auth.isLoggedIn) return navigateTo('/auth/login')
})
```

인증 미들웨어는 반드시 아래 두 규칙을 지킨다.

**M-R1. 서버에서는 판정하지 않는다.** `import.meta.server`이면 즉시 반환한다. 서버는 로그인 여부를 알 방법이 없으므로 판정 자체가 성립하지 않는다.

**M-R2. 판정 전에 세션 복구를 기다린다.** 스토어가 비어 있으면 `restoreSession()`(6.6, single-flight)을 `await`한 뒤에 최종 판정한다.

```typescript
// ✅ 올바름
export default defineNuxtRouteMiddleware(async () => {
  if (import.meta.server) return

  const auth = useAuthStore()
  if (!auth.isLoggedIn) await auth.restoreSession()
  if (!auth.isLoggedIn) return navigateTo('/auth/login')
})
```

> **복구를 전역 플러그인에서 `await`하지 않는 이유**: 그렇게 하면 인증과 무관한 공개 페이지에 들어온
> 비로그인 방문자까지 refresh 왕복 한 번만큼 첫 렌더링이 지연된다.
> 대기는 **인증이 필요한 라우트에서만** 발생해야 한다.
> 이 방식은 요청 수도 줄인다. 미들웨어에서 먼저 복구하면 `refresh → 데이터 요청` 2회로 끝나지만,
> 복구 없이 진입하면 `데이터 요청 → 401 → refresh → 재시도` 3회가 나간다.

### 6.3 상태 모델

API Client 인스턴스 내부에 **모듈 스코프가 아닌 클로저 스코프**로 유지한다 (SSR 요청 간 상태 누수 방지).

```typescript
let refreshPromise: Promise<string> | null = null  // single-flight
let isLoggingOut = false
```

> axios 예제의 `failedQueue: Array<{resolve, reject}>` 배열 대신 **단일 Promise 공유(single-flight)** 를 사용한다.
> 동작은 동일하며(대기 → 갱신 완료 후 일괄 재개), 큐 배열을 직접 관리하지 않아 누락·중복 resolve 버그 여지가 없다.

### 6.4 요청 흐름

```
$api.get('/orders/me')
      │
      ▼
[1] rawFetch 실행
      │
      ├── 2xx ──────────────────► ApiResponse.data 반환 (종료)
      │
      └── 401 Unauthorized
              │
              ▼
        [2] 재시도 가능 여부 판정
              │  아니오 (skipRefresh / 이미 재시도함 / 인증 예외 경로 / SSR)
              ├──────────────────► ApiError throw (종료)
              │
              ▼ 예
        [3] ensureRefreshed() 호출
              │
              ├── refreshPromise 존재 → 그 Promise를 await (큐 대기)
              │
              └── 없음 → refreshPromise 생성
                          POST /auth/refresh-token (skipAuth, skipRefresh)
                          │
                          ├── 성공 → authStore.setToken(newToken)
                          │           refreshPromise = null
                          │           resolve(newToken)
                          │
                          └── 실패 → refreshPromise = null
                                      reject
                                      handleLogout()
              │
              ▼
        [4] 갱신 성공 시 원 요청 1회 재시도 (새 Bearer 토큰)
              │
              ├── 2xx ───────────► ApiResponse.data 반환 (종료)
              └── 실패 ──────────► ApiError throw (종료)
```

### 6.5 세부 규칙

**R1. 재시도는 요청당 정확히 1회.**
재시도한 요청이 다시 401을 받으면 갱신을 재차 시도하지 않고 그대로 `ApiError`를 throw 한다.
(axios 예제의 `originalRequest._retry` 플래그에 대응. `$fetch`는 config 객체를 재사용하지 않으므로,
래퍼 함수의 지역 변수 `retried: boolean`으로 관리한다.)

**R2. 갱신 요청 자체는 절대 갱신 흐름에 들어가지 않는다.**
`/auth/refresh-token` 호출은 `skipAuth: true, skipRefresh: true`로 보낸다. 무한 재귀를 원천 차단한다.

**R3. 인증 예외 경로는 401이어도 갱신하지 않는다.**

| 경로 | 이유 |
|---|---|
| `/auth/login` | 자격증명 오류이지 토큰 만료가 아니다 |
| `/auth/signup` | 위와 동일 |
| `/auth/refresh-token` | R2 |

이 경로들의 401은 `ApiError`로 그대로 전파해 폼에서 표출한다.

**R4. 갱신 실패 → 강제 로그아웃.**
순서를 엄격히 지킨다.

1. `refreshPromise = null` (다음 요청이 새로 시도할 수 있도록 먼저 해제)
2. 대기 중이던 모든 요청에 reject 전파
3. `authStore.clearAuth()`
4. `navigateTo('/auth/login')` — 플러그인 컨텍스트 밖이므로 **`nuxtApp.runWithContext()`로 감싼다**

**R5. 로그아웃 중복 방지.**
`isLoggingOut` 플래그로 리다이렉트가 여러 번 실행되지 않게 한다.
이미 `/auth/login`에 있으면 리다이렉트를 생략한다.

**R6. 서버 사이드에서는 갱신하지 않는다.**
`import.meta.server`이면 401을 그대로 `ApiError`로 전파한다.

**R7. 갱신 후 큐 재개는 새 토큰을 사용한다.**
대기 중이던 요청은 `ensureRefreshed()`가 resolve한 토큰으로 `Authorization` 헤더를 다시 만들어 재시도한다.
스토어를 다시 읽는 방식도 허용하되, 두 값은 항상 동일해야 한다.

### 6.6 앱 초기화 시 세션 복구

새로고침하면 Access Token(메모리)이 사라진다. 아래 흐름으로 복구한다.

```
restoreSession()  (client only, single-flight)
      │
      ▼
이미 accessToken이 있거나 복구가 진행 중인가?
      │ 예 → 진행 중인 Promise 반환 (중복 호출 없음)
      │
      ▼ 아니오
isRestoring = true
POST /auth/refresh-token (skipAuth, skipRefresh)
      │
      ├── 200 → setToken + 사용자 정보 조회 (GET /auth/me) → 로그인 상태 복구
      └── 401 → 비로그인 상태로 진행 (에러 표출하지 않음, 리다이렉트하지 않음)
      │
      ▼
isRestoring = false
```

**호출 지점은 두 곳이며 대기 방식이 서로 다르다.**

| 호출 지점 | 방식 | 목적 |
|---|---|---|
| 전역 (클라이언트 플러그인 또는 `app.vue`) | **`await` 하지 않음** (fire-and-forget) | 공개 페이지의 헤더 등 개인화 UI를 뒤늦게라도 채우기 위함. 렌더링을 막지 않는다 |
| 인증 라우트 미들웨어 | **`await` 함** (6.2.1 M-R2) | 로그인 사용자가 새로고침만으로 튕기지 않도록 판정 전에 확정 |

- `restoreSession()`은 **single-flight**로 구현한다. 위 두 지점이 거의 동시에 호출해도 `/auth/refresh-token`은 1회만 나간다.
- 실패는 정상 시나리오(비로그인 방문자)이므로 조용히 무시한다. 콘솔 에러를 남기지 않고 리다이렉트도 하지 않는다.
- 서버 사이드에서는 실행하지 않는다.

#### 6.6.1 복구 중 UI 렌더링 규칙 (깜빡임 방지)

토큰이 메모리에만 있으므로, 전체 페이지 로드 시 **서버가 그린 비로그인 화면 → 복구 완료 → 로그인 화면** 순으로
헤더 등이 한 번 바뀐다. 이는 이 저장 전략의 필연적 결과이며 axios/`$fetch` 선택과 무관하다.
왕복 자체를 없애려면 서버가 로그인 여부를 미리 알아야 하는데, 그 방식(표식 쿠키 · 쿠키 포워딩)은 이번 범위가 아니다 (→ 13절 O1).

대신 **잘못된 상태를 보여주지 않는 것**으로 해결한다. 인증 상태는 2가지가 아니라 **3가지**로 취급한다.

| 상태 | 조건 | 렌더링 |
|---|---|---|
| 판정 중 | `isRestoring === true` | 로그인 버튼도 사용자 정보도 그리지 않는다. 스켈레톤/빈 슬롯만 표시 |
| 로그인 | `isLoggedIn === true` | 사용자 정보 표시 |
| 비로그인 | 위 둘 다 아님 | 로그인 버튼 표시 |

- 개인화 UI(헤더 사용자 영역, 장바구니 배지 등)는 `isLoggedIn` 단독으로 분기하지 않는다. **반드시 `isRestoring`을 먼저 확인한다.**
- 스켈레톤은 실제 콘텐츠와 비슷한 너비/높이로 잡아 레이아웃 이동(CLS)이 생기지 않게 한다.
- 이렇게 하면 화면 전환이 "틀린 상태 → 올바른 상태"가 아니라 "비어 있음 → 채워짐"으로 읽혀 오류로 인지되지 않는다.

### 6.7 동시성 시나리오 (수용 기준)

| # | 시나리오 | 기대 동작 |
|---|---|---|
| C1 | 만료된 토큰으로 5개 요청 동시 발사 | `/auth/refresh-token` 호출은 **정확히 1회**. 5개 모두 새 토큰으로 재시도되어 성공 |
| C2 | 갱신 진행 중 6번째 요청 도착 | 6번째도 같은 `refreshPromise`를 기다렸다가 재개 |
| C3 | 갱신 실패 (refresh 만료) | 5개 요청 모두 reject, `clearAuth()` 1회, `/auth/login` 리다이렉트 1회 |
| C4 | 갱신 성공 후 재시도가 또 401 | 재갱신 없이 `ApiError` throw (R1) |
| C5 | 로그인 폼에서 잘못된 비밀번호 (401) | 갱신 시도 없음. `ApiError(code: 'INVALID_CREDENTIALS')` 전파 (R3) |
| C6 | 네트워크 단절 중 요청 | `ApiError(code: 'NETWORK_ERROR', status: 0)`. 갱신 시도 없음 |
| C7 | SSR 중 인증 요청이 401 | 갱신·리다이렉트 없이 `ApiError` 전파 (R6) |
| C8 | 갱신 실패 직후 새 요청 | `refreshPromise`가 이미 null이므로 새로 갱신 시도 (스토어가 비어 있으면 401 → 로그아웃 경로) |
| C9 | 로그인 상태로 `/orders/me`에서 F5 | 미들웨어가 `restoreSession()`을 await → 복구 성공 → **리다이렉트 없이** 페이지 렌더링 (6.2.1) |
| C10 | 전역 복구와 미들웨어 복구가 동시 발생 | `restoreSession()` single-flight로 `/auth/refresh-token` 호출은 **1회** (6.6) |
| C11 | 비로그인 사용자가 공개 페이지 진입 | 미들웨어 미적용 → 대기 없음. 전역 복구가 백그라운드로 401 1회, 렌더링 지연 없음 |
| C12 | 비로그인 사용자가 `/orders/me` 직접 진입 | 복구 실패 → `/auth/login` 리다이렉트 1회. 콘솔 에러 없음 |

---

## 7. SSR 연동 — `useApiFetch`

`useAsyncData`와 API Client를 결합한 얇은 래퍼를 제공한다.

```typescript
function useApiFetch<T>(
  url: string | (() => string),
  options?: ApiRequestOptions & AsyncDataOptions<T>
): AsyncData<T, ApiError>
```

**요구사항**

- 반환되는 `data`는 이미 언랩된 `T`다 (`ApiResponse` 아님).
- `error`는 `ApiError` 타입이다.
- 기본 `key`는 URL + 직렬화된 query로 자동 생성한다.
- 인증이 필요한 리소스는 호출부가 `{ server: false }`를 명시한다 (→ 6.2).
- locale이 바뀌면 재요청되어야 한다. `watch`에 i18n `locale`을 포함한다.

**사용 예 (구현 시 참고용, 이 스펙에서는 계약만 확정)**

```typescript
// 공개 데이터 — SSR 프리페치
const { data: restaurants, pending, error } = await useApiFetch<Restaurant[]>('/restaurants')

// 인증 데이터 — 클라이언트에서만
const { data: myOrders } = await useApiFetch<Order[]>('/orders/me', { server: false })

// 명령형 호출
const created = await $api.post<Order>('/orders', payload)
```

---

## 8. 인증 스토어 계약 (`stores/auth.ts`)

`auth-example.ts`를 기반으로 하되 아래를 추가한다.

```typescript
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)   // 메모리 전용
  const user = ref<AuthUser | null>(null)
  const isRestoring = ref(false)                 // [ADD] 6.6 세션 복구 진행 중

  const isLoggedIn = computed(() => accessToken.value !== null)

  function setAuth(token: string, authUser: AuthUser): void
  function setToken(token: string): void
  function clearAuth(): void

  /** [ADD] 6.6 세션 복구. single-flight — 동시 호출 시 같은 Promise를 공유한다 */
  function restoreSession(): Promise<void>
})
```

| 항목 | 규칙 |
|---|---|
| `accessToken` | **절대 localStorage / sessionStorage / 쿠키에 저장하지 않는다** |
| `user` | 서버에서 받은 값만 담는다. 클라이언트가 임의 생성하지 않는다 |
| `isRestoring` | 6.6 복구 흐름 동안 `true`. 개인화 UI는 이 값이 `true`면 로그인/비로그인 어느 쪽도 그리지 않는다 (6.6.1) |
| `restoreSession()` | single-flight. 이미 토큰이 있거나 복구 중이면 새 요청을 만들지 않는다. **실패해도 reject 하지 않는다** (비로그인은 정상 상태) |
| Pinia 영속화 플러그인 | 이 스토어에는 적용하지 않는다 |

`AuthUser`, `AuthProvider` 타입은 `auth-example.ts` 정의를 그대로 승계한다.

### 8.1 인증 상태 소비 규칙

`isLoggedIn` 단독 분기는 **판정 중(`isRestoring`)을 비로그인으로 오인**하게 만든다. 아래를 따른다.

```vue
<!-- ❌ 금지 — 복구 중 로그인 버튼이 깜빡였다가 사라진다 -->
<UserMenu v-if="auth.isLoggedIn" />
<LoginButton v-else />

<!-- ✅ 올바름 — 판정 중에는 자리만 잡는다 -->
<AuthSlotSkeleton v-if="auth.isRestoring" />
<UserMenu v-else-if="auth.isLoggedIn" />
<LoginButton v-else />
```

이 규칙은 **개인화 UI에만** 적용한다. 인증 라우트 접근 제어는 미들웨어(6.2.1)가 담당하며, 컴포넌트에서 `navigateTo`로 중복 처리하지 않는다.

---

## 9. i18n 에러 표출

`ApiError`를 화면에 표출하는 규칙은 다음과 같다.

```
ApiError.code  →  `error.${code}` i18n 키  →  $t() 로 표출
```

- 번역 키가 없으면 `error.UNKNOWN_ERROR`로 fallback 한다.
- 컴포넌트가 `error.code`를 문자열 비교로 분기하는 것은 허용하되, **사용자에게 보이는 문장은 반드시 `$t()`를 거친다.**
- `i18n/locales/{ko,ja}.json`에 4.2절의 클라이언트 자체 에러 코드 번역을 추가한다.

| 코드 | ko | ja |
|---|---|---|
| `NETWORK_ERROR` | 네트워크 연결을 확인해 주세요. | ネットワーク接続を確認してください。 |
| `REQUEST_TIMEOUT` | 요청 시간이 초과되었습니다. | リクエストがタイムアウトしました。 |
| `INVALID_RESPONSE` | 서버 응답을 처리할 수 없습니다. | サーバーの応答を処理できません。 |
| `UNKNOWN_ERROR` | 알 수 없는 오류가 발생했습니다. | 不明なエラーが発生しました。 |

---

## 10. 백엔드 요구사항 (member-auth-service / api-gateway)

이 스펙이 성립하려면 백엔드가 아래를 충족해야 한다. **미구현 시 이 문서를 근거로 백엔드 작업을 선행한다.**

### 10.1 토큰 갱신 엔드포인트

| 항목 | 값 |
|---|---|
| 경로 | `POST /api/v1/auth/refresh-token` |
| 인증 | Request Body 없음. Refresh Token은 httpOnly 쿠키에서 읽는다 |
| 성공 | `200` + `ApiResponse<{ accessToken: string }>` |
| 실패 | `401` + `message`: `REFRESH_TOKEN_EXPIRED` / `REFRESH_TOKEN_INVALID` / `REFRESH_TOKEN_NOT_FOUND` |
| 접근 제어 | `permitAll()` (Access Token 없이 호출됨) |

### 10.2 쿠키 설정

| 속성 | 개발 | 운영 |
|---|---|---|
| `HttpOnly` | true | true |
| `Secure` | false | true |
| `SameSite` | `Lax` | `None` (프론트/API 도메인이 다를 경우) |
| `Path` | `/api/v1/auth` | `/api/v1/auth` |
| `Max-Age` | Refresh Token 수명과 동일 | 동일 |

### 10.3 CORS

프론트가 `credentials: 'include'`로 요청하므로 API Gateway는 다음을 만족해야 한다.

- `Access-Control-Allow-Credentials: true`
- `Access-Control-Allow-Origin`은 **와일드카드(`*`) 불가.** 구체 오리진을 명시한다 (개발: `http://localhost:3000`)
- `Access-Control-Allow-Headers`에 `Authorization`, `Content-Type`, `Accept-Language` 포함
- Preflight(`OPTIONS`) 허용

### 10.4 401 응답 일관성

Access Token 만료로 인한 401은 `message`를 `ACCESS_TOKEN_EXPIRED` 또는 `UNAUTHORIZED`로 반환한다.
프론트는 **상태 코드 401만 보고 갱신을 판단**하므로 코드값 자체가 분기에 쓰이지는 않지만, 로깅·디버깅을 위해 일관되게 내려준다.

---

## 11. 마이그레이션 계획

| 단계 | 작업 | 영향 |
|---|---|---|
| M1 | `types/api.ts` 추가 (`ApiError`, `ApiClient`, `ApiRequestOptions`) | 신규 |
| M2 | `stores/auth.ts` 작성 (`auth-example.ts` 승계 + `isRestoring` + `restoreSession()`) | 신규 |
| M3 | `utils/api-client.ts` 작성 (`createApiClient`) | 신규 |
| M4 | `plugins/api.ts`를 `createApiClient` 기반으로 교체 | `$api` 시그니처 변경 |
| M5 | `plugins/auth-restore.client.ts` 추가 (전역 복구, await 없음) | 신규 |
| M6 | `middleware/auth.ts` 추가 (6.2.1 규칙 준수) | 신규 |
| M7 | `composables/useApiFetch.ts` 추가 | 신규 |
| M8 | i18n `error.*` 키 4개 추가 (ko/ja) | 신규 |
| M9 | 헤더 등 개인화 UI에 `isRestoring` 3-state 렌더링 적용 (8.1) | 컴포넌트 |
| M10 | `plugins/01.axios-example.ts`, `stores/auth-example.ts` 삭제 | 제거 |
| M11 | `package.json`에서 `axios` 제거 후 `npm install` | 의존성 |
| M12 | `docs/` 전반의 axios 언급 제거 | 문서 |

### 11.1 호출부 변경 대조표

| Before (axios) | After (`$api`) |
|---|---|
| `const { $axios } = useNuxtApp()` | `const { $api } = useNuxtApp()` |
| `const res = await $api.get<ApiResponse<T>>(url)` | `const data = await $api.get<T>(url)` |
| `res.data.data` | `data` |
| `res.data.message` (에러 코드) | `catch (e) { (e as ApiError).code }` |
| `error.response?.status` | `(e as ApiError).status` |
| `$api.post(url, body, config)` | `$api.post<T>(url, body, options)` (동일 형태) |
| `useAsyncData(() => $axios.get(url))` | `useApiFetch<T>(url)` |

> 현재 `app/composables/`, `app/pages/`에 실제 API 호출부가 거의 없어(`index.vue` 1개, 정적 페이지) 마이그레이션 비용은 낮다.

---

## 12. 수용 기준 (Acceptance Criteria)

구현이 완료되었다고 판단하는 기준이다.

- [ ] 저장소 전체에서 `axios` import가 0건이고, `package.json`에 `axios`가 없다.
- [ ] `$api.get<Restaurant>()`가 `ApiResponse` 래퍼 없이 `Restaurant`를 반환한다.
- [ ] 모든 실패가 `ApiError` 인스턴스로 전파된다 (`instanceof ApiError`가 항상 참).
- [ ] 6.7절 C1~C12 시나리오가 모두 기대대로 동작한다.
- [ ] Access Token이 localStorage / 쿠키 어디에도 기록되지 않는다 (DevTools로 확인).
- [ ] `Accept-Language`가 현재 locale과 일치해 전송된다.
- [ ] 공개 데이터 페이지가 SSR HTML에 콘텐츠를 포함한다 (`view-source`로 확인).
- [ ] 새로고침 후 6.6 복구 흐름으로 로그인 상태가 유지된다.
- [ ] **로그인 상태로 인증 페이지에서 F5를 눌러도 로그인 페이지로 튕기지 않는다** (C9).
- [ ] **복구 중 헤더에 로그인 버튼이 잠깐 나타났다 사라지는 깜빡임이 없다** (6.6.1 / 8.1).
- [ ] 인증 미들웨어에 `import.meta.server` 조기 반환이 있고, 판정 전에 `restoreSession()`을 await 한다.
- [ ] 공개 페이지 첫 진입 시 렌더링이 refresh 왕복만큼 지연되지 않는다 (C11).
- [ ] 비로그인 상태로 첫 방문 시 콘솔에 에러가 노출되지 않는다.

---

## 13. 미결 사항 (Open Questions)

| # | 항목 | 현재 결정 |
|---|---|---|
| O1 | SSR 중 인증 요청 지원 | 이번 범위 제외. 필요해지면 쿠키 포워딩 방식으로 6.2 개정 |
| O1-1 | 복구 왕복 자체를 없애기 (표식 쿠키) | **채택하지 않음.** 백엔드가 refresh 쿠키와 함께 httpOnly가 아닌 `hasSession` 표식 쿠키를 내려주면 서버·클라이언트가 사전에 로그인 여부를 알 수 있으나, 백엔드 계약과 쿠키 수명 동기화 부담 대비 이득이 작다. 6.6.1의 3-state 렌더링으로 체감 문제를 해소한다 |
| O2 | Refresh Token 회전(rotation) 적용 여부 | 백엔드 Phase 5(Member Auth Service)에서 결정. 프론트 계약에는 영향 없음 |
| O3 | 요청 재시도(5xx, 네트워크) 정책 | 이번 범위 제외. `retry: false` 유지 |
| O4 | WebSocket/SSE 연결의 토큰 갱신 연동 | Phase 13(Real-time Communication)에서 별도 스펙 |
