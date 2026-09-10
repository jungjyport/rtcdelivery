# food-catalog-service — 카탈로그 스펙

> **계약 방식**: Swagger (Code-first). 확정된 API 계약의 진실의 원천은 Controller/DTO의 springdoc 어노테이션이며,
> 이 문서는 어노테이션으로 표현되지 않는 **설계 결정과 그 근거**를 남기기 위한 것입니다 ([AGENTS.md §2.3](../../../AGENTS.md)).

> ⚠️ **이 문서는 구현 이후에 작성되었습니다.** SDD 원칙상 스펙이 선행해야 하지만, 설계 논의가 대화로 진행되어
> 순서가 뒤집혔습니다. 내용은 구현된 코드와 일치하며, 이후 변경은 스펙 → 코드 순서를 따릅니다.

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [architecture.md §8.1](../../../architecture.md#81-번역-데이터-소유권) | 번역 테이블의 소유권 분리 |
| [translation-system.md](../../../translation-system.md) | 번역 파이프라인 전체 설계 |
| [internationalization.md §2](../../../internationalization.md#2-번역-유형-분류) | 번역 유형 분류 (정적 UI vs 동적 데이터) |
| [api-conventions.md §6·§7](../../../api-conventions.md#6-pagination-sorting-filtering) | 페이지네이션 포맷, 인증 헤더 |
| [role-management-spec.md](../member-auth-service/role-management-spec.md) | `ROLE_OWNER`의 발급·변경 |

---

## 1. 범위

### 1.1 구현한 것

- `categories` / `restaurants` / `foods` 및 번역 테이블 2개
- 카테고리 · 음식점 · 메뉴 CRUD, 페이지네이션 · 정렬
- 음식점 · 메뉴 검색 (원본명 + 번역명)
- `Accept-Language` 기반 조회와 원본(`ko`) 폴백
- Gateway 헤더 기반 인가 + 소유권 검증
- 개발용 시드 데이터

### 1.2 구현하지 않은 것

| 항목 | 사유 |
|---|---|
| 번역 요청 이벤트 발행 (Outbox) | translation-service 착수와 함께. 원본 수정 시 번역을 폐기하는 지점(§5.3)이 이벤트 발행 자리다 |
| `translation-results` 소비 | 위와 동일. 그때까지 번역 데이터는 시드로만 존재한다 |
| Redis 캐싱 | 조회 경로가 단일 DB로 완결되므로 후속으로 미룬다 |
| 이미지 업로드 | 현재는 URL 문자열만 받는다 |
| 평점 · 영업시간 | 주문/리뷰 도메인 착수 후 |

---

## 2. 데이터 모델

모든 엔티티는 `BaseTimeEntity`(`created_at` / `updated_at`)를 상속합니다.

### 2.1 테이블 목록

| 테이블 | 소유 | 설명 |
|---|---|---|
| `categories` | food-catalog | 음식 카테고리. **번역 테이블 없음** (§2.2) |
| `restaurants` | food-catalog | 음식점. 원본 텍스트는 `ko` |
| `foods` | food-catalog | 메뉴. 원본 텍스트는 `ko` |
| `restaurant_translation` | food-catalog | 음식점의 locale별 번역 (조회용) |
| `menu_translation` | food-catalog | 메뉴의 locale별 번역 (조회용) |

번역 테이블이 translation-service가 아니라 이 서비스에 있는 이유는 [architecture.md §8.1](../../../architecture.md#81-번역-데이터-소유권)을 참조하세요.
요약하면 조회 API가 본체와 JOIN해야 하는데 Database-per-Service에서는 Cross-DB JOIN이 불가능하기 때문입니다.

### 2.2 `categories`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK | |
| `code` | VARCHAR(50) | NOT NULL, UNIQUE | 프론트 i18n 키 접미사 |
| `name` | VARCHAR(50) | NOT NULL | 관리용 한국어 이름 |
| `image_url` | VARCHAR(500) | NULL | |
| `display_order` | INT | NOT NULL | 목록 정렬 |
| `is_active` | BOOLEAN | NOT NULL | 비활성 시 목록에서 제외 |

**카테고리에 번역 테이블을 두지 않는 이유**: [internationalization.md §2](../../../internationalization.md#2-번역-유형-분류)의 분류상
카테고리는 개수가 적고 고정적이라 **정적 UI 텍스트**에 해당합니다. `i18n/locales/{ko,ja}.json`에 이미
`category.korean` / `category.japanese` 같은 키가 존재하므로, 백엔드는 `code`만 내려주고 프론트가 `$t('category.' + code)`로 표출합니다.

> 따라서 `code`는 **프론트와의 계약**입니다. 배포된 `code`를 바꾸면 프론트가 키를 찾지 못해 원문이 그대로 노출되므로,
> `PATCH`에서 `code`를 변경 대상에서 제외했습니다. 새 카테고리를 추가할 때는 두 locale JSON에 키를 함께 추가해야 합니다.

### 2.3 `restaurants`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK | |
| `owner_id` | BIGINT | NOT NULL, INDEX | `members.id`. **FK 없음** |
| `category_id` | BIGINT | NOT NULL, FK, INDEX | |
| `name` | VARCHAR(255) | NOT NULL | 원본(`ko`) |
| `description` | TEXT | NULL | 원본(`ko`) |
| `address` | VARCHAR(255) | NOT NULL | |
| `phone_number` | VARCHAR(30) | NULL | |
| `delivery_fee` | INT | NOT NULL | |
| `min_order_amount` | INT | NOT NULL | |
| `image_url` | VARCHAR(500) | NULL | |
| `is_active` | BOOLEAN | NOT NULL | 삭제는 비활성화로 처리 |

`owner_id`에 FK를 걸지 않는 것은 회원 데이터의 소유자가 member-auth-service이기 때문입니다 (Database-per-Service).
정합성은 애플리케이션 레벨에서만 보장합니다.

### 2.4 `foods`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK | |
| `restaurant_id` | BIGINT | NOT NULL, FK, INDEX | |
| `name` | VARCHAR(255) | NOT NULL | 원본(`ko`) |
| `description` | TEXT | NULL | 원본(`ko`) |
| `price` | INT | NOT NULL | |
| `image_url` | VARCHAR(500) | NULL | |
| `is_sold_out` | BOOLEAN | NOT NULL | |
| `display_order` | INT | NOT NULL | |

메뉴는 **자체 소유권을 갖지 않습니다.** 인가는 소속 음식점의 `owner_id`를 따릅니다.

### 2.5 번역 테이블

| 테이블 | 유니크 제약 | 컬럼 |
|---|---|---|
| `restaurant_translation` | (`restaurant_id`, `locale`) | `name` NOT NULL, `description` TEXT |
| `menu_translation` | (`menu_id`, `locale`) | `name` NOT NULL, `description` TEXT |

> 엔티티명은 `FoodTranslation`이지만 테이블명은 `menu_translation`입니다.
> 스펙 문서가 도메인 용어로 "menu"를 쓰기 때문에 테이블명은 그쪽에 맞췄습니다.

엔티티에서는 `Map<String, ...>`(키 = locale)으로 매핑하고, `@BatchSize(size = 100)`을 붙였습니다.
목록 조회에서 컬렉션에 fetch join을 걸면 Hibernate가 페이지네이션을 메모리에서 처리하므로,
그 대신 IN 절 배치 로딩으로 N+1을 막습니다.

---

## 3. 엔드포인트

Gateway가 `/api/v1/categories/**`, `/api/v1/restaurants/**`, `/api/v1/foods/**`를 이 서비스로 라우팅합니다.

| # | Method | Path | 접근 | 설명 |
|---|---|---|---|---|
| 1 | GET | `/api/v1/categories` | 공개 | 활성 카테고리 목록 |
| 2 | POST | `/api/v1/categories` | `ADMIN` | 카테고리 생성 |
| 3 | PATCH | `/api/v1/categories/{id}` | `ADMIN` | 카테고리 수정 (`code` 제외) |
| 4 | DELETE | `/api/v1/categories/{id}` | `ADMIN` | 비활성화 |
| 5 | GET | `/api/v1/restaurants` | 공개 | 목록 / 검색 (페이지네이션) |
| 6 | GET | `/api/v1/restaurants/{id}` | 공개 | 상세 + 메뉴 목록 |
| 7 | POST | `/api/v1/restaurants` | `OWNER` `ADMIN` | 등록 |
| 8 | PATCH | `/api/v1/restaurants/{id}` | `OWNER` `ADMIN` + 소유권 | 수정 |
| 9 | DELETE | `/api/v1/restaurants/{id}` | `OWNER` `ADMIN` + 소유권 | 비활성화 |
| 10 | GET | `/api/v1/restaurants/{id}/foods` | 공개 | 메뉴 목록 |
| 11 | POST | `/api/v1/restaurants/{id}/foods` | `OWNER` `ADMIN` + 소유권 | 메뉴 등록 |
| 12 | PATCH | `/api/v1/restaurants/{rid}/foods/{fid}` | `OWNER` `ADMIN` + 소유권 | 메뉴 수정 |
| 13 | DELETE | `/api/v1/restaurants/{rid}/foods/{fid}` | `OWNER` `ADMIN` + 소유권 | 메뉴 삭제 |
| 14 | GET | `/api/v1/foods` | 공개 | 메뉴 검색 (음식점 횡단) |

요청/응답 스키마는 `/swagger-ui`를 참조하세요. 응답은 모두 `ApiResponse<T>`로 감싸며,
목록은 `PageResponse<T>`(`content` / `page` / `size` / `totalElements` / `totalPages`)를 씁니다.

**삭제 정책**

| 리소스 | 방식 | 사유 |
|---|---|---|
| 카테고리 | 비활성화 | 음식점이 참조한다 |
| 음식점 | 비활성화 | 주문 이력이 참조한다 |
| 메뉴 | 물리 삭제 | 주문 이력이 이름·가격 스냅샷을 보관하는 것을 전제로 한다 |

---

## 4. 인가 설계

### 4.1 신뢰 모델

Gateway가 JWT를 검증하고 주입한 `X-User-Id` / `X-User-Role` 헤더를 신뢰합니다.
`HeaderAuthenticationFilter`가 이 헤더를 `SecurityContext`로 승격시킵니다 (member-auth의 동일 클래스를 옮긴 것).

> **이 필터가 없으면 `SecurityContext`가 비어 `@PreAuthorize`가 모든 요청을 거부합니다.**
> 그리고 이 신뢰는 네트워크 격리에 의존하므로, 운영에서 8081 포트를 외부에 노출하면 누구나 헤더를 위조할 수 있습니다.

### 4.2 인가는 두 단계로 나눈다

| 단계 | 담당 | 판정 |
|---|---|---|
| 필터 (`authorizeHttpRequests`) | Spring Security 필터 체인 | **인증 여부만.** 조회는 `permitAll`, 나머지는 `authenticated` |
| 메서드 (`@PreAuthorize`) | 메서드 시큐리티 | **역할** |
| 서비스 | `RestaurantService.findOwned` | **소유권** |

**필터 레벨을 생략하고 역할 검사만 두면 익명 사용자도 403을 받습니다.**
`GlobalExceptionHandler`가 `AccessDeniedException`을 먼저 잡아버려,
`ExceptionTranslationFilter`가 하는 익명(→401) / 인증됨(→403) 구분이 적용되지 않기 때문입니다.

| 상황 | 응답 |
|---|---|
| 비로그인 + 쓰기 요청 | 401 `UNAUTHORIZED` |
| `ROLE_USER` + 쓰기 요청 | 403 `FORBIDDEN` |
| `ROLE_OWNER` + 남의 가게 수정 | 404 `RESTAURANT_NOT_FOUND` (§4.4) |

### 4.3 `RoleHierarchy`를 쓰지 않는다

회원은 역할을 하나만 갖고, 계층을 두지 않습니다.

```java
@PreAuthorize("isAuthenticated()")            // 로그인 사용자 전체 — 점주·관리자 포함
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")  // 가게·메뉴 쓰기
@PreAuthorize("hasRole('ADMIN')")             // 카테고리 관리
```

"점주도 일반 사용자 기능을 쓸 수 있어야 한다"는 요구는 대부분 "로그인만 했으면 된다"는 뜻이므로
`isAuthenticated()`로 해소됩니다. 계층이 필요한 자리가 사실상 남지 않아, 계층 빈을 서비스마다 복제하는
비용 대신 역할을 명시적으로 나열하는 쪽을 택했습니다.

> **비용**: 나중에 `ROLE_RIDER`가 추가되면 `hasAnyRole` 목록을 전부 훑어야 하고, 하나 빠뜨리면 그 엔드포인트만 막힙니다.
> 대부분을 `isAuthenticated()`로 쓰면 훑을 자리가 적어 현 규모에서는 감당 가능하다고 판단했습니다.

### 4.4 소유권 위반은 403이 아니라 404

`hasAnyRole('OWNER','ADMIN')`은 "점주인가"만 판정하므로, 점주 A가 점주 B의 가게를 수정하는 것을 막지 못합니다.
따라서 서비스 계층에서 `restaurant.ownerId`와 `X-User-Id`를 대조합니다.

불일치 시 **404 `RESTAURANT_NOT_FOUND`** 를 반환합니다. 403을 주면 "그 ID의 음식점이 존재한다"는 사실이
노출되어 ID를 훑으며 리소스를 열거할 수 있기 때문입니다.

관리자(`admin() == true`)는 소유권 검사를 통과시킵니다.

메뉴는 두 겹으로 막습니다.

1. 소속 음식점의 소유권 확인 (`findOwned`)
2. `findByIdAndRestaurantId`로 조회 — 다른 음식점의 메뉴 ID를 경로에 끼워 넣는 것을 막는다

---

## 5. 다국어 조회

### 5.1 locale 판정

`AcceptHeaderLocaleResolver`에 지원 목록(`ko`, `ja`)과 기본값(`ko`)을 지정합니다.
`ja-JP,ja;q=0.9,en;q=0.8`처럼 품질값이 붙은 헤더도 처리되며, 매칭되는 언어가 없으면 `ko`로 떨어집니다.

컨트롤러가 `SupportedLocale.current()`로 코드를 뽑아 서비스에 인자로 넘깁니다.
서비스가 `LocaleContextHolder`를 직접 읽지 않는 이유는 테스트에서 locale을 명시적으로 주기 위해서입니다.

### 5.2 폴백

```java
public String resolveName(String locale) {
    RestaurantTranslation t = this.translations.get(locale);
    return (t != null && t.getName() != null) ? t.getName() : this.name;
}
```

번역이 없으면 원본(`ko`)을 반환합니다. 그 결과 **translation-service가 내려가 있어도 카탈로그 조회는 정상 동작합니다.**
응답에는 실제 적용된 `locale` 필드를 함께 내려, 클라이언트가 번역 여부를 알 수 있게 합니다.

### 5.3 원본 수정 시 번역 폐기

`name` 또는 `description`이 바뀌면 기존 번역은 옛 원문을 가리키게 되므로 **모두 삭제**합니다.
재번역 결과가 도착할 때까지는 §5.2에 따라 원본으로 폴백됩니다.

가격이나 배달비만 바꾼 경우에는 번역을 유지합니다.

> 이 지점이 향후 **번역 요청 이벤트(Outbox INSERT)를 발행할 자리**입니다.

---

## 6. 검색

`GET /api/v1/restaurants?keyword=`, `GET /api/v1/foods?keyword=`는 원본명과 **요청 locale의 번역명**을 함께 검색합니다.
공백만 있는 키워드는 조건에서 제외합니다.

번역명 검색에 컬렉션 join 대신 `EXISTS` 서브쿼리를 씁니다.

```sql
AND (:keyword IS NULL
     OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
     OR EXISTS (SELECT 1 FROM RestaurantTranslation rt
                WHERE rt.restaurant = r AND rt.locale = :locale
                  AND LOWER(rt.name) LIKE LOWER(CONCAT('%', :keyword, '%'))))
```

join을 쓰면 번역 행 수만큼 결과가 중복되어 `DISTINCT`가 필요해지고,
그러면 Spring Data가 파생하는 count 쿼리와 페이지네이션이 어긋납니다.

**정렬·페이지 크기**

- 기본값: `size=10`, `sort=id,desc`
- `spring.data.web.pageable.max-page-size=100` — 상한이 없으면 `?size=100000` 한 번으로 전체 테이블을 긁어갈 수 있습니다
- 존재하지 않는 필드로 정렬하면 `PropertyReferenceException`이 발생하므로, `GlobalExceptionHandler`에서 400 `INVALID_PARAMETER`로 매핑했습니다 (기본 동작은 500)

---

## 7. 개발용 시드 데이터

`spring.jpa.hibernate.ddl-auto=update` + `src/main/resources/data.sql`.

- `spring.jpa.defer-datasource-initialization=true` — 없으면 Hibernate가 테이블을 만들기 전에 `data.sql`이 실행됩니다
- PK를 명시하고 `INSERT IGNORE`를 써서 재기동해도 중복되지 않습니다
- 카테고리 12건(프론트 i18n 키와 일치) · 음식점 4건 · 메뉴 10건
- 일본어 번역은 **일부에만** 넣어 폴백 동작을 눈으로 확인할 수 있게 했습니다
- `application-prod.properties`에서 `spring.sql.init.mode=never`로 끕니다
- 테스트에서는 `src/test/resources/application.properties`가 `never`로 덮어씁니다 (`INSERT IGNORE`가 MySQL 전용 구문이라 H2에서 실패)

`owner_id`는 1과 2로 넣었으므로, 로컬 점주 계정의 실제 `members.id`에 맞춰 조정해야 합니다.

---

## 8. 테스트

JaCoCo 기준 **라인 80.6% / 브랜치 73.8%** (총 69개). `gradle check`에 라인 70% 게이트를 연결했습니다.
설정 · DTO · 번역 엔티티는 로직이 없어 집계에서 제외합니다.

| 클래스 | 검증 대상 |
|---|---|
| `RestaurantTest` / `FoodTest` | 번역 폴백, `putTranslation` 덮어쓰기, 부분 수정 시 null 필드 유지 |
| `CategoryServiceTest` | 코드 중복, 비활성 카테고리 조회, 물리 삭제하지 않음 |
| `RestaurantServiceTest` | 소유권 위반 404, 관리자 우회, 원본 수정 시 번역 폐기 |
| `FoodServiceTest` | 음식점 경유 소유권, 다른 음식점 메뉴 ID 차단 |
| `RestaurantRepositoryTest` (`@DataJpaTest` + H2) | 검색 JPQL — 번역명 매칭, 비활성 제외, 번역 다건 시 중복 없음 |
| `RestaurantControllerTest` (`@WebMvcTest`) | **401 vs 403 구분**, `Accept-Language` 전달, 에러 응답의 `data` 키 유지 |
| `ActorTest` / `SupportedLocaleTest` | 헤더 → 주체 변환, locale 폴백 |

---

## 9. 남은 작업

| 작업 | 연결 지점 |
|---|---|
| 번역 요청 이벤트 발행 | §5.3의 번역 폐기 지점에 Outbox INSERT 추가 |
| `translation-results` 소비 | Inbox 멱등성 + `putTranslation` 호출 |
| Redis 캐싱 | 조회 경로 |

진행 상태는 [tasks.md](../../../tasks.md)를 따릅니다.
