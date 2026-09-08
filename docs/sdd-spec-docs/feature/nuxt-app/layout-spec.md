# nuxt-app — 공통 레이아웃 (글로벌 헤더 · 푸터)

> **상태**: 구현 완료
> **대상**: `frontend/nuxt-app`
> **관련**: [api-client-spec.md §6.6.1](./api-client-spec.md#661-복구-중-ui-렌더링-규칙-깜빡임-방지) · [auth-pages-spec.md](./auth-pages-spec.md)

로그인·회원가입·세션 복구는 이미 있다. 이 문서는 **헤더가 `authStore`를 어떻게 그리는지**와 레이아웃 구조만 정한다.

---

## 1. 범위

- `default` 레이아웃에 글로벌 헤더 + 푸터를 둔다. 모든 페이지에 적용된다 (로그인/회원가입 포함).
- 헤더 인증 슬롯은 `isLoggedIn` 단독 분기 금지. **3-state**.
- 메인(`index.vue`)에 있던 네비/푸터를 걷어 내고 공통 컴포넌트로 옮긴다.
- 인증 페이지 `AuthFormShell`의 중복 로고는 제거한다. 브랜드는 헤더가 담당한다.

이번에 하지 않는 것: 마이페이지, 주문내역 라우트, 장바구니 배지, 모바일 햄버거, OAuth 버튼.

---

## 2. 산출물

```
frontend/nuxt-app/app/
├── layouts/default.vue
├── components/common/
│   ├── AppHeader.vue
│   ├── AppFooter.vue
│   └── LocaleSwitcher.vue
└── stores/authStore.ts          # isRestoring 초깃값만 조정
```

`i18n/locales/{ko,ja}.json`에 `header.*` 추가.

---

## 3. 레이아웃 구조

```
default.vue
  AppHeader          ← position: fixed, h-16, z-50
  <main flex-1>      ← 페이지 슬롯. 헤더가 fixed이므로 페이지가 pt-16로 본문을 내린다
    <slot />
  </main>
  AppFooter
```

- Tailwind 유틸리티만 사용. `<style>` 금지.
- 로고 클릭 → `/`.
- 가운데 네비(홈 / 맛집 찾기 / 주문내역)는 기존 메인과 동일. 아직 없는 페이지는 `#`.
- 우측: 언어 전환 → 인증 슬롯.
- 푸터는 메인의 카피·컬럼 구성을 그대로 옮긴다. 링크 페이지가 없으면 `#`.

---

## 4. 인증 슬롯 3-state

판정 순서는 항상 이것이다.

| 우선 | 조건 | 렌더링 |
|---|---|---|
| 1 | `authStore.isRestoring` | 로그인/회원가입 버튼과 같은 크기의 pulse 스켈레톤. 텍스트 없음 |
| 2 | `authStore.isLoggedIn` | `user.nickname`이 있으면 `{name}님` + 로그아웃. nickname이 아직 없으면 로그아웃만 |
| 3 | 그 외 | 로그인 링크 + 회원가입 버튼 (기존 메인과 동일) |

```vue
<template v-if="authStore.isRestoring"> …skeleton… </template>
<template v-else-if="authStore.isLoggedIn"> …nickname + logout… </template>
<template v-else> …login + signup… </template>
```

- 스켈레톤 너비/높이는 비로그인 버튼과 맞춰 CLS를 막는다.
- 로그아웃은 `useAuth().logout()`만 호출한다. 헤더가 `$api`를 직접 치지 않는다.
- 언어 전환은 인증 상태와 무관하게 항상 보인다.

### 4.1 `isRestoring` 초깃값

Access Token은 메모리에만 있으므로 SSR은 로그인 여부를 모른다.
`isRestoring`의 초깃값을 **`true`** 로 둔다. 첫 `restoreSession()`이 끝나면 `false`.

이렇게 해야 서버 HTML에 로그인 버튼이 찍혔다가 복구 후 사라지는 깜빡임이 없다.
화면은 "빈 슬롯 → 채워짐"만 일어난다.

`restoreSession()`이 이미 토큰을 가지고 있어 즉시 return 할 때도 `isRestoring = false`를 명시한다.

---

## 5. i18n

| 키 | ko | ja |
|---|---|---|
| `header.greeting` | `{name}님` | `{name}さん` |
| `header.authSlot` | 계정 | アカウント |

`common.login` / `common.signup` / `common.logout` / `nav.*` / `footer.*` 는 기존 키를 재사용한다.

---

## 6. 수용 기준

- [ ] 메인·로그인·회원가입 페이지에 같은 헤더/푸터가 보인다
- [ ] 비로그인: 로그인 / 회원가입 버튼. 클릭 시 각 인증 페이지
- [ ] 로그인 직후: 헤더가 닉네임 + 로그아웃으로 바뀐다 (새로고침 없이)
- [ ] 새로고침: 복구 동안 스켈레톤 → 복구 후 로그인 슬롯. 로그인 버튼이 잠깐 나타났다가 사라지지 않는다
- [ ] 로그아웃: `POST /auth/logout` 후 로그인 페이지. 헤더는 비로그인 슬롯
- [ ] 언어 전환은 헤더에서 동작하고, 인증 슬롯 문구도 ko/ja로 바뀐다
- [ ] Access Token이 localStorage / 비-httpOnly 쿠키에 생기지 않는다
