# nuxt-app — 회원가입 · 로그인 화면 스펙

> **상태**: Spec (미구현)
> **대상**: `frontend/nuxt-app`
> **계약 방식**: 문서 기반. API 요청/응답은 [auth-jwt-spec.md §3](../member-auth-service/auth-jwt-spec.md#3-엔드포인트)을 따른다.
> 필드 검증의 진실의 원천은 member-auth-service의 `SignupRequest` / `LoginRequest` Bean Validation이다 (Swagger Code-first).

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [auth-jwt-spec.md §3](../member-auth-service/auth-jwt-spec.md#3-엔드포인트) | 회원가입/로그인 요청·응답·에러 코드 |
| [api-client-spec.md](./api-client-spec.md) | `$api`, 토큰 저장, 401 갱신, `restoreSession()` |
| [error-handling.md §2.3](../../../error-handling.md#23-검증-실패-응답) | `VALIDATION_ERROR`의 `data` 필드 처리 |
| [internationalization.md](../../../internationalization.md) | `auth.*` / `error.*` 키 규칙 |

백엔드 회원가입·로그인은 구현 완료다. 이 문서는 **프론트 화면과 클라이언트 검증**만 다룬다.

---

## 1. 범위

### 1.1 이번에 구현하는 것

- `/auth/signup` 회원가입 페이지 (클라이언트 검증 + `POST /auth/signup`)
- `/auth/login` 로그인 페이지 (`POST /auth/login` → `authStore.setAuth`)
- 게스트 라우트 미들웨어 (이미 로그인된 사용자는 인증 화면 진입 금지)
- 로그인/회원가입/로그아웃을 캡슐화하는 `useAuth` composable
- 인증 화면 i18n (`auth.*`) 및 인증 에러 코드 (`error.*`) 보강
- 기존 `types/index.ts`의 낡은 로그인 타입(`email` 기반)을 실제 계약에 맞게 교체

### 1.2 이번에 구현하지 않는 것

| 항목 | 사유 |
|---|---|
| OAuth (Google / Kakao) | `tasks.md`에 별도 항목. 스펙 미작성 |
| 비밀번호 재설정 / 이메일 인증 | 백엔드 미구현 |
| 공통 헤더의 3-state 인증 UI | 레이아웃 태스크. 이번 화면에서 로그인 버튼만 `/auth/login`으로 연결하면 충분 |
| Access Token localStorage 저장 | [api-client-spec.md §8](./api-client-spec.md#8-인증-스토어-계약-storesauthts)에서 금지 |

---

## 2. 라우트와 가드

| 경로 | 파일 | 미들웨어 | 설명 |
|---|---|---|---|
| `/auth/login` | `app/pages/auth/login.vue` | `guest` | 로그인 |
| `/auth/signup` | `app/pages/auth/signup.vue` | `guest` | 회원가입 |
| (기존) 인증 필요 페이지 | — | `auth` | 미로그인 시 `/auth/login`으로 보냄. **이미 구현됨** |

### 2.1 `guest` 미들웨어 (`app/middleware/guest.ts`)

로그인된 사용자가 로그인/회원가입 화면에 머물 이유가 없다.

```
1. import.meta.server 이면 즉시 return
2. authStore.restoreSession() 을 await  (auth 미들웨어와 동일한 규칙)
3. isLoggedIn 이면 navigateTo('/')  — query.redirect 가 있으면 그 경로
```

`auth` 미들웨어와 마찬가지로 **판정 전에 세션 복구를 await**한다. 빼면 새로고침 직후 로그인 화면이 한 번 깜빡인 뒤 홈으로 튕긴다.

### 2.2 로그인 후 이동

| 진입 | 성공 후 이동 |
|---|---|
| 직접 `/auth/login` | `/` |
| `auth` 미들웨어가 `navigateTo('/auth/login')` | `/` (현 미들웨어는 redirect 쿼리를 넘기지 않음) |
| 회원가입 성공 | `/auth/login?registered=1` (토큰 없음. 로그인 유도) |

후속으로 `auth` 미들웨어가 `?redirect=`를 붙이도록 바꿔도 이 화면 계약은 깨지지 않는다. 로그인 페이지는 `route.query.redirect`가 있고 같은 오리진의 상대 경로일 때만 사용한다. `http://` 로 시작하는 값은 무시한다 (오픈 리다이렉트).

---

## 3. API 계약 (프론트가 호출하는 것)

`$api`의 `baseURL`이 이미 `/api/v1`을 포함하므로 경로에 버전을 중복하지 않는다.
로그인/회원가입은 `AUTH_EXEMPT_PATHS`에 있어 401이 나와도 토큰 갱신을 시도하지 않는다. **호출 시 `skipAuth: true`, `skipRefresh: true`를 명시한다.**

### 3.1 회원가입 — `POST /auth/signup`

요청/응답/에러는 [auth-jwt-spec.md §3.1](../member-auth-service/auth-jwt-spec.md#31-회원가입--post-apiv1authsignup)과 동일하다.

**성공 `201`**: `MemberResponse`. 토큰은 없다. 스토어를 건드리지 않는다.

**에러**

| HTTP | `code` | 화면 처리 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 폼 상단 일반 메시지 + `data`의 키로 해당 필드 강조 |
| 409 | `DUPLICATE_USERNAME` | `username` 필드 에러 |
| 409 | `DUPLICATE_EMAIL` | `email` 필드 에러 |
| 409 | `DUPLICATE_NICKNAME` | `nickname` 필드 에러 |

`data`의 값(서버 기본 메시지)은 **사용자에게 그대로 보여주지 않는다** ([error-handling.md §2.3](../../../error-handling.md#23-검증-실패-응답)). 문장은 전부 i18n.

### 3.2 로그인 — `POST /auth/login`

[auth-jwt-spec.md §3.2](../member-auth-service/auth-jwt-spec.md#32-로그인--post-apiv1authlogin).

성공 `data`:

```ts
interface LoginResult {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: AuthUser
}
```

`AuthUser`는 이미 `stores/authStore.ts`에 있다. `user`를 클라이언트가 재구성하지 않는다.

Refresh Token은 httpOnly 쿠키로 내려온다. JS가 읽거나 저장하지 않는다. `$api`가 `credentials: 'include'`이므로 추가 작업 없다.

**에러**

| HTTP | `code` | 화면 처리 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 필드 강조 |
| 401 | `INVALID_CREDENTIALS` | 폼 상단 메시지. 아이디/비밀번호를 구분하지 않음 |
| 403 | `MEMBER_INACTIVE` | 폼 상단 메시지 |

성공 후:

```
authStore.setAuth(result.accessToken, result.user)
navigateTo(safeRedirect || '/')
```

`setToken`만 호출하고 `user`를 비우지 않는다. 헤더 등이 `user.nickname`을 바로 쓸 수 있어야 한다.

### 3.3 로그아웃 (이 화면의 버튼은 없지만 composable에 둔다)

`POST /auth/logout` (`skipAuth: true`, `skipRefresh: true`) → 성공/실패와 관계없이 `clearAuth()` → `/auth/login`.
백엔드는 항상 200을 반환한다. 네트워크 오류여도 클라이언트 세션은 지운다.

---

## 4. 클라이언트 검증

서버에 보내기 **전에** 같은 규칙을 적용한다. 불필요한 왕복을 줄이고, 서버 `VALIDATION_ERROR`와 메시지가 어긋나지 않게 한다.

규칙은 **현재 DTO 어노테이션**과 맞춘다. `auth-jwt-spec.md`의 비밀번호 "영문·숫자 or 특수문자" 문구는 DTO에 `@Pattern`이 없어 강제하지 않는다. 프론트가 더 엄격하면 서버는 받을 값을 클라이언트가 거절하게 된다.

### 4.1 회원가입

| 필드 | 필수 | 규칙 | i18n 키 (실패 시) |
|---|---|---|---|
| `username` | O | 4~20자, `^[a-z0-9_]+$` | `auth.validation.usernameRequired` / `usernameLength` / `usernamePattern` |
| `password` | O | 4~64자 | `auth.validation.passwordRequired` / `passwordLength` |
| `passwordConfirm` | O | `password`와 동일. **서버로 보내지 않음** | `auth.validation.passwordConfirmRequired` / `passwordMismatch` |
| `nickname` | O | 2~20자, trim 후 빈 문자열 불가 | `auth.validation.nicknameRequired` / `nicknameLength` |
| `email` | X | 비어 있으면 생략(`undefined`). 값이 있으면 이메일 형식, 최대 100자 | `auth.validation.emailInvalid` / `emailLength` |

빈 `email`은 `""`로 보내지 않는다. 백엔드 `@Email`이 빈 문자열을 거절할 수 있다.

검증 함수는 `app/utils/auth-validation.ts`에 순수 함수로 둔다. Vue/Nuxt에 의존하지 않아 테스트하기 쉽다.

```ts
export interface SignupForm {
  username: string
  password: string
  passwordConfirm: string
  nickname: string
  email: string
}

export type SignupField = 'username' | 'password' | 'passwordConfirm' | 'nickname' | 'email'

/** 통과하면 빈 객체. 실패하면 필드 → i18n 키 */
export function validateSignup(form: SignupForm): Partial<Record<SignupField, string>>
```

submit 시 전체 검증. 각 필드는 `blur`에서도 해당 필드만 재검증한다.

### 4.2 로그인

| 필드 | 필수 | 규칙 |
|---|---|---|
| `username` | O | 공백만 불가 (길이/패턴은 서버에 맡긴다. 로그인 실패를 검증 실패로 위장하지 않음) |
| `password` | O | 공백만 불가 |

아이디 형식이 틀려도 "존재하지 않는 계정"처럼 보이지 않게, 로그인 폼에서는 패턴을 강제하지 않는다.

---

## 5. 산출물

```
frontend/nuxt-app/
├── app/
│   ├── pages/auth/
│   │   ├── login.vue              # [NEW]
│   │   └── signup.vue             # [NEW]
│   ├── components/auth/
│   │   └── AuthFormShell.vue      # [NEW] 공통 카드 레이아웃
│   ├── composables/
│   │   └── useAuth.ts             # [NEW]
│   ├── middleware/
│   │   ├── auth.ts                # 기존
│   │   └── guest.ts               # [NEW]
│   ├── stores/authStore.ts        # 기존 — 이 작업에서 시그니처 변경 없음
│   ├── types/index.ts             # [MODIFY] 낡은 Login/Signup 타입 교체
│   └── utils/auth-validation.ts   # [NEW]
└── i18n/locales/
    ├── ko.json                    # [MODIFY] auth.*, error.* 보강
    └── ja.json
```

공통 헤더/푸터는 아직 없으므로 인증 페이지는 `AuthFormShell` 안에서 자체 헤더(로고 → `/`)만 둔다. 메인 페이지(`index.vue`)의 로그인/회원가입 버튼은 각각 `/auth/login`, `/auth/signup`으로 연결한다. 레이아웃 구조는 바꾸지 않는다.

### 5.1 `useAuth`

```ts
export function useAuth() {
  const isSubmitting = ref(false)
  const formError = ref<string | null>(null)      // i18n 키 또는 번역된 문장. 구현은 키를 넣고 템플릿에서 t()

  function login(payload: { username: string; password: string }): Promise<void>
  function signup(payload: { username: string; password: string; nickname: string; email?: string }): Promise<void>
  function logout(): Promise<void>
}
```

- 페이지는 `$api`를 직접 호출하지 않는다.
- `isSubmitting`이 true인 동안 제출 버튼을 비활성화한다 (더블 서브밋).
- 실패 시 `ApiError`를 잡아 `formError`에 `err.i18nKey`를 넣는다. 필드 에러는 페이지가 `err.data`의 키로 매핑한다.

### 5.2 타입 (`types/index.ts`)

아래 낡은 정의를 삭제하거나 auth 계약으로 교체한다. 카탈로그/주문 타입은 건드리지 않는다.

```ts
// 삭제 대상 (현재 파일)
LoginRequest { email, password }
LoginResponse { accessToken, refreshToken, user }
SignupRequest { email, password, name, phone, role }
User { email, name, phone, ... }
```

교체:

```ts
export type { AuthUser, AuthProvider } from '~/stores/authStore'

export interface LoginRequest {
  username: string
  password: string
}

export interface SignupRequest {
  username: string
  password: string
  nickname: string
  email?: string
}
```

`refreshToken`을 응답 타입에 넣지 않는다. 쿠키로만 온다.

---

## 6. UI

메인 페이지와 같은 톤을 유지한다. Tailwind 유틸리티만 사용한다 (`<style>` 금지).

- 배경: `bg-surface-50`, 히어로와 같은 primary/accent 그라데이션 블러
- 카드: 흰 배경, `rounded-2xl`, 얕은 섀도, 최대 너비 `max-w-md`
- 로고: 메인과 동일한 RTC Delivery 마크. 클릭 시 `/`
- 입력: 라벨 + 텍스트 필드. 에러 시 보더 `border-red-500`, 필드 아래 `$t(fieldErrorKey)`
- 비밀번호: type 전환 토글 (보기/숨기기). 아이콘은 Heroicons outline SVG 인라인
- 1차 버튼: 기존 `btn-primary`
- 하단 링크: 로그인 페이지 → 회원가입, 회원가입 페이지 → 로그인

로그인 페이지는 `?registered=1`이면 상단에 성공 알림(`auth.signupSuccess`)을 보여준다.

접근성:

- 각 input에 `<label for>` 연결
- 폼 `novalidate` (브라우저 기본 팝업 대신 우리 검증)
- 제출 중 버튼 `aria-busy`
- 폼 상단 에러는 `role="alert"`

모바일(`sm` 미만)에서도 카드가 화면 폭을 넘지 않게 `px-4`를 둔다.

---

## 7. i18n 키

`auth.emailLabel` 같은 이메일-로그인 전제 키는 쓰지 않는다. 이 서비스의 로컬 로그인은 **username**이다.

### 7.1 `auth.*` (신규)

| 키 | ko | ja |
|---|---|---|
| `auth.loginTitle` | 로그인 | ログイン |
| `auth.signupTitle` | 회원가입 | 会員登録 |
| `auth.usernameLabel` | 아이디 | ユーザーID |
| `auth.passwordLabel` | 비밀번호 | パスワード |
| `auth.passwordConfirmLabel` | 비밀번호 확인 | パスワード（確認） |
| `auth.nicknameLabel` | 닉네임 | ニックネーム |
| `auth.emailLabel` | 이메일 (선택) | メールアドレス（任意） |
| `auth.submitLogin` | 로그인 | ログイン |
| `auth.submitSignup` | 가입하기 | 登録する |
| `auth.goSignup` | 계정이 없으신가요? 회원가입 | アカウントをお持ちでない方は会員登録 |
| `auth.goLogin` | 이미 계정이 있으신가요? 로그인 | すでにアカウントをお持ちの方はログイン |
| `auth.signupSuccess` | 가입이 완료되었습니다. 로그인해 주세요. | 登録が完了しました。ログインしてください。 |
| `auth.showPassword` | 비밀번호 보기 | パスワードを表示 |
| `auth.hidePassword` | 비밀번호 숨기기 | パスワードを隠す |
| `auth.validation.usernameRequired` | 아이디를 입력해 주세요. | ユーザーIDを入力してください。 |
| `auth.validation.usernameLength` | 아이디는 4~20자여야 합니다. | ユーザーIDは4〜20文字です。 |
| `auth.validation.usernamePattern` | 아이디는 영문 소문자, 숫자, 밑줄(_)만 사용할 수 있습니다. | ユーザーIDは英小文字・数字・アンダースコアのみ使用できます。 |
| `auth.validation.passwordRequired` | 비밀번호를 입력해 주세요. | パスワードを入力してください。 |
| `auth.validation.passwordLength` | 비밀번호는 4~64자여야 합니다. | パスワードは4〜64文字です。 |
| `auth.validation.passwordConfirmRequired` | 비밀번호 확인을 입력해 주세요. | 確認用パスワードを入力してください。 |
| `auth.validation.passwordMismatch` | 비밀번호가 일치하지 않습니다. | パスワードが一致しません。 |
| `auth.validation.nicknameRequired` | 닉네임을 입력해 주세요. | ニックネームを入力してください。 |
| `auth.validation.nicknameLength` | 닉네임은 2~20자여야 합니다. | ニックネームは2〜20文字です。 |
| `auth.validation.emailInvalid` | 올바른 이메일 형식이 아닙니다. | メールアドレスの形式が正しくありません。 |
| `auth.validation.emailLength` | 이메일은 최대 100자까지 가능합니다. | メールアドレスは100文字以内です。 |

### 7.2 `error.*` (보강)

기존 `INVALID_CREDENTIALS` 문구가 "이메일 또는 비밀번호"로 되어 있다. **아이디** 기준으로 고친다.

| 키 | ko | ja |
|---|---|---|
| `error.INVALID_CREDENTIALS` | 아이디 또는 비밀번호가 올바르지 않습니다. | ユーザーIDまたはパスワードが正しくありません。 |
| `error.DUPLICATE_USERNAME` | 이미 사용 중인 아이디입니다. | すでに使用されているユーザーIDです。 |
| `error.DUPLICATE_NICKNAME` | 이미 사용 중인 닉네임입니다. | すでに使用されているニックネームです。 |
| `error.MEMBER_INACTIVE` | 비활성화된 계정입니다. | 無効化されたアカウントです。 |

`error.DUPLICATE_EMAIL`, `error.VALIDATION_ERROR`는 이미 있다. 번역 키가 없으면 `error.UNKNOWN_ERROR`로 fallback ([api-client-spec.md §9](./api-client-spec.md#9-i18n-에러-표출)).

---

## 8. 에러 표시 규칙

```
1. 클라이언트 검증 실패
   → 해당 필드 아래 auth.validation.* 
   → API를 호출하지 않음

2. API 4xx (ApiError)
   → 폼 상단: t(err.i18nKey)
   → DUPLICATE_* / VALIDATION_ERROR: data의 키와 같은 필드에도 표시
     VALIDATION_ERROR의 data 값은 무시하고, 필드는 강조만 하거나
     이미 클라이언트에서 막힌 규칙이면 auth.validation.* 를 재사용

3. 네트워크 / 타임아웃
   → 폼 상단: t('error.NETWORK_ERROR') 등
```

비밀번호 불일치와 아이디 없음을 다른 문장으로 나누지 않는다. 백엔드가 `INVALID_CREDENTIALS` 하나로 내리는 이유를 프론트가 깨면 안 된다.

---

## 9. 구현 순서

| 단계 | 작업 |
|---|---|
| 1 | i18n `auth.*` / `error.*` (ko, ja) |
| 2 | `types/index.ts` 교체, `auth-validation.ts` |
| 3 | `useAuth.ts` |
| 4 | `middleware/guest.ts` |
| 5 | `AuthFormShell.vue` + `login.vue` + `signup.vue` |
| 6 | `index.vue` 로그인/회원가입 버튼을 `NuxtLink`로 연결 |
| 7 | 아래 수용 기준으로 수동 확인 |

스토어(`restoreSession`, `setAuth`)와 API Client는 이미 계약대로 동작하므로 이 작업에서 수정하지 않는다. 부족한 점이 있으면 [api-client-spec.md](./api-client-spec.md)를 먼저 고친다.

---

## 10. 수용 기준

- [ ] `/auth/signup`에서 유효한 값으로 가입하면 201 후 `/auth/login?registered=1`로 이동하고, **로그인 상태가 되지 않는다**
- [ ] 가입 직후 로그인하면 Access Token이 Pinia에만 있고 localStorage/쿠키에 `accessToken`이 없다. `refreshToken` 쿠키는 `HttpOnly`이다
- [ ] 잘못된 비밀번호로 로그인하면 `error.INVALID_CREDENTIALS`가 언어에 맞게 보이고, 아이디 존재 여부가 드러나지 않는다
- [ ] 중복 아이디/닉네임/이메일은 해당 필드에 `DUPLICATE_*`가 매핑된다
- [ ] 클라이언트 검증에 실패하면 네트워크 요청이 나가지 않는다 (DevTools)
- [ ] 이미 로그인한 채 `/auth/login`에 들어가면 홈으로 보내진다 (새로고침해도 깜빡인 뒤 남지 않음)
- [ ] 비로그인으로 인증 필요 페이지에 들어가면 `/auth/login`으로 보내진다 (기존 `auth` 미들웨어)
- [ ] 언어를 ja로 바꾸면 라벨·버튼·에러가 일본어이다
- [ ] 회원가입 요청 body에 `passwordConfirm`이 없다
- [ ] 이메일 미입력 시 body에 `email: ""`이 없다

---

## 11. 미결 사항

| # | 항목 | 현재 결정 |
|---|---|---|
| O1 | 비밀번호 복잡도 `@Pattern` | DTO에 없으므로 프론트도 길이만 검사. 백엔드가 규칙을 추가하면 이 스펙 4.1과 DTO를 같이 바꾼다 |
| O2 | `auth` 미들웨어의 `redirect` 쿼리 | 이번 범위에서 미들웨어는 수정하지 않는다. 로그인 페이지만 쿼리를 읽을 수 있게 준비한다 |
| O3 | CAPTCHA / rate limit UI | Gateway Rate Limiting 이후 |
