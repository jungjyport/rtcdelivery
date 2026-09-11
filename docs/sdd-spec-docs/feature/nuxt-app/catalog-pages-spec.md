# nuxt-app — 음식점 · 메뉴 화면 스펙 (고객용 카탈로그)

> **상태**: 구현 완료
> **대상**: `frontend/nuxt-app`
> **계약 방식**: 문서 기반. API 요청/응답은 [catalog-spec.md](../food-catalog-service/catalog-spec.md)의 Swagger 어노테이션 계약을 따른다.

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [catalog-spec.md](../food-catalog-service/catalog-spec.md) | 백엔드 카탈로그 API 계약 (카테고리/음식점/메뉴) |
| [api-client-spec.md](./api-client-spec.md) | `$api`, `useApiFetch`, SSR 프리페치, 에러 언랩 |
| [internationalization.md](../../../internationalization.md) | `Accept-Language` 헤더, 정적 vs 동적 번역 분류 |
| [layout-spec.md](./layout-spec.md) | 글로벌 헤더/푸터 연동 |

---

## 1. 범위

### 1.1 이번에 구현하는 것

- **카테고리 목록/선택 바**:
  - `GET /api/v1/categories` 연동 (SSR 프리페치 또는 클라이언트 페치)
  - `code` 필드를 프론트엔드 i18n 키(`category.{code}`)로 매핑하여 다국어 지원
  - 메인 홈(`/`) 및 음식점 목록(`/restaurants`) 내 필터링 칩 연동
- **음식점 목록 페이지 (`/restaurants`)**:
  - `GET /api/v1/restaurants?categoryId=&page=&size=&sort=`
  - `useApiFetch`를 활용한 SSR 초기 로딩 및 SEO 대응
  - 카테고리 필터링, 정렬 옵션 (최신순 `id,desc`, 최소주문금액순 `minOrderAmount,asc`, 배달비순 `deliveryFee,asc`)
  - 페이지네이션 컨트롤 (`PageResponse<RestaurantResponse>`)
- **음식점 상세 및 메뉴 목록 페이지 (`/restaurants/:id`)**:
  - `GET /api/v1/restaurants/{id}` (`RestaurantDetailResponse`)
  - 음식점 히어로 배너, 기본 정보(배달비, 최소주문금액, 전화번호, 주소)
  - 메뉴 카드 그리드/리스트 (`FoodResponse`: 메뉴명, 설명, 가격, 이미지)
  - 메뉴 품절(`isSoldOut: true`) UI 처리 및 담기 비활성화
  - 장바구니 담기 액션 연계점 (클릭 시 토스트 알림, 후속 Cart Store 연결 준비)
- **통합 검색 페이지 (`/search`)**:
  - 쿼리스트링 `?q={keyword}`
  - 헤더/메인 검색바에서 엔터/클릭 시 `/search?q=...`로 이동
  - 음식점 탭 (`GET /restaurants?keyword=`) 및 메뉴 탭 (`GET /foods?keyword=`) 제공
- **타입 및 Composable**:
  - `types/catalog.ts`: DTO 타입 정의
  - `composables/useCatalog.ts`: 카탈로그 API 호출 및 상태 캡슐화
- **i18n 키 보강 (`catalog.*`)**:
  - 정렬 라벨, 배달비/최소주문금액 포맷, 빈 결과 안내, 메뉴 탭 라벨 등

### 1.2 이번에 구현하지 않는 것

| 항목 | 사유 |
|---|---|
| 장바구니 Drawer 및 담기 상태 관리 | `tasks.md` 장바구니 별도 태스크 |
| 주문서 작성 및 결제 이동 | order / payment 연동 후속 태스크 |
| 점주 매장/메뉴 등록·수정 | [owner-store-spec.md](./owner-store-spec.md)에서 정의 |
| 리뷰 및 별점 실제 데이터 | 리뷰 도메인 서비스 착수 후속 |

---

## 2. 라우트 및 네비게이션

| 경로 | 파일 | 설명 | 접근 |
|---|---|---|---|
| `/restaurants` | `app/pages/restaurants/index.vue` | 음식점 목록 (카테고리 필터, 정렬, 페이징) | 공개 |
| `/restaurants/:id` | `app/pages/restaurants/[id].vue` | 음식점 상세 정보 + 메뉴 목록 | 공개 |
| `/search` | `app/pages/search.vue` | 음식점 및 메뉴 통합 검색 결과 | 공개 |

- `AppHeader.vue`의 "맛집 찾기"(`nav.findRestaurants`) 링크를 `/restaurants`로 연결합니다.
- 메인 페이지(`index.vue`) 및 헤더의 검색창 입력 시 `/search?q={keyword}`로 라우팅합니다.

---

## 3. API 계약 (프론트 호출 명세)

`$api`의 `baseURL`이 이미 `/api/v1`을 포함하므로 클라이언트는 경로에 `/api/v1`을 붙이지 않습니다.
`$api` 및 `useApiFetch`가 요청 시 활성 locale(`ko` 또는 `ja`)을 `Accept-Language` 헤더로 자동 전송합니다.

### 3.1 카테고리 목록 — `GET /categories`

- **요청**: 파라미터 없음
- **성공 응답**: `CategoryResponse[]`
  ```ts
  interface CategoryResponse {
    id: number
    code: string // 프론트 i18n 접미사: $t('category.' + code)
    name: string // 관리용 원본 한국어 (사용자 노출용 아님)
    imageUrl: string | null
    displayOrder: number
  }
  ```

### 3.2 음식점 목록 조회 및 검색 — `GET /restaurants`

- **쿼리 파라미터**:
  - `categoryId`: number (선택)
  - `keyword`: string (선택, 원본명 + locale 번역명 동시 검색)
  - `page`: number (0-indexed, 기본 `0`)
  - `size`: number (기본 `10`, 최대 `100`)
  - `sort`: string (기본 `id,desc`, 지원: `minOrderAmount,asc`, `deliveryFee,asc`)
- **성공 응답**: `PageResponse<RestaurantResponse>`
  ```ts
  interface RestaurantResponse {
    id: number
    categoryId: number
    categoryCode: string
    name: string // Accept-Language에 따른 번역명 (부재 시 ko 원본)
    description: string | null
    address: string
    phoneNumber: string | null
    deliveryFee: number
    minOrderAmount: number
    imageUrl: string | null
    locale: string
  }
  ```

### 3.3 음식점 상세 및 메뉴 목록 — `GET /restaurants/:id`

- **경로 변수**: `id` (음식점 ID)
- **성공 응답**: `RestaurantDetailResponse`
  ```ts
  interface RestaurantDetailResponse {
    restaurant: RestaurantResponse
    foods: FoodResponse[]
  }

  interface FoodResponse {
    id: number
    restaurantId: number
    name: string // Accept-Language에 따른 번역명
    description: string | null
    price: number
    imageUrl: string | null
    soldOut: boolean // 품절 여부
    displayOrder: number
    locale: string
  }
  ```

### 3.4 메뉴 검색 (횡단 검색) — `GET /foods`

- **쿼리 파라미터**:
  - `keyword`: string (선택)
  - `restaurantId`: number (선택)
  - `page`: number, `size`: number, `sort`: string
- **성공 응답**: `PageResponse<FoodResponse>`

---

## 4. 데이터 모델 및 Composable

### 4.1 타입 정의 (`app/types/catalog.ts`)

`types/index.ts`에 산재된 구형 임시 모델을 정규화하고, `types/catalog.ts`로 분리하여 내보냅니다.

```ts
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface CategoryResponse {
  id: number
  code: string
  name: string
  imageUrl: string | null
  displayOrder: number
}

export interface RestaurantResponse {
  id: number
  categoryId: number
  categoryCode: string
  name: string
  description: string | null
  address: string
  phoneNumber: string | null
  deliveryFee: number
  minOrderAmount: number
  imageUrl: string | null
  locale: string
}

export interface FoodResponse {
  id: number
  restaurantId: number
  name: string
  description: string | null
  price: number
  imageUrl: string | null
  soldOut: boolean
  displayOrder: number
  locale: string
}

export interface RestaurantDetailResponse {
  restaurant: RestaurantResponse
  foods: FoodResponse[]
}
```

### 4.2 Composable (`app/composables/useCatalog.ts`)

```ts
export function useCatalog() {
  const { $api } = useNuxtApp()

  // 카테고리 목록 조회
  async function getCategories(): Promise<CategoryResponse[]>

  // 음식점 목록 조회 (SSR 프리페치 지원)
  function useRestaurantsFetch(query: Ref<Record<string, any>>): Promise<AsyncData<PageResponse<RestaurantResponse>>>

  // 음식점 상세 조회
  function useRestaurantDetailFetch(restaurantId: Ref<number> | number): Promise<AsyncData<RestaurantDetailResponse>>

  // 메뉴 검색
  async function searchFoods(keyword: string, page?: number): Promise<PageResponse<FoodResponse>>

  return {
    getCategories,
    useRestaurantsFetch,
    useRestaurantDetailFetch,
    searchFoods,
  }
}
```

---

## 5. 화면 설계 및 UI 컴포넌트

모든 컴포넌트는 `<script setup lang="ts">`와 Tailwind CSS 유틸리티 클래스를 사용합니다 (`<style>` 금지).

### 5.1 음식점 목록 페이지 (`/restaurants`)
- **상단 필터 바**:
  - 카테고리 가로 스크롤 칩 (전체 + 백엔드 카테고리 목록)
  - 정렬 드롭다운: 추천순(ID 내림차순), 배달비 낮은순, 최소주문금액 낮은순
- **음식점 카드 그리드**:
  - 이미지 썸네일 (누락 시 fallback 플레이스홀더 패턴)
  - 음식점 이름 (동적 번역명), 카테고리 뱃지 (`$t('category.' + code)`)
  - 배달비 및 최소주문금액 텍스트
  - 카드 클릭 시 `/restaurants/:id`로 이동
- **페이지네이션 바**:
  - 이전/다음 버튼 및 페이지 번호 목록

### 5.2 음식점 상세 페이지 (`/restaurants/:id`)
- **히어로 헤더**:
  - 매장 썸네일/배너 및 타이틀
  - 매장 상세 정보 바: 최소주문금액, 배달비, 주소, 전화번호
- **메뉴 리스트 / 그리드**:
  - 메뉴 카드: 썸네일, 메뉴명, 설명, 가격(원화/엔화 포맷)
  - 품절 처리: `soldOut === true` 시 회색 오버레이 + "품절" 뱃지 표시 및 클릭 비활성화
  - 장바구니 담기 버튼: 클릭 시 토스트 피드백 ("장바구니에 담았습니다")

### 5.3 검색 페이지 (`/search`)
- **상단 검색 헤더**: 입력 필드 + 검색 버튼 + 현재 검색어 뱃지
- **탭 네비게이션**: 음식점 탭 (`count`), 메뉴 탭 (`count`)
- **결과 목록**:
  - 음식점 탭: 음식점 카드 목록
  - 메뉴 탭: 메뉴 카드 목록 (소속 음식점 ID 링크 포함)
  - 결과 없을 시 안내 뷰 (`catalog.searchEmpty`)

---

## 6. i18n 키 명세 (`catalog.*`)

`i18n/locales/{ko,ja}.json`에 아래 키를 추가합니다.

| 키 | ko | ja |
|---|---|---|
| `catalog.title` | 맛집 탐색 | お店を探す |
| `catalog.allCategories` | 전체 | すべて |
| `catalog.sort.latest` | 최신순 | 新着順 |
| `catalog.sort.deliveryFee` | 배달비 낮은순 | 配達料が安い順 |
| `catalog.sort.minOrder` | 최소주문금액순 | 最低注文金額順 |
| `catalog.deliveryFee` | 배달비 | 配達料 |
| `catalog.minOrderAmount` | 최소주문 | 最低注文 |
| `catalog.freeDelivery` | 무료배달 | 配送料無料 |
| `catalog.currency` | 원 | 円 |
| `catalog.soldOut` | 품절 | 売り切れ |
| `catalog.addToCart` | 담기 | カートに追加 |
| `catalog.addedToCart` | 장바구니에 담았습니다. | カートに追加しました。 |
| `catalog.searchTitle` | 검색 결과 | 検索結果 |
| `catalog.searchTabRestaurants` | 음식점 | レストラン |
| `catalog.searchTabFoods` | 메뉴 | メニュー |
| `catalog.searchEmpty` | 검색 결과가 없습니다. | 検索結果がありません。 |
| `catalog.restaurantInfo` | 매장 정보 | 店舗情報 |
| `catalog.address` | 주소 | 住所 |
| `catalog.phoneNumber` | 전화번호 | 電話番号 |

---

## 7. 수용 기준

- [ ] `/restaurants` 접속 시 카테고리 목록과 음식점 목록이 SSR로 렌더링된다
- [ ] 카테고리 칩 클릭 시 해당 카테고리(`categoryId`)로 필터링된 음식점 목록이 로드된다
- [ ] 정렬 변경 시 URL 쿼리와 함께 목록이 재정렬된다
- [ ] 음식점 카드 클릭 시 `/restaurants/:id`로 이동하여 상세 정보와 메뉴 목록이 정상 노출된다
- [ ] 언어를 `ja`로 변경 시, 음식점/메뉴 번역이 존재하는 항목은 일본어로 표시되고 없는 항목은 한국어로 정상 폴백된다
- [ ] 품절된 메뉴는 시각적으로 비활성화되고 담기 버튼이 차단된다
- [ ] 검색창에서 키워드 검색 시 `/search?q={keyword}`로 이동하여 음식점 및 메뉴 검색 결과가 탭별로 분리 표시된다
