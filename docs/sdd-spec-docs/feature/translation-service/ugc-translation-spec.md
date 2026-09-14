# translation-service — UGC 온디맨드 번역 스펙

> **계약 방식**: Swagger (Code-first). 확정된 API 계약의 진실의 원천은 Controller/DTO의 springdoc 어노테이션이며,
> 이 문서는 어노테이션으로 표현되지 않는 설계 결정과 근거를 남깁니다 ([AGENTS.md §2.3](../../../AGENTS.md)).

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [gemini-provider-spec.md](./gemini-provider-spec.md) | AI 호출 계약 · 무료 쿼터 가드 |
| [translation-pipeline-spec.md](./translation-pipeline-spec.md) | 비동기 파이프라인 · DB · 에러 코드 |
| [api-client-spec.md](../nuxt-app/api-client-spec.md) | `$api` 계약 · `ApiError` |
| [api-conventions.md §3](../../../api-conventions.md#3-다국어-locale-처리-규칙) | `Accept-Language` 규약 |

---

## 1. 무엇을 만드는가

X(구 트위터)의 **[번역] 버튼**과 같은 동작입니다.

리뷰 본문 아래에 "번역 보기" 버튼이 있고, 누르면 본문이 사용자 언어로 바뀌며 버튼이 "원문 보기"로 변합니다.
다시 누르면 원문으로 돌아갑니다. 이후 토글은 **네트워크 요청 없이** 즉시 전환됩니다.

핵심 성격이 카탈로그 번역과 다릅니다.

| | 카탈로그 (음식점·메뉴) | UGC (리뷰) |
|---|---|---|
| 트리거 | 점주의 등록·수정 | 사용자의 버튼 클릭 |
| 타이밍 | 비동기 (사후) | 동기 (요청-응답) |
| 대상 건수 | 유한하고 느리게 증가 | 사용자 수 × 리뷰 수 |
| 미번역 시 | `ko` 폴백으로 조용히 넘어감 | 버튼을 눌렀으니 결과를 보여줘야 함 |

---

## 2. 캐싱 설계 — 3계층

### 2.1 왜 프론트 메모리인가

번역 결과를 어디에 둘지는 이 스펙에서 가장 논쟁적인 지점입니다. 결론은 **프론트엔드 메모리를 1차로 두는 것**입니다.

- 같은 리뷰의 번역/원문 토글은 **한 사람이 한 화면에서 여러 번** 누르는 동작입니다. 이걸 매번 서버로 보내면
  Redis든 DB든 결국 왕복 비용을 내는데, 정작 필요한 건 이미 손에 있는 문자열입니다.
- 새로고침하면 사라져도 됩니다. 리뷰 번역은 다시 누르면 되는 부수적 기능이고,
  영속화의 복잡도를 감당할 가치가 없습니다.

**Redis 계층은 두지 않습니다.** 서버 캐시가 필요한 이유는 "다른 사용자가 같은 리뷰를 번역할 때 AI를 다시 부르지 않는 것"인데,
그 역할은 이미 존재하는 `ugc_translation` 테이블이 합니다. Redis를 추가하면 DB와 이중 관리가 되고,
UGC 번역은 초당 수천 건 규모가 아니라 DB 인덱스 조회로 충분합니다.

### 2.2 3계층

| 계층 | 위치 | 수명 | 막는 것 |
|---|---|---|---|
| 1 | 프론트 컴포저블의 `Map` | 페이지 생존 기간 (새로고침 시 소멸) | 같은 사용자의 토글 반복 |
| 2 | `ugc_translation` 테이블 | 영구 | **다른 사용자의 같은 리뷰 번역 요청** |
| 3 | (없음) | | AI 호출 |

2계층이 없으면 리뷰 하나가 인기를 얻을 때 그 리뷰를 본 사람 수만큼 AI를 부릅니다.
무료 티어의 하루 400건 예산에서는 **리뷰 하나가 예산 전체를 태울 수 있습니다.**
그래서 프론트 캐시만으로 끝내지 않고 서버 lookup을 둡니다.

> 사용자 관점에서는 2계층이 보이지 않습니다. 응답의 `cached` 필드로만 드러납니다.

### 2.3 `ugc_translation`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK | |
| `content_type` | VARCHAR(30) | NOT NULL | `REVIEW` / `INQUIRY` / `COMMENT` |
| `content_id` | BIGINT | NOT NULL | 원본 콘텐츠 ID |
| `source_hash` | CHAR(64) | NOT NULL | 정규화한 원문의 SHA-256 |
| `source_locale` | VARCHAR(10) | NOT NULL | |
| `target_locale` | VARCHAR(10) | NOT NULL | |
| `translated_text` | TEXT | NOT NULL | |
| `provider` / `model` | VARCHAR | NOT NULL / NULL | |
| — | | UNIQUE `uk_ugc (content_type, content_id, source_hash, target_locale)` | |

**`content_id`와 `source_hash`를 함께 유니크 키에 넣습니다.**
`content_id`만으로 잡으면 사용자가 리뷰를 수정했을 때 옛 원문의 번역이 반환됩니다.
`source_hash`만으로 잡으면 `content_id` 없이도 동작하지만, 어떤 콘텐츠의 번역인지 추적할 수 없어 삭제 연동이 불가능합니다.

원문이 수정되면 새 `source_hash`로 행이 하나 더 생기고, 옛 행은 조회되지 않은 채 남습니다.
정리는 하지 않습니다 — 리뷰 수정 빈도가 낮고, 이력으로서 가치가 있습니다.

---

## 3. API

### 3.1 엔드포인트

| Method | Path | 접근 |
|---|---|---|
| POST | `/api/v1/translations/ugc` | `isAuthenticated()` |

Gateway가 `/api/v1/translations/**`를 translation-service로 라우팅합니다.

**GET이 아니라 POST인 이유**: 원문 전체를 요청에 실어 보냅니다. 리뷰 본문은 수백 자가 되므로 쿼리 스트링에 넣을 수 없고,
URL과 액세스 로그에 사용자 작성 글이 그대로 남는 것도 피해야 합니다.
"조회이므로 GET"이라는 원칙([api-conventions.md §2](../../../api-conventions.md#2-rest-api-naming-rules))과 어긋나지만,
본문 길이와 로그 노출이 더 무게가 있다고 판단했습니다.

### 3.2 원문을 클라이언트가 보내는 이유

translation-service가 `contentId`로 리뷰를 조회해오는 방법도 있습니다. 그렇게 하지 않은 이유입니다.

- 리뷰는 아직 존재하지 않는 도메인입니다. 소유 서비스가 정해지지 않았는데 조회 의존을 만들 수 없습니다.
- 조회를 하려면 동기 REST 호출이 생기고, Circuit Breaker·Retry가 붙고, 그 서비스가 내려가면 번역이 멈춥니다 (AGENTS.md §1.1).
- 프론트는 화면에 그 텍스트를 이미 렌더링해 두었습니다. 서버가 다시 읽어올 이유가 없습니다.

> **비용**: 클라이언트가 임의의 텍스트를 보낼 수 있으므로, 이 엔드포인트는 사실상 **범용 번역기**입니다.
> 남용 방지가 §5의 주제이고, 이 스펙에서 인증을 요구하는 이유입니다.

### 3.3 요청

```json
{
  "contentType": "REVIEW",
  "contentId": 1024,
  "text": "면이 쫄깃하고 국물이 진해요. 재방문 의사 100%!",
  "sourceLocale": "ko"
}
```

| 필드 | 검증 | 설명 |
|---|---|---|
| `contentType` | `@NotNull`, enum | `REVIEW` / `INQUIRY` / `COMMENT` |
| `contentId` | `@NotNull`, `@Positive` | |
| `text` | `@NotBlank`, `@Size(max = 2000)` | 원문 |
| `sourceLocale` | nullable, 지원 목록 | 생략 시 `ko` |

**대상 locale은 본문에 넣지 않고 `Accept-Language` 헤더에서 읽습니다.**
프로젝트 전체가 이미 그 규약을 쓰고 있고([api-conventions.md §3](../../../api-conventions.md#3-다국어-locale-처리-규칙)),
API Client가 헤더를 자동 주입하므로 호출부가 신경 쓸 것이 없습니다.

`text`의 상한 2000자는 리뷰 입력 상한(향후 리뷰 도메인에서 정할 값)과 맞춰야 합니다.
상한이 없으면 한 번의 요청으로 대량 토큰을 태울 수 있습니다.

### 3.4 응답

`ApiResponse<T>`로 감쌉니다.

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "contentType": "REVIEW",
    "contentId": 1024,
    "sourceLocale": "ko",
    "targetLocale": "ja",
    "translatedText": "麺がもちもちで、スープが濃厚です。再訪希望100%！",
    "cached": true
  }
}
```

| 필드 | 설명 |
|---|---|
| `translatedText` | 번역 결과 |
| `targetLocale` | 실제 적용된 locale. 요청 헤더와 다를 수 있다 (지원하지 않는 언어 → `ko` 폴백) |
| `cached` | `ugc_translation` 히트 여부. 프론트는 쓰지 않지만 쿼터 절감 효과 확인에 쓰인다 |

### 3.5 `sourceLocale == targetLocale`인 경우

한국어 사용자가 한국어 리뷰의 번역 버튼을 누른 상황입니다.
**AI를 부르지 않고 원문을 그대로 반환**하며 `cached: true`로 응답합니다.

프론트에서 버튼 자체를 감추는 것이 먼저지만(§6.2), 서버도 방어합니다.
이 케이스에 AI를 부르면 쿼터를 낭비하고, 모델이 문장을 "다듬어" 반환해 원문이 미묘하게 바뀝니다.

### 3.6 에러

| 코드 | HTTP | 조건 | 프론트 동작 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 필드 검증 실패 | 토스트 |
| `TRANSLATION_TEXT_TOO_LONG` | 400 | `text` 2000자 초과 | 토스트 |
| `UNAUTHORIZED` | 401 | 비로그인 | 로그인 유도 |
| `TRANSLATION_QUOTA_EXCEEDED` | 429 | 일일/분당 쿼터 소진 또는 사용자 상한 초과 | 토스트 + 버튼 원복 |
| `TRANSLATION_UNAVAILABLE` | 503 | AI API 장애 · 타임아웃 | 토스트 + 버튼 원복 |

**실패 시 원문을 반환하지 않습니다.** 비동기 카탈로그 경로는 원문 폴백이 맞지만
([catalog-spec.md §5.2](../food-catalog-service/catalog-spec.md#52-폴백)), 여기서는 프론트가 원문을 이미 화면에 갖고 있습니다.
200에 원문을 담아 주면 **번역이 실패했다는 사실이 사라져** 사용자는 "버튼을 눌렀는데 아무 일도 안 일어났다"고 인식합니다.
에러로 내려 토스트를 띄우고 버튼을 원복하는 것이 정직합니다.

에러 코드는 `error.{CODE}` i18n 키로 매핑되므로 **`i18n/locales/{ko,ja}.json`에 4개 키를 추가**해야 합니다
([error-handling.md §3](../../../error-handling.md#3-에러-코드-카탈로그)).

---

## 4. 서버 처리 순서

```
POST /api/v1/translations/ugc
  │
  ├─ ① Bean Validation
  ├─ ② targetLocale 판정 (Accept-Language → SupportedLocale, 미지원 시 ko)
  ├─ ③ sourceLocale == targetLocale ? → 원문 반환 (§3.5)
  ├─ ④ 원문 정규화 → source_hash 계산
  ├─ ⑤ ugc_translation 조회 → 히트 시 반환 (cached: true)
  ├─ ⑥ 사용자별 일일 상한 확인 → 초과 시 429 (§5.2)
  ├─ ⑦ 전역 쿼터 가드 확인 → 소진 시 429
  ├─ ⑧ TranslationProvider 호출 (entries 1건)
  ├─ ⑨ ugc_translation INSERT + translation_history INSERT
  └─ ⑩ 응답 (cached: false)
```

⑥이 ⑦보다 앞인 이유는, 한 사용자가 전역 예산을 태워 다른 사용자를 막는 것을 방지하기 위해서입니다.

⑤가 ⑥보다 앞인 이유는, **캐시 히트는 AI를 부르지 않으므로 사용자 상한에 계상하지 않기** 위해서입니다.
캐시된 리뷰를 여러 번 토글하는 것은 제한할 이유가 없습니다.

⑧은 [translation-pipeline-spec.md §4](./translation-pipeline-spec.md)의 잡 큐를 경유하지 **않습니다.**
동기 요청이라 사용자가 응답을 기다리고 있고, 잡 큐는 "나중에 처리해도 되는" 작업을 위한 것입니다.
쿼터가 없으면 미루지 않고 429로 즉시 알립니다.

⑨의 `translation_history` INSERT는 카탈로그 경로와 이력을 공유하기 위한 것입니다.
리뷰에 "김치찌개"만 적혀 있으면 카탈로그 번역이 그 이력을 재사용합니다.

---

## 5. 남용 방지

### 5.1 인증을 요구한다

카탈로그 조회는 공개인데 번역만 로그인을 요구하는 것은 UX상 어색합니다. 그럼에도 `isAuthenticated()`인 이유입니다.

§3.2에서 밝혔듯 이 엔드포인트는 **임의 텍스트를 받는 범용 번역기**이고, 예산은 하루 400건입니다.
익명 공개로 두면 스크립트 하나가 그날의 번역 기능 전체를 정지시킬 수 있으며,
무료 티어에서는 요금이 아니라 **기능 정지**로 귀결됩니다. 게다가 남용 주체를 특정할 수단도 없습니다.

> 로그인 사용자만 리뷰를 쓸 수 있다면 번역만 로그인 필요라는 비대칭도 크게 줄어듭니다.
> 리뷰 도메인 착수 시 다시 검토합니다.

### 5.2 사용자별 일일 상한

| 항목 | 값 |
|---|---|
| 키 | `ugc:quota:{userId}:{yyyyMMdd}` (태평양시 기준 — 전역 쿼터와 리셋 시점을 맞춘다) |
| 상한 | `translation.ugc.daily-limit-per-user=20` |
| 계상 대상 | **AI를 실제로 부른 요청만** (§4의 ⑤ 캐시 히트는 제외) |
| 초과 시 | 429 `TRANSLATION_QUOTA_EXCEEDED` |

Redis 카운터를 씁니다. §2.1에서 "UGC에 Redis를 두지 않는다"고 한 것은 **번역 결과 캐시**이고,
카운터는 인스턴스 간에 공유되어야 하는 짧은 수명의 데이터라 Redis가 맞습니다.

20건은 정상 사용자가 한 화면에서 누를 수 있는 횟수를 넉넉히 넘고, 전역 400건의 5%입니다.
한 사용자가 예산을 독점하지 못하는 선입니다.

### 5.3 Gateway Rate Limiting

`/api/v1/translations/**`는 Redis 기반 Rate Limiting의 우선 적용 대상입니다
([tasks.md §6](../../../tasks.md)의 미착수 항목).
아직 없으므로 §5.2의 애플리케이션 상한이 유일한 방어선입니다. 이 사실을 인지하고 배포합니다.

---

## 6. 프론트엔드

### 6.1 컴포저블

`app/composables/useUgcTranslation.ts`. AGENTS.md §5.6에 따라 `isLoading` / `errorMessage`를 포함합니다.

```typescript
type UgcContentType = 'REVIEW' | 'INQUIRY' | 'COMMENT'

interface UgcTranslationResponse {
  contentType: UgcContentType
  contentId: number
  sourceLocale: string
  targetLocale: string
  translatedText: string
  cached: boolean
}

export const useUgcTranslation = () => {
  const { $api } = useNuxtApp()
  const { t, locale } = useI18n()

  // 캐시 키에 locale을 넣는다. 언어를 바꿨는데 이전 언어의 번역이 보이면 안 된다.
  const cache = new Map<string, string>()
  const pending = ref<Set<string>>(new Set())
  const errorMessage = ref('')

  const keyOf = (type: UgcContentType, id: number) => `${type}:${id}:${locale.value}`

  const translate = async (type: UgcContentType, id: number, text: string) => {
    const key = keyOf(type, id)
    const hit = cache.get(key)
    if (hit) return hit

    pending.value.add(key)
    errorMessage.value = ''
    try {
      const res = await $api.post<UgcTranslationResponse>('/translations/ugc', {
        contentType: type,
        contentId: id,
        text,
        sourceLocale: 'ko',
      })
      cache.set(key, res.translatedText)
      return res.translatedText
    } catch (e) {
      errorMessage.value = t((e as ApiError).i18nKey)
      return null
    } finally {
      pending.value.delete(key)
    }
  }

  return { translate, pending, errorMessage }
}
```

`cache`를 `useState`가 아니라 **평범한 `Map`으로 둡니다.** `useState`는 SSR 페이로드에 직렬화되어
HTML에 실려 나가는데, 이 데이터는 서버가 만들 수 없고(인증 필요) 새로고침 시 사라져도 되는 값입니다.
페이로드만 키웁니다.

`pending`을 boolean 하나가 아니라 키 집합으로 두는 이유는, 목록에 리뷰가 여러 개 있고 사용자가
두 개를 연달아 누를 수 있기 때문입니다. 스피너가 눌린 항목에만 떠야 합니다.

### 6.2 컴포넌트

`app/components/review/ReviewTranslateToggle.vue` (리뷰 도메인 착수 시 배치).

```
상태: 'original' | 'translated'

[번역 보기]  클릭
  → 캐시 있으면 즉시 'translated'
  → 없으면 스피너 → 성공 시 'translated' / 실패 시 'original' 유지 + 토스트

[원문 보기]  클릭 → 즉시 'original' (네트워크 요청 없음)
```

**버튼을 숨기는 조건**: `locale === 'ko'`이고 원문도 한국어인 경우. 번역할 것이 없습니다.
지금은 원문 언어를 판별할 수단이 없으므로 리뷰 도메인이 `sourceLocale`을 저장하게 하는 것을 전제로 하고,
그때까지는 `locale !== 'ko'`일 때만 버튼을 노출합니다.

번역 표시 중에는 "AI 번역" 라벨을 함께 보여줍니다. 기계 번역이라는 사실을 감추면 오역의 책임이 작성자에게 갑니다.
라벨 문구는 `translation.aiTranslated` i18n 키입니다.

### 6.3 i18n 키

`i18n/locales/{ko,ja}.json`에 추가합니다.

| 키 | ko | ja |
|---|---|---|
| `translation.showTranslation` | 번역 보기 | 翻訳を表示 |
| `translation.showOriginal` | 원문 보기 | 原文を表示 |
| `translation.aiTranslated` | AI 번역 | AI翻訳 |
| `error.TRANSLATION_QUOTA_EXCEEDED` | 번역 요청이 많아 잠시 후 다시 시도해 주세요. | 翻訳リクエストが多いため、しばらくしてからお試しください。 |
| `error.TRANSLATION_UNAVAILABLE` | 번역 서비스를 사용할 수 없습니다. | 翻訳サービスを利用できません。 |
| `error.TRANSLATION_TEXT_TOO_LONG` | 번역할 수 있는 길이를 초과했습니다. | 翻訳可能な長さを超えています。 |
| `error.UNSUPPORTED_TARGET_LOCALE` | 지원하지 않는 언어입니다. | サポートされていない言語です。 |

---

## 7. 개인정보와 무료 티어

무료 티어에서는 **프롬프트와 응답이 Google 제품 개선에 사용됩니다**
([gemini-provider-spec.md §1.2](./gemini-provider-spec.md#12-google-ai-studioGemini-developer-api를-쓰고-vertex-ai는-쓰지-않는다)).

음식점명·메뉴명은 공개 데이터라 무해하지만, **리뷰는 사용자가 작성한 글입니다.**
현 단계(개발·시연)에서는 감수하되, 아래를 지킵니다.

- 실사용자를 받기 전에 **유료 티어(Tier 1) 전환**. 유료 티어에서는 데이터가 학습에 쓰이지 않습니다
- `store=false`로 서버측 대화 이력을 남기지 않습니다
- 요청 본문 전체를 로그에 남기지 않습니다. `contentType:contentId`와 길이만 기록합니다
- 시연용 시드 리뷰에 실제 개인정보를 넣지 않습니다

> 이 조건은 배포 게이트입니다. 회원 리뷰 기능을 공개하는 시점에 유료 전환이 선행되어야 합니다.

---

## 8. 테스트 계획

| 대상 | 검증 |
|---|---|
| `UgcTranslationServiceTest` | 캐시 히트 시 Provider 미호출, `cached: true` |
| " | `sourceLocale == targetLocale`이면 Provider 미호출, 원문 반환 (§3.5) |
| " | 원문 수정(해시 변경) 시 새 행 생성, 옛 행 미반환 |
| " | 사용자 상한 초과 시 429, **캐시 히트는 상한에 계상되지 않음** |
| " | Provider 실패를 `TRANSLATION_UNAVAILABLE`로 변환 (원문 폴백하지 않음) |
| `UgcTranslationControllerTest` (`@WebMvcTest`) | 비로그인 401 vs 권한 부족 403 구분, `Accept-Language` → `targetLocale` 반영 |
| " | `text` 2001자 → 400, 에러 응답에 `data` 키 유지 |
| `UgcTranslationRepositoryTest` (`@DataJpaTest`) | `uk_ugc` 4열 유니크, `content_id` 같고 해시 다른 행 공존 |
