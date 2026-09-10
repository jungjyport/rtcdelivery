# RTC Delivery — Error Handling

## 1. 개요

전 서비스가 동일한 형태의 에러 응답을 내려주기 위한 표준을 정의합니다.
[api-conventions.md §5](./api-conventions.md#5-system-error--status-code-처리-규칙)가 정한 응답 포맷을 실제 코드로 어떻게 구현할지가 이 문서의 범위입니다.

**적용 대상**

| 대상 | 스택 | 핸들러 |
|---|---|---|
| member-auth-service | Spring MVC | `@RestControllerAdvice` — `GlobalExceptionHandler` |
| food-catalog-service | Spring MVC | 동일 |
| order-service | Spring MVC | 동일 |
| payment-service | Spring MVC | 동일 |
| api-gateway | Spring WebFlux | `ErrorWebExceptionHandler` — `GatewayErrorWebExceptionHandler` |
| discovery-service | — | 도메인 API가 없으므로 제외 |

---

## 2. 응답 계약

### 2.1 형태

성공과 실패 모두 `ApiResponse<T>` 래퍼를 사용합니다. **에러 전용 필드(`code`, `timestamp`, `errors` 등)를 최상위에 추가하지 않습니다.**

```json
{
  "status": 409,
  "message": "DUPLICATE_USERNAME",
  "data": null
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | number | HTTP 상태 코드와 동일한 값 |
| `message` | string | `SCREAMING_SNAKE_CASE` 에러 코드. 사람이 읽는 문장이 아니다 |
| `data` | any \| null | 실패 시 원칙적으로 `null`. 필드 검증 실패만 예외적으로 상세를 담는다 |

### 2.2 `data`를 절대 생략하지 않는다

프론트엔드 `api-client.ts`는 응답이 `ApiResponse`인지 아래로 판정합니다.

```typescript
// frontend/nuxt-app/app/utils/api-client.ts
function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  if (value === null || typeof value !== 'object') return false
  const record = value as Record<string, unknown>
  return typeof record.status === 'number'
    && typeof record.message === 'string'
    && 'data' in record
}
```

`'data' in record`를 요구하므로 `data` 키가 JSON에서 빠지면 판정에 실패하고, 에러 코드 대신 `UNKNOWN_ERROR`가 프론트에 노출됩니다.
따라서 `ApiResponse`에 **`@JsonInclude(NON_NULL)`을 붙이지 않습니다.** `data`는 `null`이라도 직렬화되어야 합니다.

### 2.3 검증 실패 응답

`message`는 항상 단일 에러 코드이므로, 필드별 상세는 `data`에 담습니다.

```json
{
  "status": 400,
  "message": "VALIDATION_ERROR",
  "data": {
    "username": "4자 이상 20자 이하여야 합니다",
    "password": "영문/숫자/특수문자를 각각 1자 이상 포함해야 합니다"
  }
}
```

`data`의 값은 개발자 디버깅용이며 **사용자에게 그대로 표출하지 않습니다.** 프론트는 `error.VALIDATION_ERROR` i18n 키로 문장을 만들고, 필요 시 `data`의 키(필드명)만 사용해 해당 입력란을 강조합니다.

---

## 3. 에러 코드 카탈로그

프론트엔드는 각 코드를 `error.{CODE}` i18n 키로 매핑합니다. **새 코드를 추가하면 `i18n/locales/{ko,ja}.json`에도 함께 추가합니다.**

### 3.1 공통 (전 서비스)

| 코드 | HTTP | 발생 조건 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Bean Validation 실패 (`@Valid` 바인딩 오류) |
| `INVALID_PARAMETER` | 400 | 필수 쿼리 파라미터 누락, 타입 불일치 |
| `MALFORMED_REQUEST_BODY` | 400 | JSON 파싱 실패 |
| `UNAUTHORIZED` | 401 | 인증 정보 없음 또는 무효 |
| `FORBIDDEN` | 403 | 인증은 되었으나 권한 부족 |
| `ENDPOINT_NOT_FOUND` | 404 | 매핑되지 않은 경로 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
| `DATA_INTEGRITY_VIOLATION` | 409 | DB 제약 위반 (unique, FK 등) |
| `RATE_LIMIT_EXCEEDED` | 429 | Gateway Rate Limit 초과 |
| `INTERNAL_SERVER_ERROR` | 500 | 처리되지 않은 예외 |
| `SERVICE_UNAVAILABLE` | 503 | 다운스트림 서비스 응답 불가 |
| `GATEWAY_TIMEOUT` | 504 | 다운스트림 응답 타임아웃 |

### 3.2 인증 · 회원 (member-auth-service / api-gateway)

| 코드 | HTTP | 발생 조건 |
|---|---|---|
| `DUPLICATE_USERNAME` | 409 | 회원가입 시 아이디 중복 |
| `DUPLICATE_EMAIL` | 409 | 회원가입 시 이메일 중복 |
| `DUPLICATE_NICKNAME` | 409 | 회원가입 시 닉네임 중복 |
| `INVALID_CREDENTIALS` | 401 | 로그인 실패 (아이디 없음 / 비밀번호 불일치 **구분하지 않음**) |
| `MEMBER_NOT_FOUND` | 404 | 존재하지 않는 회원 조회 |
| `MEMBER_INACTIVE` | 403 | 비활성(`is_active = false`) 계정 |
| `CANNOT_CHANGE_OWN_ROLE` | 400 | 관리자가 자신의 역할을 변경하려 함 (마지막 관리자 권한 소실 방지) |
| `ACCESS_TOKEN_EXPIRED` | 401 | Access Token 만료 |
| `ACCESS_TOKEN_INVALID` | 401 | 서명 불일치 / 형식 오류 / 토큰 누락 |
| `REFRESH_TOKEN_NOT_FOUND` | 401 | 쿠키에 Refresh Token 없음 |
| `REFRESH_TOKEN_INVALID` | 401 | 서명 불일치 또는 Redis 저장값과 불일치 (재사용 탐지) |
| `REFRESH_TOKEN_EXPIRED` | 401 | Refresh Token 만료 |

> **`INVALID_CREDENTIALS`를 세분화하지 않는 이유**: "존재하지 않는 아이디"와 "비밀번호 불일치"를 구분해 내려주면 공격자가 유효한 아이디 목록을 열거할 수 있습니다.

### 3.3 도메인별 코드

각 서비스는 자신의 `ErrorCode` enum에 도메인 코드를 추가합니다. 명명은 `{리소스}_{사유}` 형식을 따릅니다.

```
RESTAURANT_NOT_FOUND, RESTAURANT_CLOSED, FOOD_NOT_FOUND, FOOD_UNAVAILABLE
CATEGORY_NOT_FOUND, DUPLICATE_CATEGORY_CODE, DUPLICATE_CATEGORY_NAME
ORDER_NOT_FOUND, ORDER_ALREADY_CANCELLED, INVALID_ORDER_STATUS_TRANSITION
PAYMENT_NOT_FOUND, PAYMENT_ALREADY_COMPLETED, REFUND_NOT_ALLOWED
```

> **소유권 위반은 `FORBIDDEN`이 아니라 `RESTAURANT_NOT_FOUND`로 응답합니다.** 남의 음식점을 수정하려는
> 요청에 403을 주면 "그 ID의 음식점이 존재한다"는 사실이 노출되어 리소스 열거가 가능해집니다.

---

## 4. MVC 서비스 구현 (member-auth / food-catalog / order / payment)

패키지: `{base-package}.exception`

### 4.1 `ErrorCode` (enum)

에러 코드와 HTTP 상태를 한 곳에 묶습니다. 코드값은 enum 상수명과 동일하므로 별도 문자열 필드를 두지 않습니다.

```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    // ...
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    /** 응답 message에 실리는 값. enum 상수명이 곧 에러 코드다. */
    public String code() {
        return name();
    }
}
```

### 4.2 `BusinessException`

`ErrorCode`를 받는 생성자를 기본으로 합니다. HTTP 상태는 `ErrorCode`가 이미 알고 있으므로 호출부가 지정하지 않습니다.

```java
throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
```

- 스택 트레이스가 필요 없는 예상된 흐름이므로 `super(message, null, false, false)`로 스택 트레이스 수집을 끕니다.
- `detail` 필드에 로그 전용 부가 정보를 담을 수 있습니다. **`detail`은 응답에 포함되지 않습니다.**

### 4.3 `GlobalExceptionHandler`

`@RestControllerAdvice`로 아래 예외를 매핑합니다.

| 예외 | 에러 코드 |
|---|---|
| `BusinessException` | 예외가 지닌 `ErrorCode` |
| `MethodArgumentNotValidException`, `BindException` | `VALIDATION_ERROR` (+ `data`에 필드별 상세) |
| `ConstraintViolationException` | `VALIDATION_ERROR` |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` | `INVALID_PARAMETER` |
| `HttpMessageNotReadableException` | `MALFORMED_REQUEST_BODY` |
| `HttpRequestMethodNotSupportedException` | `METHOD_NOT_ALLOWED` |
| `NoHandlerFoundException`, `NoResourceFoundException` | `ENDPOINT_NOT_FOUND` |
| `AuthenticationException` | `UNAUTHORIZED` |
| `AccessDeniedException` | `FORBIDDEN` |
| `DataIntegrityViolationException` | `DATA_INTEGRITY_VIOLATION` |
| `Exception` (fallback) | `INTERNAL_SERVER_ERROR` |

**로그 레벨 규칙**

| 대상 | 레벨 | 스택 트레이스 |
|---|---|---|
| 4xx (클라이언트 귀책) | `WARN` | 남기지 않음 |
| 5xx (서버 귀책) | `ERROR` | 남김 |

`AccessDeniedException`을 `@RestControllerAdvice`가 잡으려면 Spring Security 필터 체인보다 뒤에서 발생해야 합니다. 필터 단계에서 발생하는 401/403은 `AuthenticationEntryPoint` / `AccessDeniedHandler`를 등록해 동일한 `ApiResponse` 포맷으로 직렬화합니다.

### 4.4 `SecurityConfig`에 진입점 연결

```java
.exceptionHandling(ex -> ex
        .authenticationEntryPoint(restAuthenticationEntryPoint)
        .accessDeniedHandler(restAccessDeniedHandler))
```

두 핸들러는 `exception/` 패키지에 두고, `ObjectMapper`로 `ApiResponse.error(...)`를 직접 써 내려갑니다.

---

## 5. Gateway 구현 (WebFlux)

### 5.1 `@RestControllerAdvice`를 쓰지 않는 이유

Spring Cloud Gateway(Reactive)는 프록시 필터 체인에서 발생한 예외를 MVC 예외 핸들러로 전달하지 않습니다.
`ErrorWebExceptionHandler`를 구현하고 `@Order(-2)`로 기본 핸들러(`DefaultErrorWebExceptionHandler`, `-1`)보다 먼저 실행되게 합니다.

### 5.2 `GatewayErrorWebExceptionHandler`

`docs/참고/GlobalErrorHandler/GatewayErrorWebExceptionHandler.java`를 이식하되 아래를 바꿉니다.

| 참고 코드 | 이 프로젝트 | 이유 |
|---|---|---|
| `{"code":…,"message":"…","status":…,"timestamp":"…"}` | `ApiResponse` — `{"status":…,"message":"CODE","data":null}` | 프론트 `api-client.ts`가 `ApiResponse`만 파싱한다 (§2.2) |
| `message`에 한국어 문장 | `message`에 에러 코드, 문장은 프론트 i18n | AGENTS.md §3.5 |
| `String.format`으로 JSON 조립 | `ObjectMapper.writeValueAsBytes` | 메시지에 따옴표가 섞이면 JSON이 깨진다 |
| `GlobalException` | `BusinessException` | 서비스 간 예외 클래스명 통일 |
| `implements WebExceptionHandler` | `implements ErrorWebExceptionHandler` | 에러 처리 전용 인터페이스가 우선순위 계약에 맞다 |

**추가 매핑**

| 예외 | 에러 코드 | HTTP |
|---|---|---|
| `BusinessException` | 예외가 지닌 `ErrorCode` | 코드에 대응 |
| `ResponseStatusException` | 상태 코드에서 역매핑 | 예외의 상태 |
| `NotFoundException` (Gateway) | `SERVICE_UNAVAILABLE` | 503 |
| `ConnectException` | `SERVICE_UNAVAILABLE` | 503 |
| `TimeoutException` | `GATEWAY_TIMEOUT` | 504 |
| `AccessDeniedException`, `AuthorizationDeniedException` | `FORBIDDEN` | 403 |
| 그 외 | `INTERNAL_SERVER_ERROR` | 500 |

> 참고 코드는 `AccessDeniedException`을 **401**로 매핑하지만, 이는 "인증은 되었으나 권한이 없음"이므로 **403**이 맞습니다.
> 401로 내리면 프론트 `api-client.ts`가 토큰 갱신을 시도했다가 다시 403을 받는 불필요한 왕복이 생깁니다.

**응답 커밋 가드**

응답이 이미 커밋된 뒤에는 상태 코드를 바꿀 수 없으므로, 핸들러 진입 시 `response.isCommitted()`를 확인하고 커밋되었으면 `Mono.error(ex)`를 그대로 전파합니다.

### 5.3 JWT 검증 실패 응답

`JwtVerificationFilter`는 예외를 던지지 않고 응답을 직접 씁니다(성능상 예외 생성 회피). 다만 **본문 조립은 에러 핸들러와 동일한 유틸(`ErrorResponseWriter`)을 공유**해 포맷이 갈라지지 않게 합니다.

상세는 [gateway-auth-spec.md](./sdd-spec-docs/feature/api-gateway/gateway-auth-spec.md)를 참조하세요.

---

## 6. 체크리스트

새 서비스를 추가하거나 에러 처리를 손볼 때 확인합니다.

- [ ] `ApiResponse`에 `@JsonInclude(NON_NULL)`이 없다 (§2.2)
- [ ] `message`에 한국어/일본어 문장이 들어가지 않는다
- [ ] 새 에러 코드를 §3 카탈로그와 `i18n/locales/{ko,ja}.json`에 함께 등록했다
- [ ] 4xx는 `WARN`(스택 없음), 5xx는 `ERROR`(스택 포함)로 로깅한다
- [ ] Security 필터 단계의 401/403도 `ApiResponse` 포맷으로 나간다
- [ ] 예외 메시지에 비밀번호·토큰 원문이 로그로 새지 않는다
