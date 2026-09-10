# RTC Delivery — AI Translation System (Planned)

## 1. 개요 및 목적

RTC Delivery는 다국적 실시간 커머스 서비스로서, 정적 UI뿐만 아니라 **동적 도메인 데이터(음식점 정보, 메뉴 정보)** 및 **사용자 생성 콘텐츠(UGC, 리뷰 등)**에 대한 번역 체계가 필수적입니다.

본 문서에서는 향후 구현될 **Translation Service**의 역할, AI 번역 연동 구조, 비동기 파이프라인, 캐싱 전략 및 비용 절감 방안을 명세합니다.

> **주의**: 본 시스템은 현재 설계 단계이며 실제 구현은 Planned 상태입니다.

---

## 2. 번역 대상 데이터의 분류 및 처리 전략

| 구분 | 데이터 종류 | 번역 타이밍 | 처리 방식 | 저장/캐싱 위치 |
|---|---|---|---|---|
| **핵심 도메인 데이터** | 음식점 이름/설명, 메뉴 이름/설명, 카테고리 | **등록/수정 시점 (자동 비동기)** | Food Catalog Service → Kafka → Translation Service → AI API → Kafka → Food Catalog Service | `rtc_food_catalog`의 `restaurant_translation` / `menu_translation` (조회용) + `rtc_translation`의 번역 이력 |
| **사용자 생성 콘텐츠 (UGC)** | 리뷰, 문의사항, 사용자 커뮤니티 | **사용자 요청 시점 (온디맨드)** | Frontend [번역] 버튼 클릭 → Translation Service → AI API | Redis Cache (TTL) + `rtc_translation`의 `ugc_translation` |

> **소유권 원칙**: 조회 경로에 쓰이는 번역은 **food-catalog DB**에 두고, 번역 도메인의 원본(이력·사전·UGC)은 **translation-service DB**에 둡니다. 근거는 [architecture.md §8.1](./architecture.md#81-번역-데이터-소유권)을 참조하세요.

---

## 3. 핵심 도메인 데이터 번역 흐름 (등록/수정 시)

```
Food Catalog Service
        │
        │ 1. RestaurantCreated / MenuCreated Event (Outbox 경유)
        ▼
   Kafka Topic: `translation-requests`
        │
        ▼
   Translation Service (Consumer)
        │
        ├─► 2. 중복 번역 검사 (Redis / Glossary / 번역 이력 Lookup)
        │
        ├─► 3. AI API 호출 (OpenAI / DeepL / Gemini 등)
        │
        ├─► 4. 번역 이력 저장 (`rtc_translation`)
        │
        └─► 5. Kafka Topic: `translation-results`
                │
                ▼
        Food Catalog Service (Consumer)
                │
                └─► 6. `restaurant_translation` / `menu_translation` upsert
                       (`rtc_food_catalog`, Inbox 멱등성 적용)
```

### 흐름 상세

1. **이벤트 발행**: 점주가 한국어로 음식점/메뉴를 등록하면 `food-catalog-service`가 같은 트랜잭션에서 `outbox_event`에 INSERT하고, Polling Publisher가 Kafka로 발행합니다. DB 저장과 Kafka 발행을 동시에 하지 않는 이유는 [AGENTS.md §4.1](./AGENTS.md)을 참조하세요.
2. **비동기 처리**: `translation-service`가 이벤트를 소비하고 지원 언어(`ja` 등)로 AI 번역을 수행합니다.
3. **원본 영속화**: 번역 결과는 `rtc_translation`에 이력으로 남습니다. 재번역 판단과 비용 추적의 기준이 됩니다.
4. **조회용 반영**: `translation-results`를 `food-catalog-service`가 소비해 자기 DB의 `restaurant_translation` / `menu_translation`에 upsert합니다. 이후 조회는 food-catalog 단독 JOIN으로 끝납니다.
5. **폴백**: 4번이 아직 도착하지 않은 데이터는 원본 언어(`ko`)로 응답합니다. 번역 파이프라인이 멈춰도 카탈로그 조회는 영향받지 않습니다.

---

## 4. 사용자 생성 콘텐츠 (UGC) 번역 흐름 (온디맨드)

```
Nuxt Frontend
     │
     │ 1. GET /api/v1/translations/ugc?contentId=123&targetLocale=ja
     ▼
API Gateway
     │
     ▼
Translation Service
     │
     ├─► 2. Redis 캐시 확인 (Key: `trans:review:123:ja`)
     │       ├─ Cache Hit: 즉시 반환
     │       └─ Cache Miss: 계속 진행
     │
     ├─► 3. AI API 번역 요청
     │
     ├─► 4. Redis 캐시 저장 (TTL: 7일) & DB 기록
     │
     └─► 5. 번역 결과 응답
```

---

## 5. DB Translation Schema 설계 (Planned)

아래 두 테이블은 **`rtc_food_catalog`(food-catalog-service 소유)** 에 둡니다. 조회 API가 `restaurant` / `menu` 본체와 JOIN해야 하기 때문입니다. `translation-service`는 이 테이블에 직접 쓰지 않고 `translation-results` 이벤트로만 갱신을 요청합니다.

### Restaurant Translation

```sql
CREATE TABLE restaurant_translation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    locale VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_restaurant_locale (restaurant_id, locale)
);
```

### Menu Translation

```sql
CREATE TABLE menu_translation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_id BIGINT NOT NULL,
    locale VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_menu_locale (menu_id, locale)
);
```

---

## 6. 장애 대응 및 예외 처리 전략

### Retry 전략

- AI API 호출 실패 시 Exponential Backoff (1s, 2s, 4s...) 방식으로 최대 3회 재시도합니다.
- 지속 실패 시 Kafka Dead Letter Queue (DLQ)로 격리하여 관리자 알림 및 수동 re-drive를 제공합니다.

### Fallback 전략

- 번역 실패 또는 AI API 장애 시 원본 언어(Source Text, 기본 `ko`)를 원본 그대로 반환합니다.
- 프론트엔드는 번역 실패 시 원본 문구를 보여주어 서비스 중단을 방지합니다.

### 원본 변경 시 번역 갱신

- 원본 음식점/메뉴 텍스트가 수정되면 `restaurant_translation` / `menu_translation`에 저장된 해당 데이터는 invalidation 처리되거나 재번역 이벤트를 발행합니다.

---

## 7. AI API 비용 절감 전략

1. **Redis Caching**: 동일 문장에 대한 중복 AI API 호출 방지
2. **Batch Request**: 여러 메뉴 항목을 한 번의 API 요청으로 묶어서 처리
3. **Glossary/Dictionary**: 음식 명칭(예: "김치찌개" -> "キムチチゲ")은 공통 고유명사 사전을 우선 적용하여 API 호출 절감 및 정확도 향상
