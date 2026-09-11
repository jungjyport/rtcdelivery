# nuxt-app — 점주 매장 · 메뉴 관리 화면 스펙

> **상태**: 구현 완료
> **대상**: `frontend/nuxt-app`
> **계약 방식**: 문서 기반. API 계약은 [catalog-spec.md](../food-catalog-service/catalog-spec.md) 및 [role-management-spec.md](../member-auth-service/role-management-spec.md)을 따른다.

**관련 문서**

| 문서 | 다루는 것 |
|---|---|
| [catalog-spec.md §3·§4](../food-catalog-service/catalog-spec.md#3-엔드포인트) | 매장·메뉴 쓰기 API, 소유권 검증(404), 인가 모델 |
| [role-management-spec.md](../member-auth-service/role-management-spec.md) | `ROLE_OWNER` 발급 및 역할 정책 |
| [api-client-spec.md](./api-client-spec.md) | `$api`, 토큰 주입, 401 갱신 큐 |
| [layout-spec.md](./layout-spec.md) | 글로벌 헤더 네비게이션 확장 |

---

## 1. 범위

### 1.1 이번에 구현하는 것

- **점주 권한 가드 미들웨어 (`owner.ts`)**:
  - `authStore.restoreSession()` 완료 대기
  - 비로그인 시 `/auth/login?redirect={path}`로 리다이렉트
  - 로그인 상태이나 역할이 `ROLE_OWNER` 또는 `ROLE_ADMIN`이 아니면 홈(`/`)으로 리다이렉트 및 경고
- **글로벌 헤더 네비게이션 연동 (`AppHeader.vue`)**:
  - `authStore.user.role`이 점주(`ROLE_OWNER`) 또는 관리자(`ROLE_ADMIN`)일 때 상단 네비게이션에 "매장 관리"(`nav.manageStores`) 링크 노출
- **점주 매장 목록 및 신규 등록 페이지 (`/owner/restaurants`)**:
  - 내가 등록한 매장 목록 조회 및 상태(활성/비활성) 표시
  - 신규 매장 등록 폼/모달 (`POST /api/v1/restaurants`)
  - 카테고리 선택 드롭다운, 매장명, 주소, 전화번호, 배달비, 최소주문금액, 이미지 URL
- **점주 매장 수정 및 메뉴 관리 페이지 (`/owner/restaurants/:id`)**:
  - 매장 기본정보 수정 (`PATCH /api/v1/restaurants/:id`)
  - 매장 비활성화 (삭제) (`DELETE /api/v1/restaurants/:id`)
  - 소속 메뉴 목록 조회 (`GET /api/v1/restaurants/:id/foods`)
  - 신규 메뉴 등록 폼 (`POST /api/v1/restaurants/:id/foods`)
  - 메뉴 수정 폼 (`PATCH /api/v1/restaurants/:rid/foods/:fid`)
  - 메뉴 물리 삭제 (`DELETE /api/v1/restaurants/:rid/foods/:fid`)
  - 메뉴 원클릭 품절 토글 (`PATCH`의 `isSoldOut` 변경)
- **타입 및 Composable (`app/composables/useOwnerStore.ts`)**:
  - 매장 및 메뉴 CRUD 비즈니스 로직 캡슐화

### 1.2 이번에 구현하지 않는 것

| 항목 | 사유 |
|---|---|
| 파일 직접 업로드 (S3/스토리지) | 백엔드가 현재 URL 문자열만 수신 ([catalog-spec.md §1.2](../food-catalog-service/catalog-spec.md#12-구현하지-않은-것)) |
| 다국어 직접 입력/수정 | 한국어 원본 등록 후 translation-service가 자동 번역하는 파이프라인 구조 ([catalog-spec.md §5.3](../food-catalog-service/catalog-spec.md#53-원본-수정-시-번역-폐기)) |
| 영업 시간 / 휴무일 설정 | 후속 주문/운영 도메인 확장 시 반영 |

---

## 2. 라우트 및 접근 제어

| 경로 | 파일 | 미들웨어 | 설명 |
|---|---|---|---|
| `/owner/restaurants` | `app/pages/owner/restaurants/index.vue` | `owner` | 점주 매장 목록 및 등록 |
| `/owner/restaurants/:id` | `app/pages/owner/restaurants/[id].vue` | `owner` | 매장 상세 정보 수정 + 메뉴 관리 |

### 2.1 `owner` 미들웨어 (`app/middleware/owner.ts`)

```ts
export default defineNuxtRouteMiddleware(async (to) => {
  if (import.meta.server) return

  const authStore = useAuthStore()
  await authStore.restoreSession()

  if (!authStore.isLoggedIn) {
    return navigateTo({
      path: '/auth/login',
      query: { redirect: to.fullPath },
    })
  }

  const role = authStore.user?.role
  if (role !== 'ROLE_OWNER' && role !== 'ROLE_ADMIN') {
    return navigateTo('/')
  }
})
```

---

## 3. API 계약 및 요청 스키마

백엔드는 Gateway가 검증하여 주입한 `X-User-Id`와 `X-User-Role`을 기반으로 인가합니다. `$api` 호출 시 `Authorization: Bearer {token}`이 자동으로 포함됩니다.

### 3.1 매장 등록 — `POST /restaurants`
- **권한**: `OWNER`, `ADMIN`
- **Request Body**:
  ```json
  {
    "categoryId": 1,
    "name": "홍길동 피자",
    "description": "매일 직접 반죽하는 수제 피자",
    "address": "서울시 강남구 테헤란로 123",
    "phoneNumber": "02-1234-5678",
    "deliveryFee": 3000,
    "minOrderAmount": 15000,
    "imageUrl": "https://images.unsplash.com/photo-..."
  }
  ```
- **Response `201`**: `RestaurantResponse`

### 3.2 매장 수정 — `PATCH /restaurants/:id`
- **권한**: `OWNER`, `ADMIN` (소유 점주 본인 대조)
- **Request Body**: 부분 수정 지원 (`RestaurantUpdateRequest`)
- **Response `200`**: `RestaurantResponse`
- **소유권 불일치 시**: `404 RESTAURANT_NOT_FOUND` (리소스 열거 공격 차단 목적)

### 3.3 매장 비활성화 — `DELETE /restaurants/:id`
- **권한**: `OWNER`, `ADMIN` (소유 점주 본인 대조)
- **Response `200`**: `null`

### 3.4 메뉴 등록 — `POST /restaurants/:id/foods`
- **Request Body**:
  ```json
  {
    "name": "페퍼로니 피자",
    "description": "진한 치즈와 페퍼로니의 조화",
    "price": 18900,
    "imageUrl": "https://...",
    "displayOrder": 1
  }
  ```
- **Response `201`**: `FoodResponse`

### 3.5 메뉴 수정 및 품절 토글 — `PATCH /restaurants/:rid/foods/:fid`
- **Request Body**:
  ```json
  {
    "name": "페퍼로니 피자 (수정)",
    "price": 19900,
    "soldOut": true
  }
  ```
- **Response `200`**: `FoodResponse`

### 3.6 메뉴 삭제 — `DELETE /restaurants/:rid/foods/:fid`
- **Response `200`**: `null`

---

## 4. Composable 설계 (`app/composables/useOwnerStore.ts`)

```ts
export function useOwnerStore() {
  const { $api } = useNuxtApp()
  const isSubmitting = ref(false)
  const errorMessage = ref<string | null>(null)

  // 매장 등록
  async function createRestaurant(payload: RestaurantCreateRequest): Promise<RestaurantResponse>

  // 매장 정보 수정
  async function updateRestaurant(id: number, payload: RestaurantUpdateRequest): Promise<RestaurantResponse>

  // 매장 비활성화
  async function deleteRestaurant(id: number): Promise<void>

  // 소속 메뉴 목록 조회
  async function getRestaurantFoods(restaurantId: number): Promise<FoodResponse[]>

  // 메뉴 생성
  async function createFood(restaurantId: number, payload: FoodCreateRequest): Promise<FoodResponse>

  // 메뉴 수정
  async function updateFood(restaurantId: number, foodId: number, payload: FoodUpdateRequest): Promise<FoodResponse>

  // 메뉴 삭제
  async function deleteFood(restaurantId: number, foodId: number): Promise<void>

  // 품절 상태 원클릭 토글
  async function toggleFoodSoldOut(restaurantId: number, foodId: number, currentSoldOut: boolean): Promise<FoodResponse>

  return {
    isSubmitting,
    errorMessage,
    createRestaurant,
    updateRestaurant,
    deleteRestaurant,
    getRestaurantFoods,
    createFood,
    updateFood,
    deleteFood,
    toggleFoodSoldOut,
  }
}
```

---

## 5. 화면 UI 및 UX 설계

### 5.1 점주 매장 목록 (`/owner/restaurants`)
- **헤더 영역**: "내 매장 관리" 타이틀 + "신규 매장 등록" 버튼
- **매장 카드 리스트**:
  - 매장 썸네일, 매장명, 카테고리, 등록일자
  - 배달비 및 최소주문금액 정보
  - 관리 액션 버튼: "매장 및 메뉴 관리 (`/owner/restaurants/:id`)", "비활성화"

### 5.2 매장 및 메뉴 관리 뷰 (`/owner/restaurants/:id`)
- **상단 탭**: [1. 매장 기본 정보] / [2. 메뉴 관리]
- **매장 기본 정보 탭**:
  - 매장명, 설명, 카테고리, 배달비, 최소주문금액, 주소, 전화번호 입력 폼
  - 저장하기 버튼 (`PATCH`)
  - 위험 구역(Danger Zone): 매장 비활성화 버튼
- **메뉴 관리 탭**:
  - 메뉴 추가 버튼 (모달/폼 오픈)
  - 메뉴 테이블/그리드: 순서, 이미지, 메뉴명, 가격, 품절 여부 토글 스위치, 수정/삭제 버튼
  - 품절 토글 스위치 클릭 시 즉시 `PATCH` 호출 후 UI 갱신

---

## 6. i18n 키 명세 (`owner.*`)

| 키 | ko | ja |
|---|---|---|
| `owner.title` | 점주 매장 관리 | 店舗管理 |
| `owner.myRestaurants` | 내 매장 목록 | マイ店舗一覧 |
| `owner.newRestaurant` | 새 매장 등록 | 新規店舗登録 |
| `owner.editRestaurant` | 매장 정보 수정 | 店舗情報編集 |
| `owner.deactivateRestaurant` | 매장 비활성화 | 店舗の無効化 |
| `owner.manageMenu` | 메뉴 관리 | メニュー管理 |
| `owner.addFood` | 새 메뉴 등록 | メニュー追加 |
| `owner.editFood` | 메뉴 수정 | メニュー編集 |
| `owner.deleteFood` | 메뉴 삭제 | メニュー削除 |
| `owner.foodSoldOut` | 품절 상태 | 売り切れ状態 |
| `owner.soldOutToggle` | 품절로 변경 | 売り切れにする |
| `owner.inStockToggle` | 판매중으로 변경 | 販売中にする |
| `owner.saveSuccess` | 저장이 완료되었습니다. | 保存が完了しました。 |
| `owner.deleteConfirm` | 정말 삭제하시겠습니까? | 本当に削除しますか？ |
| `nav.manageStores` | 매장 관리 | 店舗管理 |

---

## 7. 수용 기준

- [ ] 비로그인 사용자가 `/owner/restaurants` 접속 시 `/auth/login`으로 리다이렉트된다
- [ ] 일반 회원(`ROLE_USER`)이 접속 시 홈(`/`)으로 리다이렉트된다
- [ ] 점주(`ROLE_OWNER`) 로그인 시 헤더에 "매장 관리" 메뉴가 보이고 접속 가능하다
- [ ] 신규 매장 등록 폼 제출 시 유효성 검사를 거쳐 `POST /restaurants`가 호출되고 목록에 반영된다
- [ ] 매장 상세에서 메뉴 신규 등록, 정보 수정, 물리 삭제가 정상 동작한다
- [ ] 메뉴의 품절 스위치를 클릭하면 즉시 `PATCH`가 호출되어 고객 페이지에서 품절 상태로 노출된다
- [ ] 타인의 매장 ID로 직접 URL 접근 시 404 에러가 적절한 사용자 메시지로 처리된다
