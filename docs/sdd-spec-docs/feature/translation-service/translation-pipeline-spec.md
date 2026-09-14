# translation-service — 번역 파이프라인 스펙

> **계약 방식**: Swagger (Code-first). REST 계약의 진실의 원천은 Controller/DTO 어노테이션이며,
> 이 문서는 Kafka 이벤트 계약과 설계 근거를 남깁니다 ([AGENTS.md §2.3](../../../AGENTS.md)).
>
> **Kafka 이벤트는 Swagger로 표현되지 않으므로, §2의 페이로드 정의가 그 자체로 계약입니다.**
> food-catalog와 translation-service 양쪽이 이 문서를 따라야 합니다.

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [gemini-provider-spec.md](./gemini-provider-spec.md) | AI 호출 계약 · 무료 쿼터 가드 |
| [ugc-translation-spec.md](./ugc-translation-spec.md) | 온디맨드 UGC 번역 API |
| [architecture.md §8.1](../../../architecture.md#81-번역-데이터-소유권) | 번역 데이터 소유권 분리 |
| [catalog-spec.md §5](../food-catalog-service/catalog-spec.md#5-다국어-조회) | food-catalog의 조회·폴백·번역 폐기 |
| [AGENTS.md §4](../../../AGENTS.md) | Outbox · Inbox · Kafka 규칙 |

---

## 1. 서비스 개요

### 1.1 위치와 포트

| 항목 | 값 |
|---|---|
| 폴더 | `backend/translation-service/` |
| 베이스 패키지 | `com.rtcdelivery.translation` |
| 포트 | 8085 (기존 8081~8084 다음) |
| 컨테이너명 | `rtc-translation-service` |
| DB | `rtc_translation` |
| Consumer Group | `translation-service-group` |

> DB명은 다른 서비스와 같은 `rtc_{domain}` 규약을 따릅니다. `infra/mysql/init/01-init-databases.sql`에
> `rtc_translation`과 `rtcuser` 권한을 추가합니다.

### 1.2 현재 스캐폴딩 상태

`backend/translation-service/`는 Spring Initializr 산출물만 있는 상태입니다. 아래를 채워야 합니다.

- [ ] `build.gradle` — food-catalog의 것을 기준으로 web / data-jpa / mysql / security / validation / eureka-client / kafka / data-redis / actuator / springdoc / lombok / mapstruct / jacoco. **`spring-boot` 플러그인 버전을 3.4.1로, `springCloudVersion`을 2024.0.0으로 맞춘다** (현재 4.1.1이 박혀 있어 다른 서비스와 어긋난다)
- [ ] `common/ApiResponse`, `exception/{ErrorCode, BusinessException, GlobalExceptionHandler, ErrorResponseWriter, RestAuthenticationEntryPoint, RestAccessDeniedHandler}` — food-catalog와 동일 ([error-handling.md §4](../../../error-handling.md#4-mvc-서비스-구현-member-auth--food-catalog--order--payment))
- [ ] `domain/BaseTimeEntity` + `config/JpaAuditingConfig`
- [ ] `config/SecurityConfig` + `security/HeaderAuthenticationFilter` + `security/Actor`
- [ ] `common/SupportedLocale` + `config/LocaleConfig`
- [ ] Eureka Client 등록 + api-gateway 라우팅(`/api/v1/translations/**`) 추가
- [ ] `infra/mysql/init/01-init-databases.sql`에 DB·권한 추가
- [ ] `docker-compose-dev.yml`에 서비스 추가

> 공통 클래스를 복제하는 것은 이 프로젝트의 기존 방침입니다. 공유 라이브러리를 두면 배포 결합이 생기고,
> MSA 경계를 흐립니다. member-auth → food-catalog에서도 같은 방식으로 옮겼습니다.

---

## 2. Kafka 이벤트 계약

### 2.1 토픽

| 토픽 | Producer | Consumer | 용도 |
|---|---|---|---|
| `translation-requests` | food-catalog (Outbox) | translation-service | 번역 요청 |
| `translation-requests.DLT` | translation-service | — (수동 re-drive) | 재시도 소진 격리 |
| `translation-results` | translation-service (Outbox) | food-catalog | 번역 결과 |
| `translation-results.DLT` | food-catalog | — (수동 re-drive) | 반영 실패 격리 |

`AckMode.RECORD`(레코드 단위 커밋)와 역직렬화 실패 시 레코드를 건너뛰는 에러 핸들러를 씁니다 (AGENTS.md §4.4).

메시지 키는 **`restaurant:{restaurantId}`** 로 고정합니다. 같은 음식점의 요청이 같은 파티션에 들어가
순서가 보장되므로, 원본을 두 번 연달아 수정했을 때 오래된 번역이 새 번역을 덮어쓰는 일을 막습니다.

### 2.2 공통 Envelope

두 토픽 모두 같은 봉투를 씁니다.

```json
{
  "eventId": "3f2a9c10-5b1e-4f8a-9d02-7c6b5e4a1d33",
  "eventType": "TRANSLATION_REQUESTED",
  "aggregateType": "RESTAURANT",
  "aggregateId": "12",
  "occurredAt": "2026-09-14T09:30:00Z",
  "payload": { }
}
```

`eventId`는 UUID이고 **Inbox 멱등성의 키**입니다 (§5.2).

### 2.3 `translation-requests` 페이로드

```json
{
  "sourceLocale": "ko",
  "targetLocales": ["ja"],
  "reason": "CREATED",
  "entries": [
    { "targetType": "RESTAURANT", "targetId": 12, "name": "백종원의 골목식당", "description": "3대째 이어온 노포" },
    { "targetType": "MENU", "targetId": 87, "name": "김치찌개", "description": null },
    { "targetType": "MENU", "targetId": 88, "name": "된장찌개", "description": "구수한 재래식 된장" }
  ]
}
```

| 필드 | 설명 |
|---|---|
| `sourceLocale` | 항상 `ko`. 원본 언어가 확장될 여지를 남긴다 |
| `targetLocales` | 번역 대상. 현재 `["ja"]`. **food-catalog가 정하지 않고 translation-service가 결정할 수도 있지만, 요청자가 명시하게 두었다** (§2.5) |
| `reason` | `CREATED` / `UPDATED`. 이력 분석용이며 처리 분기에는 쓰지 않는다 |
| `entries[].targetType` | `RESTAURANT` / `MENU` |
| `entries[].targetId` | food-catalog의 `restaurants.id` / `foods.id` |
| `entries[].name` | 번역 대상 원문. **NOT NULL** |
| `entries[].description` | 번역 대상 원문. nullable |

**원문을 이벤트에 실어 보냅니다.** translation-service가 food-catalog에 되묻지 않는 이유는,
동기 호출이 생기면 Circuit Breaker·Retry가 필요해지고 food-catalog가 내려가면 번역이 멈추기 때문입니다.
이벤트가 자기완결적이면 소비 시점에 원본이 이미 또 바뀌어 있어도 **그 이벤트가 가리키는 원문**을 정확히 번역합니다.

> **음식점과 메뉴를 한 이벤트에 담습니다.** 배치 1회 호출로 묶기 위해서입니다
> ([gemini-provider-spec.md §5.2](./gemini-provider-spec.md)). 메뉴 하나만 수정된 경우에는 그 메뉴만 담은
> `entries` 1건짜리 이벤트가 됩니다.

### 2.4 `translation-results` 페이로드

```json
{
  "targetLocale": "ja",
  "sourceLocale": "ko",
  "entries": [
    { "targetType": "RESTAURANT", "targetId": 12, "name": "ペク・ジョンウォンの路地食堂", "description": "三代続く老舗" },
    { "targetType": "MENU", "targetId": 87, "name": "キムチチゲ", "description": null }
  ]
}
```

**locale 하나당 이벤트 하나**입니다. `ja`와 `zh`를 한 이벤트에 담으면 food-catalog가 부분 성공을 처리해야 하는데,
locale마다 AI 호출이 별도로 실패할 수 있어 부분 성공이 실제로 발생합니다. 나눠 보내면 각각 독립적으로 재시도됩니다.

`entries`는 요청의 부분집합일 수 있습니다. 사전·이력에서 해결된 항목과 AI가 번역한 항목이 섞이고,
일부 항목이 실패해 다음 잡으로 미뤄질 수 있기 때문입니다. **food-catalog는 도착한 항목만 upsert합니다.**

### 2.5 `targetLocales`를 food-catalog가 정하는 이유

지원 언어를 아는 쪽은 번역 서비스라서 translation-service가 정하는 것이 자연스러워 보입니다.
그럼에도 요청자가 명시하게 둔 것은, **이벤트 페이로드만 보고 재처리 결과를 재현할 수 있어야** 하기 때문입니다.
서비스가 정하면 DLQ에 3개월 전 이벤트를 re-drive했을 때 그동안 늘어난 언어까지 번역되어,
원래 이벤트가 의도한 것과 다른 결과가 나옵니다.

지원 언어 목록은 `SupportedLocale`(`ko` / `ja`)로 두 서비스에 중복 정의됩니다. `ko`는 원본이므로 대상에서 제외합니다.

---

## 3. 처리 흐름 — 잡 큐를 경유한다

### 3.1 왜 컨슈머가 바로 번역하지 않는가

무료 티어의 하루 400~500건 예산 안에서 돌아야 합니다
([gemini-provider-spec.md §5](./gemini-provider-spec.md#5-무료-쿼터-가드)).
컨슈머가 이벤트를 받는 자리에서 AI를 호출하면 두 가지가 깨집니다.

1. **쿼터가 소진되면 ack할 방법이 없다.** 예외를 던져 재처리시키면 컨슈머가 같은 레코드로 하루 종일 회전하고,
   ack하고 버리면 번역이 유실됩니다.
2. **Kafka의 재시도는 우리가 원하는 재시도가 아니다.** "내일 다시 시도"를 Kafka 리트라이 토픽으로 표현하려면
   지연 토픽 계층을 쌓아야 합니다.

그래서 **수신과 처리를 분리**합니다. 컨슈머는 잡을 적재하고 즉시 ack하며, 스케줄러가 예산 안에서 꺼내 처리합니다.
"내일 다시"는 `next_retry_at` 컬럼 한 개로 표현됩니다.

### 3.2 전체 흐름

```
food-catalog: 음식점/메뉴 등록·수정 (같은 트랜잭션)
     │  outbox_event INSERT
     ▼
food-catalog: Outbox Polling Publisher
     │
     ▼  Kafka `translation-requests`
translation-service: RequestConsumer
     │  ① inbox_event INSERT (eventId 중복이면 스킵 후 ack)
     │  ② targetLocale 별로 translation_job INSERT (status=PENDING)
     │  ③ 즉시 ack                                    ← AI 호출 없음
     ▼
translation-service: JobScheduler  (@Scheduled, 30초 간격)
     │  ① 쿼터 잔량 확인 — 없으면 이번 턴 종료
     │  ② PENDING & next_retry_at <= now 인 잡을 잔량만큼 SELECT ... FOR UPDATE SKIP LOCKED
     │  ③ 3단 게이트: Glossary → translation_history → 남은 항목만 AI
     │  ④ translation_history UPSERT
     │  ⑤ outbox_event INSERT (translation-results)   ← 같은 트랜잭션
     │  ⑥ 잡 상태 전이
     ▼
translation-service: Outbox Polling Publisher
     │
     ▼  Kafka `translation-results`
food-catalog: ResultConsumer
     │  ① inbox_event INSERT (멱등성)
     │  ② restaurant_translation / menu_translation UPSERT (putTranslation)
     ▼
조회 API가 Accept-Language로 번역본을 반환. 없으면 원본(ko) 폴백
```

### 3.3 스케줄러가 필요한 이유와 `SKIP LOCKED`

인스턴스가 여러 개면 같은 잡을 동시에 집어 AI를 두 번 부릅니다. 무료 예산에서는 치명적이므로
`SELECT ... FOR UPDATE SKIP LOCKED`로 잡을 선점합니다. 잠긴 행은 건너뛰므로 인스턴스들이 서로 다른 잡을 나눠 갖습니다.

```java
@Query(value = """
        SELECT * FROM translation_job
        WHERE status = 'PENDING' AND next_retry_at <= :now
        ORDER BY next_retry_at, id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
List<TranslationJob> lockPending(@Param("now") LocalDateTime now, @Param("limit") int limit);
```

`limit`은 남은 쿼터 잔량과 `gemini.batch.max-items`로 계산합니다. 잔량이 0이면 쿼리조차 실행하지 않습니다.

### 3.4 원본이 다시 바뀐 경우

잡이 대기 중인데 같은 대상의 새 이벤트가 오면, 오래된 잡의 번역 결과는 이미 폐기된 원문을 가리킵니다.

`translation_job`에 `(target_type, target_id, target_locale)` 부분 유니크를 걸 수도 있지만,
**대기 중인 이전 잡을 `SUPERSEDED`로 전이시키고 새 잡을 넣는 방식**을 씁니다.
유니크 제약으로 새 잡 INSERT를 막으면 나중 원문이 유실되고, 잡을 덮어쓰면 이력이 사라집니다.

이미 `IN_PROGRESS`인 잡은 그대로 완주시킵니다. 그 결과가 도착해 옛 번역이 저장되더라도,
뒤따라오는 새 잡의 결과가 같은 `(id, locale)` 행을 덮어씁니다. §2.1의 파티션 키 고정이 이 순서를 보장합니다.

---

## 4. 데이터 모델 (`rtc_translation`)

모든 엔티티는 `BaseTimeEntity`(`created_at` / `updated_at`)를 상속합니다.

### 4.1 테이블 목록

| 테이블 | 역할 |
|---|---|
| `translation_job` | 번역 잡 큐 + 상태 머신 |
| `translation_history` | 원문 해시 단위 번역 이력. **재사용 lookup의 핵심** |
| `glossary` | 고유명사 사전 |
| `ugc_translation` | UGC 번역 이력 ([ugc-translation-spec.md](./ugc-translation-spec.md)) |
| `inbox_event` | 소비 멱등성 |
| `outbox_event` | 결과 발행 |

조회용 `restaurant_translation` / `menu_translation`은 **이 DB에 두지 않습니다.**
food-catalog가 소유합니다 ([architecture.md §8.1](../../../architecture.md#81-번역-데이터-소유권)).

### 4.2 `translation_job`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK | |
| `event_id` | CHAR(36) | NOT NULL, INDEX | 원본 이벤트 추적 |
| `restaurant_id` | BIGINT | NOT NULL, INDEX | 배치 묶음의 단위 |
| `target_locale` | VARCHAR(10) | NOT NULL | |
| `source_locale` | VARCHAR(10) | NOT NULL | |
| `entries` | JSON | NOT NULL | 요청 `entries` 스냅샷 |
| `status` | VARCHAR(20) | NOT NULL, INDEX | §4.3 |
| `retry_count` | INT | NOT NULL, DEFAULT 0 | |
| `next_retry_at` | DATETIME | NOT NULL, INDEX | 최초에는 `now()` |
| `last_error` | VARCHAR(500) | NULL | 에러 코드 + 요약. **응답 본문 전체를 넣지 않는다** |

`INDEX idx_job_poll (status, next_retry_at)` — §3.3 폴링 쿼리가 이 인덱스를 탑니다.

`entries`를 JSON 한 컬럼에 넣는 이유는 이 데이터가 **불변 스냅샷**이라 검색·조인 대상이 아니기 때문입니다.
정규화하면 잡 하나에 자식 행 N개가 생기고, 얻는 것이 없습니다.

### 4.3 잡 상태 머신

| 상태 | 의미 | 다음 상태 |
|---|---|---|
| `PENDING` | 대기 | `IN_PROGRESS`, `SUPERSEDED` |
| `IN_PROGRESS` | 스케줄러가 선점 | `COMPLETED`, `PENDING`(재시도), `FAILED` |
| `COMPLETED` | 결과 Outbox 적재 완료 | — |
| `PENDING`(회귀) | 재시도 예약 | — |
| `FAILED` | 재시도 소진 → DLQ 격리 | — (수동 re-drive) |
| `SUPERSEDED` | 원본이 다시 바뀌어 무효 (§3.4) | — |

**`FAILED`와 쿼터 소진을 구분합니다.** 쿼터로 미뤄진 잡은 `PENDING`에 남고 `retry_count`가 오르지 않습니다.
이 구분이 없으면 하루 예산을 다 쓴 다음 도착한 정상 데이터가 3번의 쿼터 거절로 DLQ에 들어갑니다.

### 4.4 `translation_history`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK | |
| `source_hash` | CHAR(64) | NOT NULL | 정규화한 원문의 SHA-256 |
| `source_locale` | VARCHAR(10) | NOT NULL | |
| `target_locale` | VARCHAR(10) | NOT NULL | |
| `source_text` | TEXT | NOT NULL | 해시 충돌 검증 및 감사용 |
| `translated_text` | TEXT | NOT NULL | |
| `provider` | VARCHAR(50) | NOT NULL | `GEMINI` / `GLOSSARY` |
| `model` | VARCHAR(100) | NULL | `gemini-3.5-flash-lite` |
| `input_tokens` / `output_tokens` | INT | NULL | `usage`에서 기록. 유료 전환 판단 근거 |
| — | | UNIQUE `uk_history (source_hash, source_locale, target_locale)` | |

**엔티티 ID가 아니라 원문 해시가 키입니다.** 그래서 서로 다른 음식점의 "김치찌개"가 이력 1건을 공유하고,
두 번째 가게부터는 AI 호출이 0회입니다. 메뉴명 중복률이 높은 도메인이라 이 설계가 RPD 절감에 가장 크게 기여합니다.

`source_hash`는 **정규화 후** 계산합니다 — 양끝 공백 제거, 연속 공백 1개로 축약, Unicode NFC 정규화.
정규화하지 않으면 `"김치찌개 "`와 `"김치찌개"`가 다른 이력이 되어 캐시 적중률이 떨어집니다.
대소문자는 접지 않습니다. 고유명사 표기가 바뀔 수 있습니다.

`name`과 `description`은 **각각 별도 이력 행**입니다. 필드를 합치면 설명만 바뀐 경우에도 이름을 다시 번역해야 합니다.

### 4.5 `glossary`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK | |
| `source_text` | VARCHAR(255) | NOT NULL | 원문 (정규화 저장) |
| `source_locale` | VARCHAR(10) | NOT NULL | |
| `target_locale` | VARCHAR(10) | NOT NULL | |
| `translated_text` | VARCHAR(255) | NOT NULL | |
| `is_active` | BOOLEAN | NOT NULL | |
| — | | UNIQUE `uk_glossary (source_text, source_locale, target_locale)` | |

**완전일치만 적용합니다.** 부분 치환(문장 안의 "김치찌개"를 "キムチチゲ"로 바꾸기)은 하지 않습니다.
"김치찌개 정식"을 "キムチチゲ 정식"으로 만들어 절반만 번역된 문자열을 낳고, 그 상태가 이력에 저장되면
잘못된 번역이 영구히 재사용됩니다. 부분 일치가 필요하면 그 조합을 사전에 항목으로 추가합니다.

사전은 관리자 API 없이 **`data.sql` 시드로 시작**합니다. 초기 항목은 시드 메뉴 10건에 나오는 음식명입니다.
관리 화면은 번역 품질 문제가 실제로 관측된 뒤에 만듭니다.

### 4.6 `inbox_event` / `outbox_event`

AGENTS.md §4.1·§4.2를 따릅니다. 두 서비스가 같은 구조를 씁니다.

| `inbox_event` 컬럼 | 제약 |
|---|---|
| `event_id` | CHAR(36) NOT NULL |
| `consumer_group` | VARCHAR(100) NOT NULL |
| `event_type` | VARCHAR(50) NOT NULL |
| `processed_at` | DATETIME NOT NULL |
| — | UNIQUE `uk_inbox (event_id, consumer_group)` |

| `outbox_event` 컬럼 | 설명 |
|---|---|
| `event_id` | CHAR(36) NOT NULL UNIQUE |
| `aggregate_type` / `aggregate_id` | `RESTAURANT` / `12` |
| `topic` / `event_type` | `translation-results` / `TRANSLATION_COMPLETED` |
| `message_key` | `restaurant:12` (§2.1) |
| `payload` | JSON |
| `status` | `PENDING` / `PUBLISHED` / `FAILED` |
| `retry_count` / `next_retry_at` | Exponential backoff (base 1s, cap 300s, 30회 후 FAILED) |

---

## 5. 멱등성

### 5.1 왜 두 겹인가

Kafka는 at-least-once이므로 같은 이벤트가 두 번 옵니다. 번역에서 중복 소비는 **돈이 아니라 쿼터**를 먹습니다.

| 겹 | 위치 | 막는 것 |
|---|---|---|
| 1 | `inbox_event` 유니크 | 같은 `eventId` 재소비 |
| 2 | `translation_history` 유니크 | 같은 원문을 다시 AI에 보내는 것 |

1겹이 뚫려도 2겹이 AI 호출을 막습니다. 두 겹이 다른 것을 막기 때문에 둘 다 필요합니다.

### 5.2 Inbox 처리 순서

```java
@Transactional
public void consume(EventEnvelope envelope) {
    if (!inboxService.markProcessed(envelope.eventId(), CONSUMER_GROUP)) {
        log.debug("이미 처리한 이벤트 — 스킵: {}", envelope.eventId());
        return;   // ack
    }
    jobService.enqueue(envelope);
}
```

`markProcessed`는 INSERT를 시도해 `DataIntegrityViolationException`이면 `false`를 반환합니다.
**`SELECT`로 존재를 확인한 뒤 `INSERT`하지 않습니다.** 동시에 같은 이벤트가 두 개 들어오면 둘 다 통과합니다.
유니크 제약이 판정 주체여야 합니다.

Inbox INSERT와 잡 적재를 **같은 트랜잭션**에 둡니다. 나뉘면 Inbox만 커밋되고 잡 적재가 실패해
"처리했다고 기록되었지만 번역되지 않은" 이벤트가 생깁니다.

---

## 6. food-catalog 측 변경

이 파이프라인은 food-catalog 수정 없이 동작하지 않습니다. 함께 작업해야 하는 항목입니다 (AGENTS.md §10.1).

### 6.1 요청 이벤트 발행

발행 지점은 [catalog-spec.md §5.3](../food-catalog-service/catalog-spec.md#53-원본-수정-시-번역-폐기)의
**번역 폐기 지점**입니다. 이미 "번역을 버려야 하는 변경"을 판정하고 있으므로, 그 조건이 곧 "재번역이 필요한 변경"입니다.

| 시점 | 발행 |
|---|---|
| 음식점 등록 | `entries` = 음식점 1건 |
| 메뉴 등록 | `entries` = 메뉴 1건 |
| 음식점 수정 시 `name`/`description` 변경 | 번역 폐기 후 `entries` = 음식점 1건 |
| 메뉴 수정 시 `name`/`description` 변경 | 번역 폐기 후 `entries` = 메뉴 1건 |
| 가격·배달비 등만 변경 | 발행하지 않음 |

`outbox_event` INSERT는 원본 UPDATE와 **같은 트랜잭션**입니다. Dual-Write를 피하는 것이 Outbox의 목적입니다 (AGENTS.md §4.1).

### 6.2 결과 이벤트 소비

`inbox_event` 멱등성 확인 후 `Restaurant.putTranslation` / `Food.putTranslation`으로 upsert합니다.
`putTranslation`은 이미 덮어쓰기 동작이 구현되어 있고 테스트도 있습니다 (catalog-spec.md §8).

**도착한 `targetId`가 존재하지 않으면 조용히 스킵하고 ack합니다.** 번역 대기 중에 메뉴가 삭제된 정상적인 경우이고,
예외를 던지면 존재하지 않는 대상을 향해 영원히 재시도합니다.

### 6.3 초기 시드 번역

현재 `data.sql`은 일본어 번역을 일부만 넣어 폴백을 눈으로 확인하게 되어 있습니다 (catalog-spec.md §7).
**이 상태를 유지합니다.** 시드 전체를 번역해두면 폴백 경로가 검증되지 않고, 없는 번역을 채우려고
파이프라인을 돌리면 하루 예산의 상당 부분을 시드에 씁니다.

---

## 7. DLQ와 운영

### 7.1 격리 기준

| 대상 | 소진 조건 | 격리 |
|---|---|---|
| 번역 잡 | `retry_count >= 3` (쿼터 거절은 제외) | `status=FAILED` + `translation-requests.DLT` |
| Outbox 발행 | `retry_count >= 30` | `status=FAILED` |

DLT에는 **원본 Envelope 그대로** 보냅니다. `last_error`는 잡 테이블에만 남깁니다.
DLT 메시지를 가공하면 re-drive할 때 원본 토픽으로 되돌릴 수 없습니다.

### 7.2 관측

| 지표 | 이유 |
|---|---|
| `translation.job.pending` (게이지) | 쿼터 부족으로 적체되는지. 계속 증가하면 유료 전환 신호 |
| `translation.quota.remaining.daily` (게이지) | 남은 일일 예산 |
| `translation.provider.calls` (카운터, `outcome` 태그) | `success` / `quota` / `error` 분리 |
| `translation.cache.hit` (카운터, `source` 태그) | `glossary` / `history`. 절감 효과 측정 |
| `translation.job.failed` (카운터) | DLQ 유입 |

Actuator + Micrometer로 노출합니다. 별도 알림은 두지 않습니다 — 모니터링 스택 구축이 아직 미착수입니다
([tasks.md §9](../../../tasks.md)).

---

## 8. 에러 코드

`ErrorCode` enum에 추가합니다. **`i18n/locales/{ko,ja}.json`의 `error.*` 키도 함께 추가해야 합니다**
([error-handling.md §3](../../../error-handling.md#3-에러-코드-카탈로그)).

| 코드 | HTTP | 발생 조건 |
|---|---|---|
| `TRANSLATION_QUOTA_EXCEEDED` | 429 | AI 일일/분당 쿼터 소진 |
| `TRANSLATION_UNAVAILABLE` | 503 | AI API 장애 · 타임아웃 · 재시도 소진 |
| `TRANSLATION_TEXT_TOO_LONG` | 400 | 요청 텍스트가 상한 초과 |
| `UNSUPPORTED_TARGET_LOCALE` | 400 | 지원하지 않는 대상 locale |

비동기 파이프라인에서는 이 코드들이 응답으로 나가지 않고 `translation_job.last_error`에 기록됩니다.
사용자에게 나가는 것은 UGC 온디맨드 API 경로뿐입니다 ([ugc-translation-spec.md §4](./ugc-translation-spec.md)).

---

## 9. 구현 순서

앞 단계 없이 뒷 단계를 검증할 수 없는 순서입니다.

| # | 작업 | 대상 | 검증 방법 |
|---|---|---|---|
| 1 | 스캐폴딩 (§1.2) — 공통 클래스, Security, DB, Eureka, Gateway 라우팅 | translation-service | `/actuator/health` + Eureka 등록 |
| 2 | 엔티티 6개 + Repository (§4) | translation-service | `@DataJpaTest` — 유니크 제약, `SKIP LOCKED` |
| 3 | `TranslationProvider` + Gemini 어댑터 | translation-service | `MockRestServiceServer` ([gemini-provider-spec.md §8](./gemini-provider-spec.md)) |
| 4 | 쿼터 가드 + 3단 게이트 + 배치 분할 | translation-service | 단위 테스트 |
| 5 | UGC 온디맨드 API | translation-service | **AI 연동을 사람이 눈으로 확인할 수 있는 첫 지점** |
| 6 | Outbox + Publisher | translation-service | `spring-kafka-test` |
| 7 | food-catalog 요청 이벤트 발행 (§6.1) | food-catalog | Kafka UI에서 메시지 확인 |
| 8 | `RequestConsumer` + Inbox + 잡 적재 | translation-service | `translation_job` 행 생성 |
| 9 | `JobScheduler` (§3.3) | translation-service | 잡 → `translation-results` 발행 |
| 10 | food-catalog `ResultConsumer` (§6.2) | food-catalog | 등록 → 잠시 후 `Accept-Language: ja` 조회로 번역 확인 |

**5를 6~10보다 앞에 둔 이유**는 Kafka 배관 전체를 깔기 전에 Gemini 연동을 검증하기 위해서입니다.
UGC API는 요청 → AI → 응답이 한 호출로 끝나므로, 모델명·`Api-Revision`·응답 파싱이 맞는지 즉시 드러납니다.
비동기 경로로 먼저 가면 실패가 로그와 DB 컬럼 안에서만 보입니다.

---

## 10. 테스트 계획

| 대상 | 검증 |
|---|---|
| `TranslationJobTest` | 상태 전이, 쿼터 거절 시 `retry_count` 불변, `SUPERSEDED` 전이 |
| `TranslationJobRepositoryTest` (`@DataJpaTest`) | 폴링 쿼리 정렬·limit, `next_retry_at` 필터 |
| `TranslationHistoryTest` | 원문 정규화(공백·NFC) 후 해시 일치, 대소문자는 구분 |
| `InboxServiceTest` | 중복 `eventId` 스킵, 유니크 위반을 `false`로 변환 |
| `GlossaryServiceTest` | 완전일치만 적용, 부분 일치는 미적용 |
| `JobSchedulerTest` | 3단 게이트 순서, 사전·이력 히트 시 Provider 미호출, 쿼터 0이면 조회조차 안 함 |
| `RequestConsumerTest` (`spring-kafka-test`) | Envelope 역직렬화, locale별 잡 분리, 중복 이벤트 |
| food-catalog `ResultConsumerTest` | `putTranslation` upsert, 없는 `targetId` 스킵 후 ack |
| food-catalog `RestaurantServiceTest` (기존 보강) | 텍스트 변경 시에만 Outbox INSERT, 가격만 변경 시 미발행 |

`SKIP LOCKED`는 H2에서 동작이 다릅니다. 해당 테스트는 문법 검증까지만 하고, 동시 선점은 로컬 MySQL에서 수동 확인합니다.
