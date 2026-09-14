# RTC Delivery — AI Translation System

> 이 문서는 **개요**입니다. 구현 계약은 아래 스펙이 진실의 원천입니다.
>
> | 문서 | 다루는 것 |
> |---|---|
> | [translation-pipeline-spec.md](./sdd-spec-docs/feature/translation-service/translation-pipeline-spec.md) | Kafka · 잡 큐 · DB · DLQ · food-catalog 연동 |
> | [gemini-provider-spec.md](./sdd-spec-docs/feature/translation-service/gemini-provider-spec.md) | Gemini Developer API · 모델 · 무료 쿼터 |
> | [ugc-translation-spec.md](./sdd-spec-docs/feature/translation-service/ugc-translation-spec.md) | 온디맨드 UGC 번역 API · 프론트 토글 |

---

## 1. 개요 및 목적

RTC Delivery는 정적 UI뿐만 아니라 **동적 도메인 데이터(음식점·메뉴)** 와 **UGC(리뷰 등)** 를 번역해야 합니다.
두 경로는 타이밍이 달라 같은 서비스 안에서 분리합니다.

| 경로 | 트리거 | 처리 | 실패 시 |
|---|---|---|---|
| 카탈로그 | 등록/수정 (자동, 비동기) | Kafka → 잡 큐 → Gemini → Kafka | 조회는 원본(`ko`) 폴백. 서비스가 내려가도 카탈로그는 살아 있다 |
| UGC | 사용자가 [번역] 버튼을 누름 (동기) | `POST /api/v1/translations/ugc` | 에러 코드 + 토스트. 원문은 프론트가 이미 갖고 있다 |

AI 제공자는 **Google AI Studio의 Gemini Developer API**입니다. Vertex AI는 쓰지 않습니다.
근거는 [gemini-provider-spec.md §1](./sdd-spec-docs/feature/translation-service/gemini-provider-spec.md#1-제공자-선택)을 참조하세요.

---

## 2. 번역 대상과 저장 위치

| 구분 | 데이터 | 타이밍 | 저장 |
|---|---|---|---|
| **핵심 도메인** | 음식점/메뉴 이름·설명 | 등록/수정 시 자동 비동기 | 조회용: `rtc_food_catalog`의 `restaurant_translation` / `menu_translation`. 이력: `rtc_translation.translation_history` |
| **UGC** | 리뷰, 문의, 댓글 | 버튼 클릭 시 온디맨드 | 프론트 메모리(토글) + `rtc_translation.ugc_translation` |

카테고리는 번역 테이블을 두지 않습니다. 프론트 i18n `category.{code}`가 담당합니다 ([internationalization.md §2-A](./internationalization.md#a-static-ui--nuxt-i18n)).

> **소유권**: 조회에 쓰이는 번역은 **food-catalog DB**, 이력·사전·UGC는 **translation-service DB**.
> 근거는 [architecture.md §8.1](./architecture.md#81-번역-데이터-소유권)입니다.

**번역 결과를 Redis에 두지 않습니다.** Redis는 RPM/RPD·사용자 일일 상한 카운터만 담당합니다.

---

## 3. 카탈로그 번역 흐름

```
Food Catalog (등록/수정, 같은 트랜잭션)
        │  outbox_event INSERT
        ▼
   Kafka `translation-requests`
        ▼
Translation Service: Consumer
        │  Inbox 멱등성 → translation_job 적재 후 즉시 ack  (AI 호출 없음)
        ▼
JobScheduler (쿼터 잔량 안에서)
        │  Glossary → translation_history → 남은 항목만 Gemini
        │  결과 Outbox INSERT
        ▼
   Kafka `translation-results`
        ▼
Food Catalog: Consumer
        └─► restaurant_translation / menu_translation upsert
```

컨슈머가 이벤트를 받는 자리에서 AI를 부르지 않는 이유는 무료 쿼터입니다.
하루 예산이 끝나면 잡을 `PENDING`에 남겨 다음 날 처리합니다. Kafka에서 같은 레코드를 하루 종일 재시도하지 않습니다.

상세는 [translation-pipeline-spec.md](./sdd-spec-docs/feature/translation-service/translation-pipeline-spec.md)를 따릅니다.

---

## 4. UGC 번역 흐름

X(구 트위터)의 [번역] 버튼과 같습니다. 한 번 번역하면 프론트 변수에 두고 원문↔번역을 토글하며, 새로고침하면 사라져도 됩니다.

```
Frontend [번역 보기]
     │  POST /api/v1/translations/ugc  (원문을 본문에 실음)
     ▼
Translation Service
     ├─ ugc_translation 히트 → 즉시 반환 (AI 없음)
     ├─ 사용자 일일 상한 / 전역 쿼터 확인
     └─ Gemini 호출 → ugc_translation + translation_history 저장
```

- **GET이 아니라 POST**: 원문 전체를 쿼리스트링/액세스 로그에 남기지 않기 위함
- **서버 Redis 캐시 없음**: 같은 리뷰의 재요청은 `ugc_translation`이 막고, 토글은 프론트 `Map`이 막음
- **인증 필요**: 임의 텍스트를 받는 범용 번역기이므로 익명 공개로 두면 하루 예산을 한 스크립트가 소진함

상세는 [ugc-translation-spec.md](./sdd-spec-docs/feature/translation-service/ugc-translation-spec.md)를 따릅니다.

---

## 5. food-catalog 조회용 스키마

아래 두 테이블은 **이미 food-catalog가 소유**합니다. translation-service는 이 테이블에 직접 쓰지 않고 `translation-results`로만 갱신을 요청합니다.

스키마와 폴백 규칙은 [catalog-spec.md §2.5 · §5](./sdd-spec-docs/feature/food-catalog-service/catalog-spec.md)가 진실의 원천입니다.

---

## 6. 장애 대응

| 경로 | 재시도 | 실패 시 |
|---|---|---|
| 카탈로그 (비동기) | 지수 백오프 최대 3회. **429 쿼터는 retry_count를 올리지 않고 다음 날** | DLQ 격리. 조회는 `ko` 폴백 |
| UGC (동기) | 네트워크/5xx만 짧게 재시도 | `TRANSLATION_UNAVAILABLE` / `TRANSLATION_QUOTA_EXCEEDED`. 원문 폴백 응답은 하지 않음 |

원본 텍스트가 바뀌면 food-catalog가 기존 번역을 폐기하고 재번역 이벤트를 발행합니다 ([catalog-spec.md §5.3](./sdd-spec-docs/feature/food-catalog-service/catalog-spec.md#53-원본-수정-시-번역-폐기)).

---

## 7. 무료 쿼터를 전제로 한 절감

무료 티어에서는 캐시·배치·사전이 "비용 절감"이 아니라 **시스템이 동작하기 위한 조건**입니다.

1. **원문 해시 이력** — 서로 다른 가게의 "김치찌개"가 이력 1건을 공유
2. **배치 요청** — 음식점 + 메뉴를 locale당 1회 호출로 묶음
3. **Glossary 완전일치** — 고유명사는 AI를 부르지 않음
4. **자체 RPM/RPD 가드** — 서버 429보다 먼저 멈춤
