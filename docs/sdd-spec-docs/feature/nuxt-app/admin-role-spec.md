# nuxt-app — 관리자 역할 관리 화면 스펙

> **상태**: 구현 완료
> **대상**: `frontend/nuxt-app`
> **계약 방식**: 문서 기반. API 계약은 [role-management-spec.md](../member-auth-service/role-management-spec.md)을 따른다.

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [role-management-spec.md](../member-auth-service/role-management-spec.md) | 백엔드 역할 모델, 승격 API, RTR 폐기 정책 |
| [auth-jwt-spec.md](../member-auth-service/auth-jwt-spec.md) | 토큰 발급 및 만료(30분), 역할 클레임 |
| [api-client-spec.md](./api-client-spec.md) | `$api`, 에러 정규화 (`ApiError`) |

---

## 1. 범위

### 1.1 이번에 구현하는 것

- **관리자 권한 가드 미들웨어 (`admin.ts`)**:
  - `authStore.restoreSession()` 완료 대기
  - 비로그인 시 `/auth/login?redirect={path}`로 리다이렉트
  - 로그인 상태이나 역할이 `ROLE_ADMIN`이 아니면 홈(`/`)으로 리다이렉트
- **글로벌 헤더 네비게이션 연동 (`AppHeader.vue`)**:
  - `authStore.user.role === 'ROLE_ADMIN'`일 때 상단 네비게이션에 "역할 관리"(`nav.manageRoles`) 링크 노출
- **회원 역할 관리 페이지 (`/admin/members`)**:
  - 대상 회원 번호(Member ID) 입력 및 역할(`ROLE_USER`, `ROLE_OWNER`, `ROLE_ADMIN`) 선택
  - 역할 변경 요청 (`PATCH /api/v1/members/{memberId}/role`)
  - 결과 피드백: 성공 시 변경된 회원 정보(`MemberResponse`) 노출
  - 안전 장치: 현재 로그인한 관리자 본인의 ID는 변경 시도 차단 (`CANNOT_CHANGE_OWN_ROLE`)
  - 토큰 갱신 안내: 역할 변경 시 Redis Refresh Token이 폐기되어 해당 회원의 다음 로그인/갱신 시 새 역할이 반영됨을 안내하는 배너
- **타입 및 Composable (`app/composables/useRoleManagement.ts`)**:
  - 관리자 역할 변경 API 호출 캡슐화

### 1.2 이번에 구현하지 않는 것

| 항목 | 사유 |
|---|---|
| 전체 회원 목록 페이징 검색 API 연동 | 백엔드 member-auth-service에 현재 회원 목록 검색 엔드포인트가 없음 (ID 기반 변경만 존재) |
| 카테고리 관리 UI | 카탈로그 관리자 기능으로 별도 확장 가능 |

---

## 2. 라우트 및 접근 제어

| 경로 | 파일 | 미들웨어 | 설명 |
|---|---|---|---|
| `/admin/members` | `app/pages/admin/members.vue` | `admin` | 관리자 회원 역할 승격/강등 UI |

### 2.1 `admin` 미들웨어 (`app/middleware/admin.ts`)

```ts
export default defineNuxtRouteMiddleware(async (to) => {
  if (import.meta.server) return

  const authStore = useAuthStore()
  await authStore.restoreSession()

  if (!authStore.isLoggedIn) {
    return navigateTo({
      path: '/auth/login',
      query: { redirect: to.fullPath },
    })
  }

  if (authStore.user?.role !== 'ROLE_ADMIN') {
    return navigateTo('/')
  }
})
```

---

## 3. API 계약

### 3.1 역할 변경 — `PATCH /members/:memberId/role`

- **권한**: `ROLE_ADMIN`
- **Request Body**:
  ```json
  {
    "role": "ROLE_OWNER"
  }
  ```
- **Response `200`**:
  ```ts
  interface MemberResponse {
    id: number
    username: string
    nickname: string
    email: string | null
    role: string // 'ROLE_USER' | 'ROLE_OWNER' | 'ROLE_ADMIN'
    authProvider: string
    createdAt: string
  }
  ```
- **에러**:
  - `400 CANNOT_CHANGE_OWN_ROLE`: 관리자가 자기 자신의 역할을 변경하려고 시도한 경우
  - `404 MEMBER_NOT_FOUND`: 존재하지 않는 회원 ID
  - `403 FORBIDDEN`: 관리자가 아닌 사용자가 호출한 경우

---

## 4. Composable 설계 (`app/composables/useRoleManagement.ts`)

```ts
export function useRoleManagement() {
  const { $api } = useNuxtApp()
  const isSubmitting = ref(false)
  const errorMessage = ref<string | null>(null)
  const successResult = ref<MemberResponse | null>(null)

  async function updateMemberRole(memberId: number, role: string): Promise<MemberResponse> {
    isSubmitting.value = true
    errorMessage.value = null
    successResult.value = null

    try {
      const res = await $api.patch<MemberResponse>(`/members/${memberId}/role`, { role })
      successResult.value = res
      return res
    } catch (e) {
      const err = e as ApiError
      errorMessage.value = err.i18nKey
      throw err
    } finally {
      isSubmitting.value = false
    }
  }

  return {
    isSubmitting,
    errorMessage,
    successResult,
    updateMemberRole,
  }
}
```

---

## 5. 화면 UI 및 UX 설계

- **접근 안내 배너**:
  - 역할 변경 시 해당 회원의 Refresh Token이 즉시 무효화되며, 기존 Access Token 만료(최대 30분) 후 완전 반영됨을 명시
- **회원 역할 변경 카드**:
  - 회원 ID 입력 필드 (`type="number"`, 필수)
  - 변경할 역할 선택 라디오/셀렉트: 일반 회원(`ROLE_USER`), 점주(`ROLE_OWNER`), 관리자(`ROLE_ADMIN`)
  - "역할 변경 적용" 버튼 (로딩 스피너 및 비활성화 처리)
- **결과 카드**:
  - 변경 성공 시 회원의 닉네임, 아이디, 변경된 역할 뱃지, 가입일 노출
- **안전 장치**:
  - 현재 로그인한 본인 ID를 입력할 경우 프론트엔드에서 즉시 "본인 권한은 변경할 수 없습니다" 경고 및 제출 버튼 비활성화

---

## 6. i18n 키 명세 (`admin.*`)

| 키 | ko | ja |
|---|---|---|
| `admin.title` | 회원 역할 관리 | ユーザー権限管理 |
| `admin.memberIdLabel` | 회원 번호 (ID) | ユーザー番号 (ID) |
| `admin.roleLabel` | 변경할 권한 | 変更する権限 |
| `admin.roleUser` | 일반 회원 (ROLE_USER) | 一般ユーザー (ROLE_USER) |
| `admin.roleOwner` | 점주 (ROLE_OWNER) | 店舗オーナー (ROLE_OWNER) |
| `admin.roleAdmin` | 관리자 (ROLE_ADMIN) | 管理者 (ROLE_ADMIN) |
| `admin.submitChange` | 권한 변경 적용 | 権限変更を適用 |
| `admin.changeSuccess` | 역할이 성공적으로 변경되었습니다. | 権限が正常に変更されました。 |
| `admin.tokenNotice` | 대상 회원의 Refresh Token이 폐기되어 다음 로그인/갱신 시 새 권한이 적용됩니다. | 対象ユーザーのRefresh Tokenが無効化され、次回ログイン/更新時に新しい権限が適用されます。 |
| `admin.selfChangeWarning` | 본인 계정의 역할은 변경할 수 없습니다. | 自身のアカウントの権限は変更できません。 |
| `nav.manageRoles` | 역할 관리 | 権限管理 |

---

## 7. 수용 기준

- [ ] 비로그인 또는 일반 회원/점주 계정이 `/admin/members` 접속 시 홈(`/`) 또는 로그인으로 리다이렉트된다
- [ ] 관리자(`ROLE_ADMIN`) 계정 로그인 시 헤더에 "역할 관리" 링크가 노출되고 페이지에 정상 접속된다
- [ ] 회원 ID와 새 역할을 지정하여 제출 시 `PATCH /members/:memberId/role`이 호출되고 결과 카드가 렌더링된다
- [ ] 본인 ID를 입력하고 제출 시도시 프론트 검증 및 백엔드 에러(`CANNOT_CHANGE_OWN_ROLE`)가 올바르게 차단·표시된다
- [ ] 존재하지 않는 회원 ID 입력 시 `MEMBER_NOT_FOUND` i18n 에러 메시지가 표시된다
