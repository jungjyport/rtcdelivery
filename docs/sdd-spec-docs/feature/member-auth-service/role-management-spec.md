# member-auth-service — 역할 관리 스펙

> **계약 방식**: Swagger (Code-first). API 계약의 진실의 원천은 Controller/DTO의 springdoc 어노테이션입니다.

> ⚠️ **이 문서는 구현 이후에 작성되었습니다.** 설계 논의가 대화로 진행되어 SDD 순서가 뒤집혔습니다.
> 내용은 구현된 코드와 일치하며, 이후 변경은 스펙 → 코드 순서를 따릅니다.

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [auth-jwt-spec.md](./auth-jwt-spec.md) | 토큰 발급, RS256 키 관리, RTR |
| [api-conventions.md §7](../../../api-conventions.md#7-인증-및-보안-authentication--security) | 인증 헤더 규약 |
| [catalog-spec.md §4](../food-catalog-service/catalog-spec.md#4-인가-설계) | `ROLE_OWNER`를 실제로 쓰는 쪽 |

---

## 1. 범위

점주(`ROLE_OWNER`) 역할을 추가하고, 관리자가 회원의 역할을 변경할 수 있게 합니다.

**구현하지 않은 것**

| 항목 | 사유 |
|---|---|
| 점주 직접 회원가입 | 아무나 점주로 가입하면 검증 절차가 없다. 일반 가입 후 관리자 승격만 허용한다 |
| 사업자 등록 정보 · 승인 워크플로 | 후속. 현재는 관리자의 수동 승격이다 |
| Access Token 즉시 무효화 | JWT 블랙리스트가 필요하다 (§4의 한계) |

---

## 2. 역할 모델

```java
public enum Role {
    ROLE_USER,
    ROLE_OWNER,
    ROLE_ADMIN
}
```

**회원은 역할을 하나만 갖습니다.** `Set<Role>`도, `RoleHierarchy`도 쓰지 않습니다.

### 2.1 `Set<Role>`을 쓰지 않는 이유

이 도메인의 역할은 서로 직교하는 능력의 조합이 아니라 포함 관계에 가깝습니다.
`Set`으로 만들면 JWT 클레임, `X-User-Role` 헤더, 필터의 권한 변환이 전부 단수 → 복수로 바뀌어야 하는데,
그 비용에 걸맞은 표현력이 필요하지 않습니다.

### 2.2 `RoleHierarchy`를 쓰지 않는 이유

"점주도 일반 사용자 기능(주문 등)을 쓸 수 있어야 한다"는 요구는 실제로는
"**ROLE_USER여야 한다**"가 아니라 "**로그인만 했으면 된다**"입니다. 그래서 계층 대신 이렇게 씁니다.

```java
@PreAuthorize("isAuthenticated()")            // 로그인 사용자 전체
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")  // 점주 기능
@PreAuthorize("hasRole('ADMIN')")             // 관리자 전용
```

계층 빈은 공유 모듈이 없어 4개 서비스에 복제해야 하고, `@PreAuthorize`에 적용하려면
`MethodSecurityExpressionHandler`까지 따로 등록해야 합니다 (필터 레벨과 달리 자동 적용되지 않습니다).
`isAuthenticated()`로 요구가 해소되므로 그 비용을 지지 않기로 했습니다.

### 2.3 역할 검사는 소유권 검사가 아니다

`ROLE_OWNER`는 "점주인가"만 판정합니다. 점주 A가 점주 B의 가게를 수정하는 것은 막지 못하므로,
자원을 다루는 서비스가 `ownerId`와 `X-User-Id`를 별도로 대조해야 합니다
([catalog-spec.md §4.4](../food-catalog-service/catalog-spec.md#44-소유권-위반은-403이-아니라-404)).

---

## 3. 역할 변경 API

```
PATCH /api/v1/members/{memberId}/role
```

Gateway가 이미 `/api/v1/members/**`를 이 서비스로 라우팅하므로 별도 경로 추가는 없습니다.
`/api/v1/admin/**` 같은 URL 접두어로 관리자 전용을 표현하지 않고, `@PreAuthorize("hasRole('ADMIN')")`로 판정합니다.

**Request**

```json
{ "role": "ROLE_OWNER" }
```

**Response `200`** — `MemberResponse` (`id`, `username`, `nickname`, `email`, `role`, `authProvider`, `createdAt`)

**에러**

| 상황 | HTTP | `message` |
|---|---|---|
| 비로그인 | 401 | `UNAUTHORIZED` |
| 관리자가 아님 | 403 | `FORBIDDEN` |
| 대상 회원 없음 | 404 | `MEMBER_NOT_FOUND` |
| 자기 자신의 역할 변경 시도 | 400 | `CANNOT_CHANGE_OWN_ROLE` |
| `role` 누락 / 잘못된 값 | 400 | `VALIDATION_ERROR` |

**동작**

1. 대상이 호출자 본인이면 `CANNOT_CHANGE_OWN_ROLE`. 관리자가 스스로를 강등해 마지막 관리자 권한이 사라지는 것을 막습니다.
2. 회원 조회. 없으면 `MEMBER_NOT_FOUND`.
3. 현재 역할과 같으면 아무것도 하지 않고 반환합니다 (Refresh Token도 건드리지 않음).
4. `Member.changeRole(role)` — `@Setter` 금지 규칙에 따라 도메인 메서드로 변경합니다.
5. `DEL RT:{username}` — §4 참조.

---

## 4. 역할 변경과 토큰

역할은 Access Token의 `role` 클레임에 담기고 Gateway가 이를 `X-User-Role`로 주입합니다.
따라서 **이미 발급된 Access Token은 만료(30분)까지 옛 역할을 그대로 유지합니다.**

| 방향 | 영향 |
|---|---|
| 승격 (USER → OWNER) | 최대 30분간 점주 기능을 쓰지 못한다. 재로그인하면 즉시 반영 |
| 강등 (OWNER → USER) | **최대 30분간 점주 기능이 남는다.** 보안상 문제 |

강등을 완화하기 위해 역할 변경 시 Redis의 `RT:{username}`을 삭제합니다.
갱신 시점에 `REFRESH_TOKEN_INVALID`가 나므로 재로그인이 강제되고, 새 토큰은 새 역할을 갖습니다.

> **남은 30분의 노출 창은 감수합니다.** 즉시 차단하려면 Access Token 블랙리스트가 필요한데,
> 이는 [auth-jwt-spec.md §1.2](./auth-jwt-spec.md#12-이번에-구현하지-않는-것)에서 이미 후속으로 미룬 항목입니다.
> 소유권 검증이 DB의 `ownerId`를 기준으로 하므로, 강등된 점주가 이 창 동안 할 수 있는 일은
> 원래 자기 소유였던 자원을 다루는 것으로 제한됩니다.

Refresh Token에는 `role` 클레임을 넣지 않습니다. 갱신 시점에 DB를 다시 읽어야 변경이 반영되기 때문입니다
([auth-jwt-spec.md §4.3](./auth-jwt-spec.md#43-refresh-token)).

---

## 5. 다른 서비스에 미치는 영향

| 서비스 | 변경 |
|---|---|
| api-gateway | **없음.** `role` 클레임을 문자열로 그대로 헤더에 옮기므로 값이 늘어도 무관하다 |
| food-catalog-service | `Role` enum 복제 + `@PreAuthorize`로 `ROLE_OWNER` 사용 |
| order / payment | 아직 없음 |

food-catalog가 `Role` enum을 복제해 갖는 것은 헤더 문자열을 비교하기 위해서입니다.
**회원 정보의 소유자는 member-auth이므로, 이쪽 enum에 값을 추가하면 복제본도 함께 갱신해야 합니다.**

---

## 6. 테스트

`MemberServiceTest` (Mockito)

| 시나리오 | 기대 |
|---|---|
| `changeRole_점주로_승격하고_Refresh_Token을_폐기한다` | `invalidateByUsername` 호출 |
| `changeRole_같은_역할이면_Refresh_Token을_건드리지_않는다` | 호출 없음 |
| `changeRole_자기_자신의_역할은_바꿀_수_없다` | `CANNOT_CHANGE_OWN_ROLE`, 조회조차 하지 않음 |
| `changeRole_존재하지_않는_회원은_MEMBER_NOT_FOUND` | |
