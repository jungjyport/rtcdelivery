# RTC Delivery — Internationalization (i18n) 정책

## 1. 지원 언어

| 코드 | 언어 | 상태 |
|---|---|---|
| `ko` | 한국어 | ✅ 기본 locale |
| `ja` | 日本語 | ✅ 지원 |

향후 `en`, `zh` 등 추가 가능.

---

## 2. 번역 유형 분류

이 프로젝트에서는 **4가지 유형의 텍스트**를 명확히 구분하여 처리합니다.

### A. Static UI — Nuxt i18n

사용자 인터페이스의 고정 텍스트.

**예시**: 로그인, 회원가입, 주문, 결제, 검색, 네비게이션, 버튼, 에러 메시지

**처리 방법**: `@nuxtjs/i18n` 모듈, JSON 번역 파일

```vue
{{ $t('common.login') }}
```

### B. Dynamic Domain Data — Translation Table

백엔드 DB에서 가져오는 비즈니스 데이터.

**예시**: 음식점 이름, 음식점 설명, 메뉴 이름, 메뉴 설명

**처리 방법**: DB Translation Table + Translation Service (향후 구현)

```
restaurant_translation
├── restaurant_id
├── locale
├── name
└── description
```

### C. System Error / Status Code — Code 기반 + Nuxt i18n

백엔드가 전달하는 시스템 메시지.

**처리 방법**: 백엔드는 code/enum만 전달, 프론트엔드가 i18n으로 표시

```json
// Backend Response
{ "errorCode": "RESTAURANT_NOT_FOUND" }
```

```json
// ko.json
{ "error": { "RESTAURANT_NOT_FOUND": "음식점을 찾을 수 없습니다." } }

// ja.json
{ "error": { "RESTAURANT_NOT_FOUND": "店舗が見つかりません。" } }
```

```json
// Backend Response
{ "status": "PREPARING" }
```

```json
// ko.json
{ "order": { "status": { "PREPARING": "조리 중" } } }

// ja.json
{ "order": { "status": { "PREPARING": "調理中" } } }
```

### D. User Generated Content (UGC) — Translation Service + AI

사용자가 직접 작성한 콘텐츠.

**예시**: 리뷰, 문의 내용

**처리 방법**: 사용자 요청 시 Translation Service → AI API → 캐싱

> 상세 설계는 [translation-system.md](./translation-system.md)를 참조하세요.

---

## 3. Nuxt i18n 설정 상세

### 모듈

```
@nuxtjs/i18n
```

### 설정 (nuxt.config.ts)

```typescript
i18n: {
  locales: [
    { code: 'ko', name: '한국어', file: 'ko.json' },
    { code: 'ja', name: '日本語', file: 'ja.json' },
  ],
  defaultLocale: 'ko',
  lazy: true,
  langDir: 'i18n/locales',
  strategy: 'no_prefix',
  detectBrowserLanguage: {
    useCookie: true,
    cookieKey: 'i18n_locale',
    redirectOn: 'root',
    alwaysRedirect: false,
  },
}
```

### Strategy: `no_prefix`

- URL에 `/ko/`, `/ja/` prefix를 붙이지 않음
- locale 정보는 **cookie** (`i18n_locale`)에 저장
- SEO가 중요해지면 `prefix_except_default`로 전환 가능

### SSR 동작

- 서버 사이드에서 cookie의 locale 값을 읽어 적절한 번역 적용
- `detectBrowserLanguage` 설정으로 첫 방문 시 브라우저 언어 감지

---

## 4. 번역 파일 구조

```
i18n/
├── i18n.config.ts          # vue-i18n 상세 설정
└── locales/
    ├── ko.json             # 한국어
    └── ja.json             # 일본어
```

### 번역 Key Naming Convention

의미 기반의 안정적인 key를 사용합니다. 화면 문장 자체를 key로 사용하지 않습니다.

| Prefix | 용도 | 예시 |
|---|---|---|
| `common.*` | 공통 UI 요소 | `common.login`, `common.search` |
| `nav.*` | 네비게이션 | `nav.home`, `nav.findRestaurants` |
| `auth.*` | 인증 관련 | `auth.emailLabel`, `auth.passwordRequired` |
| `hero.*` | 메인 히어로 섹션 | `hero.heading1`, `hero.badge` |
| `category.*` | 음식 카테고리 | `category.korean`, `category.japanese` |
| `feature.*` | 서비스 특징 | `feature.realtime.title` |
| `cta.*` | CTA 섹션 | `cta.title`, `cta.orderNow` |
| `footer.*` | 푸터 | `footer.service`, `footer.terms` |
| `seo.*` | SEO 메타 | `seo.title`, `seo.description` |
| `restaurant.*` | 음식점 관련 UI | `restaurant.rating`, `restaurant.deliveryFee` |
| `menu.*` | 메뉴 관련 UI | `menu.addToCart`, `menu.soldOut` |
| `order.*` | 주문 관련 | `order.status.PREPARING` |
| `delivery.*` | 배달 관련 | `delivery.estimatedTime` |
| `payment.*` | 결제 관련 | `payment.method.card` |
| `error.*` | 에러 코드 | `error.RESTAURANT_NOT_FOUND` |
| `locale.*` | 언어 이름 | `locale.ko`, `locale.ja` |

### Key 작성 규칙

1. **점(.) 표기법으로 계층 구분**: `order.status.PREPARING`
2. **camelCase 사용**: `findRestaurants`, `lateNight`
3. **백엔드 enum/code는 대문자 그대로**: `order.status.PENDING`
4. **동사보다 명사/형용사 선호**: `common.search` (O), `common.doSearch` (X)
5. **페이지별 섹션은 별도 prefix**: `hero.*`, `cta.*`

---

## 5. Locale 처리

### 기본 Locale

`ko` (한국어)

### Locale 변경

- Nav bar의 언어 선택 드롭다운 UI
- `setLocale()` 호출 시 즉시 전체 UI 텍스트 변경
- cookie에 저장 → 새로고침/재방문 시 유지

### SSR에서의 Locale

1. 서버가 요청의 cookie에서 `i18n_locale` 읽음
2. 해당 locale로 서버사이드 렌더링
3. 클라이언트 하이드레이션 후에도 동일 locale 유지

### 향후 언어 추가 방법

1. `i18n/locales/` 에 새 JSON 파일 추가 (예: `en.json`)
2. `nuxt.config.ts`의 `i18n.locales` 배열에 추가
3. `availableLocales` (언어 선택 UI)에 추가
4. **기존 코드 수정 불필요** — key 구조가 동일하므로 번역 파일만 추가

---

## 6. 절대 하지 않는 것

```vue
<!-- ❌ WRONG -->
{{ locale === 'ja' ? '注文' : '주문' }}
```

```vue
<!-- ✅ CORRECT -->
{{ $t('common.order') }}
```

모든 사용자 노출 텍스트는 반드시 i18n key를 통해 표시합니다.
