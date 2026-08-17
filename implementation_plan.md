# Phase 1 — i18n 도입 + 설계 문서 작성

기존 프로젝트를 분석 완료했습니다. 최소 변경 원칙으로 작업합니다.

---

## 프로젝트 분석 결과

### Frontend 현황
| 항목 | 값 |
|---|---|
| Nuxt 버전 | 4.5.2 |
| Vue | 3.5.41 |
| 기존 모듈 | `@pinia/nuxt`, `@nuxtjs/tailwindcss` |
| 상태 관리 | Pinia (auth, cart 스토어) |
| API | Axios 플러그인 (JWT 인터셉터) |
| 디렉토리 구조 | `app/` 기반 (Nuxt 4 구조) |
| 메인 페이지 | `app/pages/index.vue` — 311줄, 하드코딩된 한국어 텍스트 다수 |

### Backend 현황 (6개 서비스)
| 서비스 | 포트 | 상태 |
|---|---|---|
| discovery-service (Eureka) | 8761 | 스캐폴딩만 완료 |
| api-gateway | 8080 | 스캐폴딩만 완료 |
| food-catalog-service | 8081 | 스캐폴딩만 완료 |
| member-auth-service | 8082 | 스캐폴딩만 완료 |
| order-service | 8083 | 스캐폴딩만 완료 |
| payment-service | 8084 | 스캐폴딩만 완료 |

### 기존 문제점 (`utils/format.ts`)
- `getOrderStatusLabel()`에 하드코딩된 한국어 매핑이 있음 → i18n 전환 대상

---

## Proposed Changes

### 1. `@nuxtjs/i18n` 설치 및 설정

#### [MODIFY] [nuxt.config.ts](file:///c:/codes2024/rtcdelivery/frontend/nuxt-app/nuxt.config.ts)
- `modules`에 `@nuxtjs/i18n` 추가
- i18n 설정: `defaultLocale: 'ko'`, `locales: ['ko', 'ja']`
- Strategy: `no_prefix` (URL에 locale prefix 없음, cookie로 유지)
- `detectBrowserLanguage`: cookie 기반 자동 감지

#### [NEW] [i18n/locales/ko.json](file:///c:/codes2024/rtcdelivery/frontend/nuxt-app/i18n/locales/ko.json)
한국어 번역 파일. 아래 key 구조 사용:
```
common.*      — 공통 UI (홈, 검색, 로그인 등)
nav.*         — 내비게이션
hero.*        — 히어로 섹션
category.*    — 카테고리
feature.*     — 특징 섹션
cta.*         — CTA 섹션
footer.*      — 푸터
auth.*        — 인증 관련
order.*       — 주문 상태
error.*       — 에러 코드 (백엔드 코드 기반)
```

#### [NEW] [i18n/locales/ja.json](file:///c:/codes2024/rtcdelivery/frontend/nuxt-app/i18n/locales/ja.json)
일본어 번역 파일. 동일한 key 구조.

#### [NEW] [i18n/i18n.config.ts](file:///c:/codes2024/rtcdelivery/frontend/nuxt-app/i18n/i18n.config.ts)
i18n 상세 설정 파일 (nuxt.config.ts에서 분리).

---

### 2. 메인 페이지 i18n 적용

#### [MODIFY] [index.vue](file:///c:/codes2024/rtcdelivery/frontend/nuxt-app/app/pages/index.vue)
변경 범위 (최소 수정):
- 모든 하드코딩 텍스트를 `$t('key')` 호출로 교체
- `categories` 배열의 `name`을 i18n key로 변경: `$t('category.korean')` 등
- `features` 배열의 `title`, `description`을 i18n key로 변경
- `useHead()`의 title/description도 i18n 적용
- **HTML 구조, CSS, 애니메이션은 변경하지 않음**

#### 언어 전환 UI 추가
- Nav bar 우측에 **언어 선택 버튼** 추가 (기존 Auth 버튼 옆)
- 드롭다운 또는 토글 형태로 `🇰🇷 한국어 / 🇯🇵 日本語` 전환
- 기존 디자인 톤에 맞춘 최소한의 UI

---

### 3. `utils/format.ts` 수정

#### [MODIFY] [format.ts](file:///c:/codes2024/rtcdelivery/frontend/nuxt-app/app/utils/format.ts)
- `getOrderStatusLabel()` 함수에서 하드코딩된 한국어 제거
- i18n의 `$t()` 또는 `useI18n()`의 `t()` 사용으로 변경
- `formatPrice()`, `formatDate()`에서 locale 파라미터 지원하도록 수정

---

### 4. 설계 문서 5개 생성

#### [NEW] docs/architecture.md
- 프로젝트 개요, 전체 시스템 구조(Mermaid 다이어그램)
- 각 MSA 서비스 역할, 통신 방식, Kafka/Redis 사용 계획
- Translation Service 위치, Docker Compose 배포 구조

#### [NEW] docs/internationalization.md
- 지원 언어 (ko, ja), 번역 유형 분류 (Static UI / Domain Data / Error Code / UGC)
- Nuxt i18n 설정 상세, locale 처리, SSR
- 번역 key naming convention
- 향후 언어 추가 방법

#### [NEW] docs/translation-system.md
- AI 번역 서비스 설계, Kafka 기반 비동기 번역 플로우
- Translation DB 구조 (restaurant_translation, menu_translation 등)
- Redis 캐싱, 중복 방지, fallback, retry 전략
- 핵심 도메인 vs UGC 번역 구분

#### [NEW] docs/api-conventions.md
- REST API naming, HTTP status, error response (code 기반)
- pagination, sorting, filtering 규칙
- locale 처리, 인증/인가, API Gateway 규칙
- 서비스 간 통신 규칙

#### [NEW] docs/development-roadmap.md
- Phase 1~14 개발 로드맵
- 현재 서비스 구조에 맞춰 조정

---

## 변경하지 않는 것

- 메인 페이지 디자인/레이아웃/CSS/애니메이션
- `app.vue`, `layouts/default.vue` — 변경 불필요
- Backend Java 코드 — 이번 단계에서 수정 없음
- Pinia 스토어 (auth, cart) — 이번 단계에서 수정 없음
- `plugins/api.ts` — 변경 불필요
- `types/index.ts` — 변경 불필요
- Docker Compose — 변경 불필요

---

## Verification Plan

### Automated Tests
```bash
cd frontend/nuxt-app && npm run dev
```
- Nuxt 개발 서버 정상 기동 확인

### Manual Verification
- 브라우저에서 메인 페이지 로드
- 한국어로 정상 표시 확인
- 언어 전환 UI 클릭 → 일본어로 전체 UI 전환 확인
- 일본어 → 한국어 다시 전환 확인
- 페이지 새로고침 후 locale 유지 확인 (cookie)
- docs/ 문서 5개 생성 확인
