# member-auth-service — 인증 · JWT 스펙

> **계약 방식**: Swagger (Code-first). 이 문서는 구현 전 설계 합의를 위한 것이며,
> 확정된 API 계약의 진실의 원천은 Controller/DTO의 springdoc 어노테이션입니다 ([AGENTS.md §2.3](../../../AGENTS.md)).

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [api-conventions.md §7](../../../api-conventions.md#7-인증-및-보안-authentication--security) | 토큰 저장 정책, 갱신 계약, CORS |
| [error-handling.md](../../../error-handling.md) | 에러 응답 포맷 및 코드 카탈로그 |
| [gateway-auth-spec.md](../api-gateway/gateway-auth-spec.md) | Gateway의 토큰 검증 및 헤더 주입 |
| [api-client-spec.md §10](../nuxt-app/api-client-spec.md#10-백엔드-요구사항-member-auth-service--api-gateway) | 프론트엔드가 백엔드에 요구하는 사항 |

---

## 1. 범위

### 1.1 이번에 구현하는 것

- `members` 테이블 및 JPA 엔티티
- 회원가입 / 로그인 / 토큰 갱신 / 로그아웃 / 내 정보 조회
- RS256 비대칭 키 기반 Access Token 발급
- Redis 기반 Refresh Token Rotation (RTR)
- Gateway가 주입한 사용자 헤더를 `SecurityContext`로 승격하는 필터

### 1.2 이번에 구현하지 않는 것

| 항목 | 사유 |
|---|---|
| OAuth (Google / Kakao) | 별도 스펙 선행. 다만 `members` 테이블은 `auth_provider` / `provider_id` 컬럼을 미리 둔다 |
| Access Token 블랙리스트 | 로그아웃 즉시 차단은 후속. 현재는 짧은 AT 수명(30분)으로 노출 창을 제한한다 |
| 비밀번호 재설정 / 이메일 인증 | 후속 |
| 다중 기기 동시 로그인 | **단일 세션 정책**을 채택했다 (§5.2) |

---

## 2. 데이터 모델

### 2.1 `members` 테이블

[01_TABLE_DESIGN_KR.md](../../../참고/01_TABLE_DESIGN_KR.md)를 기준으로 하되, 생성/수정 시각은 `BaseTimeEntity`가 관리합니다.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 내부 고유 식별자. JWT `userId` 클레임의 값 |
| `username` | VARCHAR(50) | NOT NULL, UNIQUE | 로그인 아이디. JWT `sub` 클레임의 값 |
| `password` | VARCHAR(255) | NULL 허용 | BCrypt 해시. OAuth 전용 가입 시 `NULL` |
| `email` | VARCHAR(100) | UNIQUE (NULL 허용) | 연락용 이메일 |
| `nickname` | VARCHAR(50) | NOT NULL, UNIQUE | 서비스 내 표시 이름 |
| `role` | VARCHAR(20) | NOT NULL | `ROLE_USER` / `ROLE_ADMIN` |
| `auth_provider` | VARCHAR(20) | NOT NULL | `LOCAL` / `GOOGLE` / `KAKAO` |
| `provider_id` | VARCHAR(255) | NULL 허용 | 소셜 서비스 고유 식별자 |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | 계정 활성 여부 |
| `created_at` | DATETIME(6) | NOT NULL | `BaseTimeEntity` |
| `updated_at` | DATETIME(6) | NOT NULL | `BaseTimeEntity` |

**인덱스**

| 이름 | 컬럼 | 종류 | 용도 |
|---|---|---|---|
| `uk_members_username` | `username` | UNIQUE | 로그인 조회 |
| `uk_members_email` | `email` | UNIQUE | 이메일 중복 검사 |
| `uk_members_nickname` | `nickname` | UNIQUE | 닉네임 중복 검사 |
| `uk_members_provider` | (`auth_provider`, `provider_id`) | UNIQUE | OAuth 재로그인 식별 (후속) |

> MySQL의 UNIQUE 인덱스는 `NULL`을 중복으로 보지 않으므로, `email` / `provider_id`가 `NULL`인 행이 여럿 있어도 문제없습니다.

### 2.2 `BaseTimeEntity`

아직 프로젝트에 없습니다. `domain/BaseTimeEntity.java`로 신설하고, 이후 모든 서비스의 엔티티가 상속합니다 ([AGENTS.md §3.3](../../../AGENTS.md)).

```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

`config/JpaAuditingConfig`에 `@EnableJpaAuditing`을 둡니다.

### 2.3 `Member` 엔티티 규칙

- `@Getter` + `@Builder` + `@NoArgsConstructor(access = PROTECTED)` + `@AllArgsConstructor(access = PRIVATE)`
- `@Setter` 금지. 상태 변경은 도메인 메서드로만 한다 (`deactivate()`, `changeNickname(...)`, `changePassword(...)`)
- `role`, `authProvider`는 `@Enumerated(EnumType.STRING)`. **`ORDINAL` 금지** — enum 순서가 바뀌면 기존 데이터가 다른 값으로 해석된다
- `toString()`에 `password`를 포함하지 않는다 (`@ToString.Exclude` 또는 `toString` 미생성)

---

## 3. 엔드포인트

Base path는 `/api/v1/auth`입니다. Gateway가 `/api/v1/auth/**`를 member-auth-service로 라우팅합니다.

| # | Method | Path | 접근 | 설명 |
|---|---|---|---|---|
| 1 | POST | `/api/v1/auth/signup` | `permitAll` | 회원가입 |
| 2 | POST | `/api/v1/auth/login` | `permitAll` | 로그인 — AT 반환 + RT 쿠키 |
| 3 | POST | `/api/v1/auth/refresh-token` | `permitAll` | RT 쿠키로 AT 재발급 + RT 회전 |
| 4 | POST | `/api/v1/auth/logout` | `permitAll` | RT 무효화 + 쿠키 삭제 |
| 5 | GET | `/api/v1/auth/me` | `authenticated` | 내 정보 조회 |

> **경로 명명**: 참고 문서는 `POST /api/v1/auth/register`와 `GET /api/v1/members/me`를 쓰지만,
> 이 프로젝트는 프론트엔드가 이미 `/auth/signup`, `/auth/me`, `/auth/refresh-token`으로 구현되어 있어 그쪽에 맞춥니다
> (`api-client.ts`의 `AUTH_EXEMPT_PATHS`, `authStore.ts`의 `restoreSession`).

> **`/auth/refresh-token`과 `/auth/logout`이 `permitAll`인 이유**: 둘 다 Access Token이 없거나 만료된 상태에서 호출됩니다.
> 실제 인가는 httpOnly 쿠키의 Refresh Token 검증으로 이루어집니다.

### 3.1 회원가입 — `POST /api/v1/auth/signup`

**Request**

```json
{
  "username": "hong_gildong",
  "password": "Passw0rd!",
  "nickname": "홍길동",
  "email": "hong@example.com"
}
```

| 필드 | 필수 | 검증 |
|---|---|---|
| `username` | O | 4~20자, `^[a-z0-9_]+$` (영문 소문자 · 숫자 · 언더스코어) |
| `password` | O | 4~64자, 영문 · 숫자 or 특수문자 |
| `nickname` | O | 2~20자, 공백만으로 구성 불가 |
| `email` | X | 이메일 형식, 최대 100자 |

**Response `201`**

```json
{
  "status": 201,
  "message": "Created",
  "data": {
    "id": 1,
    "username": "hong_gildong",
    "nickname": "홍길동",
    "email": "hong@example.com",
    "role": "ROLE_USER",
    "authProvider": "LOCAL",
    "createdAt": "2026-08-25T18:00:00"
  }
}
```

**에러**

| 상황 | HTTP | `message` |
|---|---|---|
| 입력 검증 실패 | 400 | `VALIDATION_ERROR` |
| 아이디 중복 | 409 | `DUPLICATE_USERNAME` |
| 이메일 중복 | 409 | `DUPLICATE_EMAIL` |
| 닉네임 중복 | 409 | `DUPLICATE_NICKNAME` |

**동작**

1. `existsByUsername` / `existsByEmail` / `existsByNickname`으로 사전 검사한다.
2. `PasswordEncoder.encode(password)`로 해시한다. **평문 저장 금지.**
3. `role = ROLE_USER`, `authProvider = LOCAL`, `isActive = true`로 저장한다.
4. **가입만 하고 토큰은 발급하지 않는다.** 프론트는 가입 성공 후 로그인 화면으로 유도한다.

> 사전 검사와 INSERT 사이에는 경합 구간이 있습니다. 동시 요청으로 UNIQUE 제약이 걸리면
> `DataIntegrityViolationException`이 발생하므로, 이를 잡아 위반된 제약명으로 `DUPLICATE_*` 코드를 재판정합니다.

### 3.2 로그인 — `POST /api/v1/auth/login`

**Request**

```json
{ "username": "hong_gildong", "password": "Passw0rd!" }
```

**Response `200`**

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "user": {
      "id": 1,
      "username": "hong_gildong",
      "nickname": "홍길동",
      "role": "ROLE_USER",
      "email": "hong@example.com",
      "authProvider": "LOCAL"
    }
  }
}
```

`user` 객체의 형태는 프론트엔드 `AuthUser` 인터페이스(`stores/authStore.ts`)와 일치해야 합니다.

**Response Header**

```
Set-Cookie: refreshToken=<JWT>; Max-Age=604800; Path=/api/v1/auth; HttpOnly; SameSite=Lax
```

**에러**

| 상황 | HTTP | `message` |
|---|---|---|
| 아이디 없음 · 비밀번호 불일치 | 401 | `INVALID_CREDENTIALS` |
| 비활성 계정 | 403 | `MEMBER_INACTIVE` |
| 소셜 전용 계정(비밀번호 `NULL`)에 비밀번호 로그인 시도 | 401 | `INVALID_CREDENTIALS` |

**동작**

1. `findByUsername` → 없으면 `INVALID_CREDENTIALS`.
2. `passwordEncoder.matches(raw, stored)` → 불일치면 `INVALID_CREDENTIALS`.
3. `isActive == false`면 `MEMBER_INACTIVE`.
4. AT + RT 발급 → Redis에 `RT:{username}` 저장(TTL 7일, 기존 값 덮어쓰기) → RT를 쿠키로 내린다.

> **타이밍 공격 완화**: 아이디가 없을 때도 더미 해시로 `matches`를 한 번 수행해 응답 시간 차이를 줄입니다.

### 3.3 토큰 갱신 — `POST /api/v1/auth/refresh-token`

**Request**: 본문 없음. `refreshToken` 쿠키만 사용합니다.

**Response `200`**

```json
{ "status": 200, "message": "Success", "data": { "accessToken": "eyJhbGciOiJSUzI1NiJ9..." } }
```

새 Refresh Token은 `Set-Cookie`로 함께 내려갑니다 (회전).

**에러**

| 상황 | HTTP | `message` |
|---|---|---|
| 쿠키 없음 | 401 | `REFRESH_TOKEN_NOT_FOUND` |
| 서명 불일치 / 형식 오류 / Redis 저장값과 불일치 | 401 | `REFRESH_TOKEN_INVALID` |
| 만료 | 401 | `REFRESH_TOKEN_EXPIRED` |
| 회원 없음 또는 비활성 | 401 | `REFRESH_TOKEN_INVALID` |

실패 응답에는 **항상 쿠키 삭제 헤더(`Max-Age=0`)를 함께 내려** 브라우저에 죽은 토큰이 남지 않게 합니다.

**동작** (§5.3 상세 참조)

### 3.4 로그아웃 — `POST /api/v1/auth/logout`

**Request**: 본문 없음.

**Response `200`**

```json
{ "status": 200, "message": "Success", "data": null }
```

```
Set-Cookie: refreshToken=; Max-Age=0; Path=/api/v1/auth; HttpOnly; SameSite=Lax
```

**동작**

1. 쿠키에서 RT를 읽어 `username`을 추출한다. 실패해도 **에러를 내지 않는다.**
2. `DEL RT:{username}`.
3. 쿠키 삭제 헤더를 내린다.

> **로그아웃은 항상 200을 반환합니다.** 이미 만료된 토큰으로 로그아웃해도 클라이언트 입장의 목적(세션 종료)은 달성되며,
> 실패를 내리면 프론트가 로그아웃 상태로 전환하지 못하고 갇힙니다.

### 3.5 내 정보 — `GET /api/v1/auth/me`

Gateway가 주입한 `X-User-Id` 헤더를 신뢰해 조회합니다.

**Response `200`**

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "id": 1,
    "username": "hong_gildong",
    "nickname": "홍길동",
    "role": "ROLE_USER",
    "email": "hong@example.com",
    "authProvider": "LOCAL"
  }
}
```

**에러**

| 상황 | HTTP | `message` |
|---|---|---|
| 인증 헤더 없음 | 401 | `UNAUTHORIZED` |
| 헤더의 회원이 DB에 없음 | 404 | `MEMBER_NOT_FOUND` |

---

## 4. JWT 설계

### 4.1 알고리즘 및 키 관리

**RS256 (비대칭)** 을 사용합니다.

| 키 | 보유 서비스 | 용도 |
|---|---|---|
| Private Key (PKCS#8) | member-auth-service **단독** | 서명(발급) |
| Public Key (X.509) | api-gateway | 검증 |

> **HS256 대신 RS256을 쓰는 이유**: 대칭키는 Gateway도 서명 능력을 갖게 되어, Gateway가 뚫리면 임의의 토큰을 위조할 수 있습니다.
> 비대칭키는 검증 측에 공개키만 배포하므로 위조 권한이 발급 서비스 한 곳에 갇힙니다. 검증 서비스가 늘어날수록 이 차이가 커집니다.

**키 주입 방식**

| 프로파일 | 방식 |
|---|---|
| dev | `src/main/resources/keys/`의 PEM 파일. **`.gitignore`에 등록하고 커밋하지 않는다** |
| prod | 환경변수 `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` (Base64 인코딩된 PEM) |

키 생성:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem
```

파싱은 애플리케이션 기동 시 1회만 수행하고 `PrivateKey` / `PublicKey` 객체를 싱글턴으로 보관합니다.
요청마다 PEM을 파싱하면 RSA 키 복원 비용이 매 요청에 붙습니다.

### 4.2 Access Token

| 항목 | 값 |
|---|---|
| 알고리즘 | RS256 |
| 수명 | **30분** (1,800,000ms) |
| 전달 | `Authorization: Bearer <token>` 헤더 |
| 저장 | 클라이언트 메모리(Pinia). localStorage / 쿠키 저장 금지 |
| 검증 | api-gateway |

**클레임**

| 클레임 | 예시 | 설명 |
|---|---|---|
| `iss` | `rtc-delivery` | 발급자 |
| `sub` | `hong_gildong` | 로그인 아이디 |
| `userId` | `1` | `members.id` |
| `role` | `ROLE_USER` | 권한 |
| `typ` | `access` | 토큰 종류 |
| `iat` / `exp` | epoch seconds | 발급/만료 시각 |

> **`typ` 클레임이 필요한 이유**: 두 토큰이 같은 키로 서명되므로, `typ` 없이는 Refresh Token을 `Authorization` 헤더에 넣어
> 7일짜리 Access Token처럼 쓸 수 있습니다. Gateway는 `typ == "access"`가 아니면 거부합니다.

### 4.3 Refresh Token

| 항목 | 값 |
|---|---|
| 알고리즘 | RS256 |
| 수명 | **7일** (604,800,000ms) |
| 전달 | httpOnly 쿠키 (`refreshToken`) |
| 저장 | 브라우저 쿠키 + Redis (서버 측 원본) |

**클레임**: `iss`, `sub`, `userId`, `typ: "refresh"`, `iat`, `exp`
(`role`을 넣지 않습니다. 갱신 시점의 DB 값을 다시 읽어야 권한 변경이 즉시 반영됩니다.)

**쿠키 속성**

| 속성 | dev | prod |
|---|---|---|
| `HttpOnly` | `true` | `true` |
| `Secure` | `false` | `true` |
| `SameSite` | `Lax` | `None` |
| `Path` | `/api/v1/auth` | `/api/v1/auth` |
| `Max-Age` | `604800` | `604800` |

프로파일별 값은 `application-{profile}.properties`의 `auth.cookie.*`로 주입합니다.

> **`SameSite=None`은 `Secure=true`를 요구합니다.** 운영에서 HTTPS가 아니면 브라우저가 쿠키를 통째로 버립니다.
> 개발에서 `Lax`로 충분한 이유는 프론트(`localhost:3000`)와 Gateway(`localhost:8080`)가 포트만 다른 same-site이기 때문입니다.

> **`Path=/api/v1/auth`의 효과**: 카탈로그·주문 등 일반 API 요청에는 Refresh Token이 전송되지 않습니다.
> 토큰이 네트워크에 노출되는 요청 수를 인증 엔드포인트로만 제한합니다.

### 4.4 두 토큰은 서로 다른 경로로 이동한다

`typ` 검증(§4.2)과 갱신 흐름이 충돌하는 것처럼 보일 수 있지만, **Refresh Token은 `Authorization` 헤더를 타지 않습니다.**

| | Access Token | Refresh Token |
|---|---|---|
| 전송 위치 | `Authorization: Bearer` 헤더 | `refreshToken` 쿠키 |
| 붙는 요청 | 모든 API 요청 | `/api/v1/auth/**` 요청만 (쿠키 `Path` 제약) |
| 검증하는 곳 | **api-gateway** (`JwtValidator`) | **member-auth-service** (`RefreshTokenService`) |
| Gateway가 보는가 | 본다. `typ == "access"`가 아니면 거부 | **보지 않는다.** 쿠키는 그대로 프록시될 뿐이다 |

`/api/v1/auth/refresh-token`은 Gateway의 **JWT 검증 제외 경로**입니다
([gateway-auth-spec.md §4.2](../api-gateway/gateway-auth-spec.md#42-제외-경로)).
Gateway는 이 경로에 대해 `Authorization` 헤더를 아예 읽지 않고 그대로 통과시키므로, `typ` 검증이 개입할 여지가 없습니다.

**그렇다면 `typ` 검증은 무엇을 막는가**: 공격자가 어떤 경로로든 Refresh Token 문자열을 손에 넣었을 때,
그것을 `Authorization: Bearer <RT>`에 실어 **7일짜리 Access Token처럼 일반 API에 쓰는 것**을 막습니다.
두 토큰이 같은 개인키로 서명되므로 `typ`가 없으면 Gateway는 둘을 구분할 방법이 없습니다.

### 4.5 만료부터 갱신까지의 전체 흐름

```
① GET /api/v1/orders
   Authorization: Bearer <만료된 AT>

   Gateway JwtVerificationFilter
   → 서명은 유효, typ도 "access", 그러나 exp 만료
   → 라우팅하지 않고 종료
   ← 401 { "status": 401, "message": "ACCESS_TOKEN_EXPIRED", "data": null }

② 프론트 api-client.ts가 401을 받고 ensureRefreshed() 호출 (single-flight)

   POST /api/v1/auth/refresh-token
   Authorization 헤더 없음          ← skipAuth: true
   Cookie: refreshToken=<RT>        ← credentials: 'include'

③ Gateway JwtVerificationFilter
   → 클라이언트가 보낸 X-User-* 헤더 제거
   → /api/v1/auth/refresh-token은 제외 경로이므로 토큰 검증 없이 통과
   → member-auth-service로 프록시 (X-User-* 는 주입하지 않음)

④ member-auth-service
   → 쿠키에서 RT 추출, 서명 · 만료 · typ == "refresh" 검증
   → Redis RT:{username}의 저장값과 대조 (§5.3)
   → 새 AT + 새 RT 발급, Redis 갱신
   ← 200 { "data": { "accessToken": "<새 AT>" } }
     Set-Cookie: refreshToken=<새 RT>

⑤ 프론트가 새 AT를 메모리에 저장하고 ①의 요청을 1회 재시도
   GET /api/v1/orders
   Authorization: Bearer <새 AT>    → 200
```

**②에서 `Authorization` 헤더를 붙이지 않는 것이 중요합니다.** 만료된 AT를 그대로 실어 보내도 Gateway가 제외 경로라 무시하긴 하지만,
만료 토큰을 계속 네트워크에 흘릴 이유가 없습니다. 프론트엔드는 `skipAuth: true`로 이를 보장합니다
(`api-client.ts`의 `ensureRefreshed`, `authStore.ts`의 `doRestore`).

> **응답 코드는 403이 아니라 401입니다.** 401은 "인증 정보가 없거나 유효하지 않음"(→ 갱신하면 해결될 수 있음),
> 403은 "인증은 됐으나 권한 부족"(→ 갱신해도 소용없음)입니다.
> 만료를 403으로 내리면 프론트가 갱신을 시도하지 않고 그대로 실패시킵니다.
> 반대로 권한 부족을 401로 내리면 불필요한 갱신 왕복이 생깁니다.

---

## 5. Refresh Token Rotation (RTR)

### 5.1 Redis 스키마

| 키 | 값 | TTL |
|---|---|---|
| `RT:{username}` | Refresh Token 문자열 | 7일 (RT 수명과 동일) |

### 5.2 단일 세션 정책

사용자당 키가 하나이므로 **가장 최근에 발급된 Refresh Token만 유효**합니다.

| 결과 | 설명 |
|---|---|
| 새 기기에서 로그인하면 | 기존 기기의 Refresh Token이 무효화되어 다음 갱신 시점에 로그아웃된다 |
| 토큰이 탈취되면 | 정상 사용자와 공격자 중 나중에 갱신한 쪽만 살아남고, 다른 쪽은 §5.4의 재사용 탐지에 걸린다 |

다중 기기를 허용하려면 키를 `RT:{userId}:{jti}`로 바꾸고 `jti`를 RT 클레임에 추가해야 합니다. 현 단계에서는 채택하지 않습니다.

### 5.3 갱신 흐름

```
1. 쿠키에서 refreshToken 추출
   └─ 없음 → 401 REFRESH_TOKEN_NOT_FOUND

2. 서명 · 만료 검증
   ├─ 만료      → 401 REFRESH_TOKEN_EXPIRED
   └─ 서명 오류 → 401 REFRESH_TOKEN_INVALID

3. typ == "refresh" 확인
   └─ 아니면 → 401 REFRESH_TOKEN_INVALID

4. GET RT:{username} 조회
   ├─ 없음            → 401 REFRESH_TOKEN_INVALID   (로그아웃됨 또는 TTL 만료)
   └─ 저장값 ≠ 요청값 → 재사용 탐지 → DEL RT:{username} → 401 REFRESH_TOKEN_INVALID

5. 회원 재조회 (권한 · 활성 상태 확인)
   └─ 없음 또는 비활성 → DEL RT:{username} → 401 REFRESH_TOKEN_INVALID

6. 새 AT + 새 RT 발급
7. SET RT:{username} = 새 RT (TTL 7일, 덮어쓰기)
8. 새 RT를 Set-Cookie로 내리고 { accessToken } 반환
```

7단계의 Redis 쓰기가 8단계의 응답보다 **먼저** 완료되어야 합니다.
순서가 뒤집히면 클라이언트가 새 토큰을 받았는데 서버에는 저장되지 않은 창이 생겨, 다음 갱신이 재사용 탐지에 오탐됩니다.

### 5.4 재사용 탐지

4단계에서 "서명은 유효하지만 Redis 저장값과 다른" 토큰은 **이미 회전되어 폐기된 토큰**입니다.
정상 클라이언트는 항상 최신 토큰만 가지고 있으므로, 이 상황은 다음 둘 중 하나입니다.

- 탈취된 옛 토큰이 사용됨
- 갱신 요청이 중복 실행됨

두 경우를 구분할 수 없으므로 **보수적으로 세션 전체를 무효화**합니다 (`DEL RT:{username}`).
정상 사용자는 재로그인 한 번으로 복구되지만, 공격자는 탈취 토큰을 잃습니다.

> 프론트엔드 `api-client.ts`는 갱신을 single-flight로 처리하므로(`ensureRefreshed`),
> 정상 동작 중에는 중복 갱신이 발생하지 않습니다.

### 5.5 프론트엔드 연동

프론트는 이미 구현되어 있으며 아래를 전제합니다.

| 프론트 동작 | 백엔드 요구사항 |
|---|---|
| `authStore.restoreSession()` — 새로고침 시 `POST /auth/refresh-token` → `GET /auth/me` | 두 엔드포인트가 위 계약대로 응답 |
| `api-client.ts` — 401 수신 시 single-flight 갱신 후 1회 재시도 | 401 응답의 `message`가 §3의 코드와 일치 |
| 모든 요청이 `credentials: 'include'` | Gateway CORS가 `allowCredentials: true` + 구체 오리진 |

---

## 6. Security 구성

### 6.1 신뢰 모델

member-auth-service는 **Gateway 뒤에서만 동작한다**고 가정하며, Gateway가 주입한 헤더를 신뢰합니다.

| 헤더 | 값 |
|---|---|
| `X-User-Id` | `members.id` |
| `X-User-Name` | `members.username` |
| `X-User-Role` | `ROLE_USER` / `ROLE_ADMIN` |

> **이 신뢰는 네트워크 격리에 의존합니다.** 서비스 포트(8081~8084)가 외부에 노출되면 누구나 헤더를 위조해 관리자로 행세할 수 있습니다.
> 운영에서는 백엔드 서비스 포트를 외부에 열지 않고, Gateway만 공개합니다.
> Gateway는 클라이언트가 보낸 `X-User-*` 헤더를 **항상 제거한 뒤** 자신이 검증한 값으로 덮어써야 합니다 ([gateway-auth-spec.md](../api-gateway/gateway-auth-spec.md)).

### 6.2 `HeaderAuthenticationFilter`

`OncePerRequestFilter`로 구현하고 `UsernamePasswordAuthenticationFilter` 앞에 등록합니다.

1. `X-User-Id`가 없으면 아무것도 하지 않고 체인을 통과시킨다 (익명 요청).
2. 있으면 `X-User-Role`로 `SimpleGrantedAuthority`를 만들고 `UsernamePasswordAuthenticationToken`을 `SecurityContext`에 넣는다.
3. Principal에는 `X-User-Id`(Long)를 담아 컨트롤러가 `@AuthenticationPrincipal`로 받게 한다.

### 6.3 `SecurityConfig`

```java
.csrf(AbstractHttpConfigurer::disable)
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
        .requestMatchers(HttpMethod.POST,
                "/api/v1/auth/signup",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh-token",
                "/api/v1/auth/logout").permitAll()
        .anyRequest().authenticated())
.exceptionHandling(ex -> ex
        .authenticationEntryPoint(restAuthenticationEntryPoint)
        .accessDeniedHandler(restAccessDeniedHandler))
.addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

> 현재 스캐폴딩은 `anyRequest().permitAll()`입니다. **이 작업에서 `authenticated()`로 바꿔야** `/auth/me`가 보호됩니다.
> CSRF는 쿠키를 쓰지만 비활성으로 둡니다 — Refresh Token 쿠키가 유효한 경로는 `/api/v1/auth`뿐이고
> `SameSite` 속성이 교차 사이트 전송을 막기 때문입니다.

---

## 7. 패키지 구조

```
com.rtcdelivery.memberauth/
├── common/
│   └── ApiResponse.java                 (기존 — @JsonInclude 제거 필요)
├── config/
│   ├── SecurityConfig.java              (기존 — 수정)
│   ├── JpaAuditingConfig.java           (신규)
│   ├── RedisConfig.java                 (신규)
│   └── SwaggerConfig.java               (신규)
├── controller/
│   └── AuthController.java              (신규)
├── domain/
│   ├── BaseTimeEntity.java              (신규)
│   ├── Member.java                      (신규)
│   ├── Role.java                        (신규 — enum)
│   └── AuthProvider.java                (신규 — enum)
├── dto/
│   ├── request/  SignupRequest, LoginRequest
│   └── response/ MemberResponse, LoginResponse, TokenResponse
├── exception/
│   ├── ErrorCode.java                   (신규)
│   ├── BusinessException.java           (기존 — 재작성)
│   ├── GlobalExceptionHandler.java      (기존 — 재작성)
│   ├── RestAuthenticationEntryPoint.java (신규)
│   └── RestAccessDeniedHandler.java     (신규)
├── repository/
│   └── MemberRepository.java            (신규)
├── security/
│   ├── JwtProperties.java               (신규 — @ConfigurationProperties)
│   ├── JwtProvider.java                 (신규 — 토큰 발급)
│   ├── HeaderAuthenticationFilter.java  (신규)
│   └── CookieUtils.java                 (신규)
└── service/
    ├── AuthService.java                 (신규)
    └── RefreshTokenService.java         (신규 — Redis RTR)
```

---

## 8. 설정 키

`application.properties`(공통)와 `application-{profile}.properties`(환경별)로 나눕니다.

```properties
# JWT
jwt.issuer=rtc-delivery
jwt.access-token-validity=1800000
jwt.refresh-token-validity=604800000
jwt.private-key-location=classpath:keys/jwt-private.pem
jwt.public-key-location=classpath:keys/jwt-public.pem

# Refresh Token Cookie
auth.cookie.name=refreshToken
auth.cookie.path=/api/v1/auth
auth.cookie.secure=false
auth.cookie.same-site=Lax
auth.cookie.max-age=604800
```

**기존 설정에서 제거할 것**

| 키 | 사유 |
|---|---|
| `jwt.secret` | HS256 대칭키. RS256 전환으로 불필요 |
| `jwt.expiration` | `jwt.access-token-validity`로 대체 |
| `jwt.refresh-expiration` | `jwt.refresh-token-validity`로 대체 |

prod에서는 `jwt.private-key-location` 대신 환경변수 `JWT_PRIVATE_KEY`(Base64 PEM)를 읽고, `auth.cookie.secure=true`, `auth.cookie.same-site=None`으로 둡니다.

---

## 9. 테스트 계획

커버리지 70% 이상을 유지합니다 ([AGENTS.md §7](../../../AGENTS.md)).

### 9.1 `AuthServiceTest` (Mockito)

| 시나리오 | 기대 |
|---|---|
| `signup_정상입력_회원저장` | 저장된 비밀번호가 원문과 다르다 (해시 확인) |
| `signup_중복아이디_DUPLICATE_USERNAME` | `BusinessException` |
| `signup_중복이메일_DUPLICATE_EMAIL` | `BusinessException` |
| `login_정상자격증명_토큰발급` | AT/RT 반환, Redis 저장 호출 검증 |
| `login_없는아이디_INVALID_CREDENTIALS` | 예외 코드가 `MEMBER_NOT_FOUND`가 **아님** |
| `login_비밀번호불일치_INVALID_CREDENTIALS` | 위와 동일한 코드 |
| `login_비활성계정_MEMBER_INACTIVE` | |

### 9.2 `RefreshTokenServiceTest`

| 시나리오 | 기대 |
|---|---|
| `refresh_유효한토큰_새토큰발급및회전` | Redis 값이 새 토큰으로 교체됨 |
| `refresh_저장값과불일치_세션전체무효화` | `DEL` 호출 + `REFRESH_TOKEN_INVALID` |
| `refresh_만료토큰_REFRESH_TOKEN_EXPIRED` | |
| `refresh_Redis에없음_REFRESH_TOKEN_INVALID` | |
| `refresh_accessToken을전달_REFRESH_TOKEN_INVALID` | `typ` 클레임 검증 |
| `logout_만료토큰_예외없이200` | |

### 9.3 `JwtProviderTest`

| 시나리오 | 기대 |
|---|---|
| `createAccessToken_클레임포함` | `sub`, `userId`, `role`, `typ=access` |
| `createRefreshToken_role클레임없음` | |
| `parse_다른키로서명된토큰_검증실패` | |
| `parse_만료토큰_ExpiredJwtException` | |

### 9.4 `AuthControllerTest` (MockMvc)

| 시나리오 | 기대 |
|---|---|
| `signup_검증실패_400_VALIDATION_ERROR` | `data`에 필드별 상세 포함 |
| `login_성공_Set-Cookie_HttpOnly포함` | 쿠키 속성 검증 |
| `refresh_쿠키없음_401_REFRESH_TOKEN_NOT_FOUND` | |
| `me_인증헤더없음_401_UNAUTHORIZED` | |
| `me_X-User-Id헤더_200` | |

**모든 에러 응답이 `{status, message, data}` 세 키를 모두 포함하는지** 검증하는 케이스를 반드시 포함합니다 ([error-handling.md §2.2](../../../error-handling.md#22-data를-절대-생략하지-않는다)).

---

## 10. 구현 순서

| 단계 | 작업 | 선행 |
|---|---|---|
| 1 | 전 서비스 `ErrorCode` + `BusinessException` + `GlobalExceptionHandler` 정비 | — |
| 2 | `BaseTimeEntity` + `JpaAuditingConfig` | — |
| 3 | `Member` 엔티티 + `Role` / `AuthProvider` enum + `MemberRepository` | 2 |
| 4 | RSA 키 생성 + `JwtProperties` + `JwtProvider` | — |
| 5 | 회원가입 (`AuthService.signup` + Controller + 테스트) | 1, 3 |
| 6 | 로그인 (AT 발급 + RT 쿠키) | 4, 5 |
| 7 | `RedisConfig` + `RefreshTokenService` (RTR) | 6 |
| 8 | `/auth/refresh-token` + `/auth/logout` | 7 |
| 9 | Gateway `JwtValidator` + `JwtVerificationFilter` (공개키 검증) | 4 |
| 10 | `HeaderAuthenticationFilter` + `SecurityConfig` 잠금 + `/auth/me` | 9 |
| 11 | 프론트 로그인 · 회원가입 화면 연동 | 10 |

9단계 이전에는 Gateway가 토큰을 검증하지 않으므로 `X-User-Id`가 주입되지 않고, `/auth/me`는 401만 반환합니다.
따라서 **9와 10은 한 묶음**으로 진행합니다.
