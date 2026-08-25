# api-gateway — 인증 · 에러 처리 스펙 (WebFlux)

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [auth-jwt-spec.md](../member-auth-service/auth-jwt-spec.md) | 토큰 발급 측 계약 (클레임, 수명, 키) |
| [error-handling.md](../../../error-handling.md) | 에러 응답 포맷 및 코드 카탈로그 |
| [api-conventions.md §7.3](../../../api-conventions.md#73-cors) | CORS 요구사항 |
| [참고/01_JWT_VERIFICATION_KR.md](../../../참고/01_JWT_VERIFICATION_KR.md) | 이전 프로젝트의 검증 필터 설계 |
| [참고/03_WEBFLUX_MIGRATION_KR.md](../../../참고/03_WEBFLUX_MIGRATION_KR.md) | Servlet → Reactive 전환 대조표 |

---

## 1. 현재 상태

**api-gateway는 이미 WebFlux(Reactive) 스택입니다.** 마이그레이션이 필요 없습니다.

| 항목 | 현재 값 | 판정 |
|---|---|---|
| Gateway 의존성 | `spring-cloud-starter-gateway` | Reactive |
| Web 스택 | Netty (webmvc 의존성 없음) | Reactive |
| Security | `@EnableWebFluxSecurity` + `SecurityWebFilterChain` | Reactive |
| 기존 필터 | `GlobalLoggingFilter implements GlobalFilter, Ordered` | Reactive |
| 라우팅 | `application.properties`의 `spring.cloud.gateway.routes[*]` + Eureka `lb://` | 동작 중 |

참고 문서의 `03_WEBFLUX_MIGRATION_KR.md`는 **이전 프로젝트가 WebMvc에서 출발했기 때문에** 필요했던 절차입니다.
이 프로젝트는 처음부터 Reactive이므로, 해당 문서에서 가져올 것은 §4.2(`GlobalFilter` 기반 JWT 필터)와 §4.4(`ErrorWebExceptionHandler`)의 **구현 형태**뿐입니다.

### 1.1 라우팅 방식

참고 문서는 `RouteLocator` Bean(Java DSL)을 쓰지만, 이 프로젝트는 `application.properties`에 라우트를 선언하고 있습니다.
**properties 방식을 유지합니다.** 라우트가 정적이고 서비스별 URL이 Eureka `lb://`로 해결되므로 Java DSL의 이점이 없고,
환경별 라우팅 차이를 프로파일 파일로 분리할 수 있습니다.

---

## 2. 신규 컴포넌트

```
com.rtcdelivery.gateway/
├── common/
│   └── ApiResponse.java                     (신규 — 에러 직렬화용)
├── config/
│   ├── SecurityConfig.java                  (기존 — CORS 추가)
│   └── JwtProperties.java                   (신규)
├── exception/
│   ├── ErrorCode.java                       (신규)
│   ├── BusinessException.java               (신규)
│   ├── ErrorResponseWriter.java             (신규 — 응답 직렬화 공용 유틸)
│   └── GatewayErrorWebExceptionHandler.java (신규)
├── filter/
│   ├── GlobalLoggingFilter.java             (기존)
│   └── JwtVerificationFilter.java           (신규)
└── security/
    └── JwtValidator.java                    (신규 — 공개키 검증)
```

> Gateway는 도메인 서비스가 아니므로 `controller/`, `domain/`, `repository/`를 두지 않습니다 ([AGENTS.md §3.2](../../../AGENTS.md)).
> 다만 `ApiResponse`는 에러 본문을 프론트가 파싱 가능한 형태로 내리기 위해 필요합니다.

---

## 3. `JwtValidator`

member-auth-service가 서명한 Access Token을 **공개키로 검증**합니다. 개인키를 갖지 않으므로 토큰을 발급할 수 없습니다.

| 항목 | 내용 |
|---|---|
| 위치 | `security/JwtValidator.java` |
| 입력 | 토큰 문자열 |
| 출력 | `Claims` 또는 검증 실패 사유 |
| 키 | X.509 PEM 공개키. 기동 시 1회 파싱해 `PublicKey`로 보관 |

**검증 항목**

1. RS256 서명 유효성
2. `exp` 만료 여부
3. `iss == rtc-delivery`
4. `typ == "access"` — Refresh Token을 `Authorization` 헤더로 우회 사용하는 것을 차단

**실패 사유 구분**

| 예외 | 에러 코드 |
|---|---|
| `ExpiredJwtException` | `ACCESS_TOKEN_EXPIRED` |
| `SignatureException`, `MalformedJwtException`, `UnsupportedJwtException` | `ACCESS_TOKEN_INVALID` |
| `typ != "access"` | `ACCESS_TOKEN_INVALID` |

> 참고 문서의 `JwtValidator`는 `boolean validateToken()` + `getClaims()` 2단계 API인데,
> 이러면 파싱을 두 번 하고 실패 사유를 잃습니다. **결과 객체 하나를 반환하는 단일 메서드**로 만듭니다.

**블로킹 금지**: Netty 이벤트 루프에서 실행되므로 검증 경로에 I/O를 넣지 않습니다.
공개키 로딩은 생성자에서 끝내고, 이후에는 순수 CPU 연산(서명 검증)만 수행합니다.

---

## 4. `JwtVerificationFilter`

`GlobalFilter` + `Ordered`로 구현합니다.

### 4.1 실행 순서

| 필터 | order | 비고 |
|---|---|---|
| `JwtVerificationFilter` | `-10` | 인증 → 헤더 주입 |
| `GlobalLoggingFilter` | `-1` | 기존 값 |

> 참고 문서는 JWT 필터에 `-1`을 쓰지만, 이 프로젝트는 `GlobalLoggingFilter`가 이미 `-1`을 점유합니다.
> 같은 order면 실행 순서가 빈 등록 순서에 좌우되어 불안정하므로, JWT 필터를 `-10`으로 앞당깁니다.

### 4.2 제외 경로

| 패턴 | 사유 |
|---|---|
| `/api/v1/auth/signup` | 회원가입 |
| `/api/v1/auth/login` | 로그인 |
| `/api/v1/auth/refresh-token` | RT 쿠키로 인증 |
| `/api/v1/auth/logout` | AT 만료 상태에서도 호출됨 |
| `/actuator/health`, `/actuator/info` | 헬스 체크 |
| `/swagger-ui/**`, `/v3/api-docs/**` | API 문서 |
| `OPTIONS` 메서드 전체 | CORS Preflight |

> **`/api/v1/auth/**`를 통째로 열지 않습니다.** 참고 문서는 와일드카드로 열지만, 그러면 `/api/v1/auth/me`도 인증 없이 통과해
> `X-User-Id`가 주입되지 않은 채 member-auth-service에 도달합니다. 개별 경로를 명시합니다.

> **Preflight 제외가 필요한 이유**: 브라우저는 `OPTIONS` 요청에 `Authorization` 헤더를 붙이지 않습니다.
> 제외하지 않으면 모든 인증 API의 Preflight가 401이 되어 본 요청이 아예 전송되지 않습니다.

> **`/api/v1/auth/refresh-token`이 제외 경로인 이유**: 이 요청은 Access Token이 만료된 상태에서 호출되며,
> 인증 수단은 `Authorization` 헤더가 아니라 httpOnly 쿠키의 Refresh Token입니다.
> Gateway는 쿠키를 검증하지 않고 그대로 프록시하며, 실제 검증은 member-auth-service가 수행합니다.
> 따라서 §3의 `typ == "access"` 검증과 갱신 흐름은 충돌하지 않습니다.
> 전체 흐름은 [auth-jwt-spec.md §4.5](../member-auth-service/auth-jwt-spec.md#45-만료부터-갱신까지의-전체-흐름)를 참조하세요.

### 4.3 처리 흐름

```
1. 클라이언트가 보낸 X-User-Id / X-User-Name / X-User-Role 헤더를 무조건 제거
2. 제외 경로면 → chain.filter(exchange)
3. Authorization: Bearer <token> 추출
   └─ 없음 → 401 ACCESS_TOKEN_INVALID
4. JwtValidator로 검증
   ├─ 만료   → 401 ACCESS_TOKEN_EXPIRED
   └─ 무효   → 401 ACCESS_TOKEN_INVALID
5. 클레임에서 userId / sub / role 추출
6. ServerHttpRequest.mutate()로 X-User-Id, X-User-Name, X-User-Role 주입
7. chain.filter(mutated)
```

**1단계를 2단계보다 먼저 하는 것이 핵심입니다.** 제외 경로에서도 클라이언트가 보낸 위조 헤더를 지워야 합니다.
`/api/v1/auth/login`에 `X-User-Role: ROLE_ADMIN`을 실어 보내는 요청을 그대로 통과시키면, 하위 서비스가 이를 신뢰하게 됩니다.

### 4.4 주입 헤더

| 헤더 | 출처 클레임 |
|---|---|
| `X-User-Id` | `userId` |
| `X-User-Name` | `sub` |
| `X-User-Role` | `role` |

`api-conventions.md §7`은 `X-User-Id`, `X-User-Role`만 명시하지만, 로깅·감사에 아이디가 유용하므로 `X-User-Name`을 추가합니다.
**인가 판단은 `X-User-Id`와 `X-User-Role`만으로 합니다.**

### 4.5 401 응답

필터는 예외를 던지지 않고 `ErrorResponseWriter`로 응답을 직접 씁니다. 형식은 `ApiResponse`입니다.

```json
{ "status": 401, "message": "ACCESS_TOKEN_EXPIRED", "data": null }
```

`chain.filter()`를 호출하지 않고 `Mono<Void>`를 즉시 반환해 요청을 종료합니다.

> 참고 문서는 `{"status":401,"code":"ERR_UNAUTHORIZED","message":"인증 정보가 유효하지 않거나 만료되었습니다.","timestamp":"..."}`를 내립니다.
> 이 프로젝트에서 그대로 쓰면 프론트 `api-client.ts`의 `isApiResponse`가 `data` 키 부재로 판정에 실패해
> **에러 코드가 `UNKNOWN_ERROR`로 뭉개지고 토큰 갱신 흐름이 깨집니다** ([error-handling.md §2.2](../../../error-handling.md#22-data를-절대-생략하지-않는다)).
> 또한 한국어 문장을 백엔드가 반환하는 것은 AGENTS.md §3.5 위반입니다.

---

## 5. `GatewayErrorWebExceptionHandler`

`ErrorWebExceptionHandler`를 구현하고 `@Order(-2)`로 등록합니다.
기본 핸들러 `DefaultErrorWebExceptionHandler`가 `-1`이므로, 그보다 앞서야 우리 포맷이 적용됩니다.

### 5.1 예외 매핑

| 예외 | HTTP | `message` |
|---|---|---|
| `BusinessException` | `ErrorCode`가 지정한 상태 | `ErrorCode` 이름 |
| `ResponseStatusException` | 예외의 상태 | 상태에서 역매핑 |
| `NotFoundException` (Spring Cloud Gateway) | 503 | `SERVICE_UNAVAILABLE` |
| `ConnectException` | 503 | `SERVICE_UNAVAILABLE` |
| `TimeoutException` | 504 | `GATEWAY_TIMEOUT` |
| `AccessDeniedException`, `AuthorizationDeniedException` | 403 | `FORBIDDEN` |
| 그 외 | 500 | `INTERNAL_SERVER_ERROR` |

> **`AccessDeniedException`을 403으로 매핑합니다.** 참고 코드는 401을 쓰지만, 이는 "인증됐으나 권한 부족" 상황입니다.
> 401로 내리면 프론트가 토큰 갱신을 시도했다가 다시 403을 받는 왕복이 생깁니다.

> **`NotFoundException`이 503인 이유**: Spring Cloud Gateway는 `lb://` 대상 서비스 인스턴스를 Eureka에서 찾지 못할 때 이 예외를 던집니다.
> 클라이언트 요청 자체는 정상이고 서버 측 가용성 문제이므로 404가 아니라 503이 맞습니다.

### 5.2 커밋 가드

```java
ServerHttpResponse response = exchange.getResponse();
if (response.isCommitted()) {
    return Mono.error(ex);
}
```

응답 본문이 이미 전송되기 시작한 뒤에는 상태 코드와 헤더를 바꿀 수 없습니다.
가드 없이 `setStatusCode`를 호출하면 무시되거나 `UnsupportedOperationException`이 발생합니다.

### 5.3 직렬화

`String.format`으로 JSON 문자열을 조립하지 않고 `ObjectMapper`를 씁니다.
예외 메시지에 따옴표나 개행이 들어가면 조립한 JSON이 깨지기 때문입니다.
`message`에는 에러 코드만 들어가므로 실제로 깨질 값이 들어올 일은 적지만, 규칙으로 고정합니다.

### 5.4 로깅

| 상태 | 레벨 | 스택 트레이스 |
|---|---|---|
| 4xx | `WARN` | 없음 |
| 5xx | `ERROR` | 있음 |

토큰 원문은 어떤 레벨에서도 로그에 남기지 않습니다.

---

## 6. CORS

프론트가 `credentials: 'include'`로 요청하므로 아래를 만족해야 합니다.

| 항목 | 값 |
|---|---|
| `allowedOrigins` | `http://localhost:3000` (dev) — **와일드카드 금지** |
| `allowCredentials` | `true` |
| `allowedMethods` | `GET, POST, PUT, PATCH, DELETE, OPTIONS` |
| `allowedHeaders` | `Authorization`, `Content-Type`, `Accept-Language` |
| `maxAge` | `3600` |

### 6.1 설정 위치를 한 곳으로 모은다

현재 CORS가 `application.properties`의 `spring.cloud.gateway.globalcors.*`에 있습니다.
`@EnableWebFluxSecurity`가 활성화되어 있으므로 **Security 필터 체인의 CORS 설정으로 일원화**합니다.

두 곳에 설정이 있으면 `Access-Control-Allow-Origin` 헤더가 중복 부착되어 브라우저가 응답을 거부할 수 있습니다.
`SecurityConfig`에 `CorsConfigurationSource` Bean을 두고 `.cors(cors -> cors.configurationSource(...))`로 연결한 뒤,
properties의 `globalcors` 항목은 제거합니다.

> `allowedHeaders=*`와 `allowCredentials=true`의 조합은 스펙상 허용되지만,
> 일부 브라우저가 `*`를 리터럴로 해석해 Preflight를 실패시킵니다. **헤더를 명시적으로 나열합니다.**

---

## 7. 설정 키

```properties
# JWT (검증 전용 — 공개키만 보유)
jwt.issuer=rtc-delivery
jwt.public-key-location=classpath:keys/jwt-public.pem

# CORS
gateway.cors.allowed-origins=http://localhost:3000
```

**제거할 것**

| 키 | 사유 |
|---|---|
| `jwt.secret` | HS256 대칭키. RS256 전환으로 불필요하며, **Gateway가 서명 능력을 갖는 것 자체가 위험** |
| `jwt.expiration` | Gateway는 토큰을 발급하지 않으므로 만료 설정이 필요 없다 |
| `spring.cloud.gateway.globalcors.*` | Security 체인으로 일원화 (§6.1) |

---

## 8. `SecurityConfig` 변경

```java
return http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .authorizeExchange(ex -> ex.anyExchange().permitAll())
        .build();
```

**인가 판단은 Security 체인이 아니라 `JwtVerificationFilter`가 담당하므로 `anyExchange().permitAll()`을 유지합니다.**
두 곳에서 인가를 나눠 가지면 어느 쪽이 요청을 막았는지 추적하기 어려워집니다.

`formLogin` / `httpBasic` 비활성화는 필수입니다. 켜져 있으면 인증 실패 시 Spring Security가 로그인 폼이나
`WWW-Authenticate: Basic` 헤더로 응답해, 브라우저가 기본 인증 팝업을 띄웁니다.

---

## 9. 테스트 계획

`WebTestClient` + `StepVerifier`를 사용합니다 (MockMvc 아님).

### 9.1 `JwtValidatorTest`

| 시나리오 | 기대 |
|---|---|
| `validate_유효한AccessToken_클레임반환` | `userId`, `sub`, `role` |
| `validate_만료토큰_ACCESS_TOKEN_EXPIRED` | |
| `validate_다른키로서명_ACCESS_TOKEN_INVALID` | |
| `validate_RefreshToken전달_ACCESS_TOKEN_INVALID` | `typ` 검증 |
| `validate_iss불일치_ACCESS_TOKEN_INVALID` | |

### 9.2 `JwtVerificationFilterTest`

| 시나리오 | 기대 |
|---|---|
| `filter_제외경로_토큰없이통과` | |
| `filter_OPTIONS요청_토큰없이통과` | Preflight |
| `filter_유효토큰_헤더주입` | `X-User-Id` / `X-User-Name` / `X-User-Role` |
| `filter_클라이언트가_X-User-Role위조_제거됨` | **보안 회귀 방어의 핵심 케이스** |
| `filter_제외경로에서도_위조헤더제거됨` | |
| `filter_토큰없음_401_ApiResponse형식` | `status` / `message` / `data` 세 키 존재 |
| `filter_만료토큰_401_ACCESS_TOKEN_EXPIRED` | |

### 9.3 `GatewayErrorWebExceptionHandlerTest`

| 시나리오 | 기대 |
|---|---|
| `handle_BusinessException_코드와상태매핑` | |
| `handle_알수없는예외_500_INTERNAL_SERVER_ERROR` | |
| `handle_응답이미커밋_예외전파` | |
| `handle_모든응답이_ApiResponse형식` | |

---

## 10. 구현 순서

| 단계 | 작업 | 선행 |
|---|---|---|
| 1 | `ApiResponse` + `ErrorCode` + `BusinessException` + `ErrorResponseWriter` | — |
| 2 | `GatewayErrorWebExceptionHandler` | 1 |
| 3 | CORS 일원화 (`SecurityConfig` + properties 정리) | — |
| 4 | `JwtProperties` + `JwtValidator` (공개키) | member-auth의 키 생성 |
| 5 | `JwtVerificationFilter` | 1, 4 |
| 6 | 통합 확인 — 로그인 → 토큰으로 보호된 API 호출 → 만료 시 갱신 | 5 |

1~3은 JWT와 무관하게 **지금 바로 진행할 수 있습니다.**
