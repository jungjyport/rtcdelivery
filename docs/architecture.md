# RTC Delivery — System Architecture

## 1. 프로젝트 개요

RTC Delivery는 **Real Time Commerce** 방식의 다국적 음식 주문 및 딜리버리 커머스 서비스입니다.

마이크로서비스 아키텍처(MSA)를 기반으로 하며, 다국어 지원(ko, ja)을 핵심 설계 원칙으로 합니다.

### 핵심 목표

- 실시간 주문/배달 추적
- 다국어(ko, ja) 지원 — 정적 UI + 동적 콘텐츠 번역
- 확장 가능한 마이크로서비스 구조
- 이벤트 기반 비동기 처리

---

## 2. 전체 시스템 구조

```
                     ┌─────────────┐
                     │  Nuxt 3     │
                     │  Frontend   │
                     │  (port 3000)│
                     └──────┬──────┘
                            │ HTTP (REST)
                     ┌──────▼──────┐
                     │ API Gateway │
                     │ (port 8080) │
                     └──────┬──────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
     ┌──────▼──────┐ ┌─────▼──────┐ ┌──────▼──────┐
     │ Food Catalog │ │  Member    │ │   Order     │
     │   Service   │ │   Auth     │ │   Service   │
     │ (port 8081) │ │  Service   │ │ (port 8083) │
     └──────┬──────┘ │ (port 8082)│ └──────┬──────┘
            │        └────────────┘        │
            │                              │
     ┌──────▼──────┐               ┌──────▼──────┐
     │ Translation │               │  Payment    │
     │   Service   │               │   Service   │
     │  (Planned)  │               │ (port 8084) │
     └──────┬──────┘               └─────────────┘
            │
     ┌──────▼──────┐
     │   AI API    │
     │  (Planned)  │
     └─────────────┘
```

### Service Discovery

```
     ┌─────────────────┐
     │ Discovery Service│
     │ (Eureka Server)  │
     │  (port 8761)     │
     └─────────────────┘
            ▲
            │ Register / Heartbeat
     ┌──────┴──────────────────┐
     │  All Microservices      │
     └─────────────────────────┘
```

---

## 3. 서비스 목록 및 역할

### 현재 구현된 서비스 (스캐폴딩 완료)

| 서비스 | 포트 | 역할 | 상태 |
|---|---|---|---|
| **discovery-service** | 8761 | Eureka Server — 서비스 등록/탐색 | 스캐폴딩 완료 |
| **api-gateway** | 8080 | Spring Cloud Gateway — API 라우팅, 인증 필터, CORS, Rate Limiting | 스캐폴딩 완료 |
| **food-catalog-service** | 8081 | 음식점/메뉴/카테고리 관리 (CRUD), 검색 | 스캐폴딩 완료 |
| **member-auth-service** | 8082 | 회원가입/로그인, JWT 발급, 회원 정보 관리 | 스캐폴딩 완료 |
| **order-service** | 8083 | 주문 생성/관리, 배달 상태 추적, 실시간 알림 | 스캐폴딩 완료 |
| **payment-service** | 8084 | 결제 처리, 결제 내역 관리, 환불 | 스캐폴딩 완료 |

### 향후 추가 예정 서비스 (Planned)

| 서비스 | 역할 |
|---|---|
| **translation-service** | AI 기반 콘텐츠 번역, 번역 결과 저장/캐싱 |

> **원칙**: 기존 서비스를 임의로 추가/삭제하지 않습니다. Translation Service는 별도 서비스로 추가될 예정이나, 현 단계에서는 설계만 합니다.

---

## 4. 기술 스택

### Frontend
| 기술 | 버전 | 용도 |
|---|---|---|
| Nuxt | 4.5.2 | SSR 프레임워크 |
| Vue | 3.5.41 | UI 프레임워크 |
| Tailwind CSS | 최신 | 스타일링 |
| Pinia | 4.x | 상태 관리 |
| Axios | 1.x | HTTP 클라이언트 |
| @nuxtjs/i18n | 최신 | 다국어 지원 |

### Backend
| 기술 | 버전 | 용도 |
|---|---|---|
| Spring Boot | 3.4.1 | 마이크로서비스 프레임워크 |
| Java | 21 | 런타임 |
| Spring Cloud Gateway | 2024.0.0 | API Gateway |
| Spring Cloud Netflix Eureka | 2024.0.0 | Service Discovery |
| Spring Data JPA | - | ORM |
| Spring Security | - | 인증/인가 |
| Spring Kafka | - | 비동기 메시징 |
| Spring Data Redis | - | 캐싱, 세션 |

### Infrastructure
| 기술 | 용도 |
|---|---|
| MySQL 8.0 | 관계형 데이터베이스 |
| Redis 7 | 캐싱, Rate Limiting, 세션 |
| Apache Kafka (Confluent 7.5) | 비동기 이벤트 스트리밍 |
| Docker Compose | 로컬 개발 환경 |

---

## 5. 서비스 간 통신

### 동기 통신 (REST)

다음 경우에 사용합니다:

- 클라이언트 → API Gateway → 각 서비스 (사용자 요청/응답)
- 서비스 간 즉시 응답이 필요한 경우 (예: 주문 생성 시 음식점 정보 조회)

### 비동기 통신 (Kafka)

다음 경우에 사용합니다:

- 주문 상태 변경 이벤트 (Order Service → Kafka → Payment Service, Notification)
- 음식점/메뉴 등록 시 번역 요청 (Food Catalog Service → Kafka → Translation Service)
- 결제 완료 이벤트 (Payment Service → Kafka → Order Service)
- 사용자 활동 로그/분석

### Kafka 예상 토픽 (Planned)

| 토픽 | Producer | Consumer | 설명 |
|---|---|---|---|
| `order-events` | order-service | payment-service | 주문 생성/변경 이벤트 |
| `payment-events` | payment-service | order-service | 결제 결과 이벤트 |
| `translation-requests` | food-catalog-service | translation-service | 번역 요청 |
| `translation-results` | translation-service | food-catalog-service | 번역 결과 |

---

## 6. Redis 사용 계획 (Planned)

| 서비스 | 용도 |
|---|---|
| api-gateway | Rate Limiting, JWT 블랙리스트 |
| food-catalog-service | 메뉴/음식점 캐싱 |
| member-auth-service | Refresh Token 저장, 세션 관리 |
| order-service | 실시간 주문 상태 캐싱 |
| translation-service (Planned) | 번역 결과 캐싱 |

---

## 7. 데이터베이스 분리

MSA 원칙에 따라 각 서비스는 **독립적인 데이터베이스**를 사용합니다.

| 서비스 | 데이터베이스 |
|---|---|
| food-catalog-service | `rtc_food_catalog` |
| member-auth-service | `rtc_member_auth` |
| order-service | `rtc_order` |
| payment-service | `rtc_payment` |

> Discovery Service와 API Gateway는 데이터베이스를 사용하지 않습니다.

---

## 8. Translation Service 위치 (Planned)

Translation Service는 Food Catalog Service와 밀접하게 연동됩니다.

```
Food Catalog Service
        │
        │ RestaurantCreated / MenuCreated (Kafka)
        ▼
Translation Service
        │
        │ API Call
        ▼
    AI API (외부)
        │
        │ 번역 결과
        ▼
Translation DB (Translation Service 전용)
```

> 상세 설계는 [translation-system.md](./translation-system.md)를 참조하세요.

---

## 9. Docker Compose / 배포 구조

### 현재 (개발 환경)

`docker-compose-dev.yml`로 인프라 컨테이너만 관리:
- MySQL, Redis, Kafka, Zookeeper

각 Spring Boot 서비스는 IDE에서 직접 실행합니다.

### 향후 (Planned)

`docker-compose-prod.yml`에 모든 서비스를 포함:
- 각 Spring Boot 서비스의 Dockerfile 작성
- Nuxt 앱의 Dockerfile 작성
- 환경별 `.env` 파일로 설정 분리
- 향후 Kubernetes 전환 고려

---

## 10. 실시간 통신 (Planned)

주문 상태 실시간 추적을 위해 다음을 계획합니다:

- **WebSocket** (STOMP over WebSocket) — Order Service에서 클라이언트로 주문 상태 푸시
- 또는 **SSE (Server-Sent Events)** — 단방향 실시간 업데이트

> Order Service의 `build.gradle`에 `spring-boot-starter-websocket`이 이미 포함되어 있습니다.
