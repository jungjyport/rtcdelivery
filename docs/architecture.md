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
                     │  Nuxt 4     │
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
| `$fetch` (ofetch) | Nuxt 내장 | HTTP 클라이언트 — 자체 API Client로 래핑 |
| @nuxtjs/i18n | 최신 | 다국어 지원 |

> HTTP 통신은 Nuxt에 내장된 `$fetch`를 래핑한 API Client(`app/utils/api-client.ts`)로 일원화합니다.
> Access Token 주입, `Accept-Language` 주입, `ApiResponse` 언랩, 401 토큰 갱신 큐를 이 계층이 담당합니다.
> 상세 계약은 [api-client-spec.md](./sdd-spec-docs/feature/nuxt-app/api-client-spec.md)를 참조하세요.

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
| translation-service (Planned) | `rtc_translation` |

> Discovery Service와 API Gateway는 데이터베이스를 사용하지 않습니다.

---

## 8. Translation Service 위치 (Planned)

Translation Service는 Food Catalog Service와 밀접하게 연동됩니다.

```
Food Catalog Service
        │
        │ ① RestaurantCreated / MenuCreated
        │    (Kafka: translation-requests, Outbox 경유)
        ▼
Translation Service
        │
        │ ② API Call
        ▼
    AI API (외부)
        │
        │ ③ 번역 결과
        ▼
Translation DB (translation-service 전용)
        │
        │ ④ TranslationCompleted (Kafka: translation-results)
        ▼
Food Catalog Service
        │
        └─► ⑤ restaurant_translation / menu_translation upsert
               (food-catalog DB, Inbox 멱등성 적용)
```

### 8.1 번역 데이터 소유권

번역 데이터는 **두 곳에 나뉘어** 저장되며 역할이 다릅니다.

| 위치 | 저장 대상 | 역할 |
|---|---|---|
| `rtc_translation` (translation-service) | 번역 이력, 고유명사 사전(Glossary), UGC 번역 | 번역 도메인의 원본. 재번역·감사·비용 추적의 기준 |
| `rtc_food_catalog` (food-catalog-service) | `restaurant_translation`, `menu_translation` | 조회 전용 복제본. 목록/상세 API가 JOIN 한 번으로 읽는다 |

조회 경로는 food-catalog 단독으로 완결됩니다. 번역이 아직 도착하지 않았거나 실패한 경우 원본 언어(`ko`)로 폴백하므로, translation-service가 내려가 있어도 카탈로그 조회는 정상 동작합니다.

> **왜 나누는가**: 음식점 목록 20건을 `ja`로 내려줄 때 번역을 translation-service에 물어보면 요청마다 서비스 간 호출이 발생합니다. Database-per-Service 원칙상 Cross-DB JOIN으로 우회할 수도 없습니다. 그래서 조회에 필요한 번역만 food-catalog DB로 복제하고, `translation-results` 토픽이 그 동기화를 담당합니다.

> 상세 설계는 [translation-system.md](./translation-system.md)를 참조하세요.

---

## 9. Docker Compose / 배포 구조

### 현재 (개발 환경)

`docker-compose-dev.yml`로 인프라와 애플리케이션을 함께 띄울 수 있습니다.

- Redis, Kafka, Zookeeper
- discovery / api-gateway / 도메인 서비스 4개 / nuxt-app
- **MySQL은 Compose에 포함하지 않습니다.** 호스트 PC의 MySQL에 원격 IP로 접속합니다. 컨테이너도 같은 JDBC URL을 쓰면 되므로 `host.docker.internal`은 쓰지 않습니다.
- 컨테이너 → 호스트 DB 접속을 위해 MySQL은 `0.0.0.0`에 바인딩하고, `infra/mysql/init/01-init-databases.sql`로 DB·권한을 준비합니다.
- JWT PEM은 각 서비스 `src/main/resources/keys/`에 두고 볼륨으로 마운트합니다.
- 백엔드 설정은 `application.properties`(기본값)와 `application-dev.properties`(Compose 네트워크 호스트명)로 나눕니다. Compose는 `SPRING_PROFILES_ACTIVE=dev`만 지정합니다.

IDE에서 서비스를 직접 실행할 때는 인프라만 올리면 됩니다.

```bash
docker compose -f docker-compose-dev.yml up redis kafka zookeeper
```

### 향후 (Planned)

`docker-compose-prod.yml`에 운영용 이미지·설정을 분리합니다.
- 환경별 `.env` 파일로 설정 분리
- 향후 Kubernetes 전환 고려

---

## 10. 실시간 통신 (Planned)

주문 상태 실시간 추적을 위해 다음을 계획합니다:

- **WebSocket** (STOMP over WebSocket) — Order Service에서 클라이언트로 주문 상태 푸시
- 또는 **SSE (Server-Sent Events)** — 단방향 실시간 업데이트

> Order Service의 `build.gradle`에 `spring-boot-starter-websocket`이 이미 포함되어 있습니다.
