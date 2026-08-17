# RTC Delivery — API Conventions

## 1. 개요

본 문서는 RTC Delivery MSA 환경에서 백엔드 API 서비스 간 규격, 응답 포맷, 예외 처리, 다국어 헤더 처리 및 URI 네이밍 규칙을 정의합니다.

---

## 2. REST API Naming Rules

1. **소문자 및 케밥 케이스(kebab-case)** 사용: `/api/v1/food-catalogs`
2. **복수형 명사 사용**: `/api/v1/restaurants`, `/api/v1/orders`
3. **버전 명시**: 모든 API는 `/api/v1/` prefix를 포함
4. **행위는 HTTP Method로 표현**:
   - `GET`: 조회
   - `POST`: 생성
   - `PUT`: 전체 수정
   - `PATCH`: 부분 수정
   - `DELETE`: 삭제

### Endpoint 예시 (Planned)

```text
GET    /api/v1/restaurants
GET    /api/v1/restaurants/{id}
POST   /api/v1/restaurants
PATCH  /api/v1/restaurants/{id}
DELETE /api/v1/restaurants/{id}

GET    /api/v1/restaurants/{restaurantId}/foods
POST   /api/v1/orders
GET    /api/v1/orders/{id}
```

---

## 3. 다국어 (Locale) 처리 규칙

클라이언트는 백엔드 API 요청 시 Header에 **`Accept-Language`**를 포함합니다.

```text
Accept-Language: ko
# 또는
Accept-Language: ja
```

- 백엔드는 `Accept-Language` 헤더를 기반으로 DB Translation 데이터를 조회하여 응답합니다.
- 헤더가 누락된 경우 기본값 `ko`로 동작합니다.

---

## 4. 공통 API 응답 구조 (Response Structure)

백엔드는 모든 응답을 `ApiResponse<T>` 래퍼 클래스로 감싸서 반환합니다.

### 성공 응답 (200 / 201)

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "id": 100,
    "name": "서울 김치찌개",
    "deliveryFee": 3000
  }
}
```

---

## 5. System Error & Status Code 처리 규칙

**중요 원칙**: 백엔드가 하드코딩된 한국어/일본어 에러 문장을 직접 반환하지 않고, **`errorCode`** 및 **`status` enum**을 내려주어 프론트엔드가 i18n 메시지로 표출하도록 설계합니다.

### 에러 응답 포맷

```json
{
  "status": 404,
  "message": "RESTAURANT_NOT_FOUND",
  "data": null
}
```

### 프론트엔드 매핑

```json
// ko.json
{
  "error": {
    "RESTAURANT_NOT_FOUND": "음식점을 찾을 수 없습니다."
  }
}

// ja.json
{
  "error": {
    "RESTAURANT_NOT_FOUND": "店舗が見つかりません。"
  }
}
```

---

## 6. Pagination, Sorting, Filtering

### Pagination & Sorting 요청

```text
GET /api/v1/restaurants?page=0&size=10&sort=rating,desc
```

### Pagination 응답 포맷

```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "content": [...],
    "page": 0,
    "size": 10,
    "totalElements": 42,
    "totalPages": 5
  }
}
```

---

## 7. 인증 및 보안 (Authentication & Security)

- 클라이언트는 로그인 성공 시 전달받은 JWT를 `Authorization` 헤더에 담아 전송합니다.

```text
Authorization: Bearer <JWT_ACCESS_TOKEN>
```

- **API Gateway**: 요청을 검증하고, 유효한 토큰일 경우 `X-User-Id`, `X-User-Role` 헤더를 하위 서비스로 전달합니다.

---

## 8. HTTP Status Codes

- `200 OK`: 요청 성공
- `201 Created`: 자원 생성 성공
- `400 Bad Request`: 요청 파라미터 검증 실패 (`VALIDATION_ERROR`)
- `401 Unauthorized`: 인증 실패 / 토큰 없음
- `403 Forbidden`: 인가 실패 / 권한 부족
- `404 Not Found`: 리소스 없음
- `500 Internal Server Error`: 서버 내부 오류
