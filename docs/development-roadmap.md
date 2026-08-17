# RTC Delivery — Development Roadmap

## 1. 단계별 로드맵 개요

본 문서는 MSA 기반 Real Time Commerce (RTC) 음식 주문 및 딜리버리 서비스 구축을 위한 단계별 순차 로드맵을 정의합니다.

---

## 2. Phase 별 상세 일정 및 현황

### Phase 1: Nuxt i18n & 아키텍처 기반 확립 🟢 (Current Phase - Completed)
- [x] `@nuxtjs/i18n` 도입 (`ko`, `ja` 지원)
- [x] Nuxt 메인 페이지 i18n 적용 및 언어 선택 UI 구현
- [x] 백엔드 6개 서비스 기본 스캐폴딩 및 `application.properties` (dev/prod) 분리
- [x] Docker Compose 개발 인프라 (`docker-compose-dev.yml`) 구축
- [x] 설계 명세 문서 작성 (`architecture.md`, `internationalization.md`, `translation-system.md`, `api-conventions.md`, `development-roadmap.md`)

---

### Phase 2: Frontend 기본 Layout & UI 컴포넌트 확장 ⚪ (Planned)
- [ ] 글로벌 헤더/푸터 공통 컴포넌트화
- [ ] 반응형 카테고리/음식점 목록 UI
- [ ] 장바구니 Drawer UI 및 Pinia 상태 연동

---

### Phase 3: API Gateway & Service Discovery ⚪ (Planned)
- [ ] Eureka Server 서비스 탐색 연동 검증
- [ ] Gateway 글로벌 JWT 인증 및 라우팅 필터 세부 구현
- [ ] CORS 및 Rate Limiting (Redis) 활성화

---

### Phase 4: Member Auth Service (회원/인증) ⚪ (Planned)
- [ ] Member 엔티티, DTO, Repository, Service 구현
- [ ] 회원가입, 로그인, JWT 토큰 발급/갱신 (Refresh Token in Redis)
- [ ] Spring Security & PasswordEncoder 연동

---

### Phase 5: Food Catalog Service (음식점/메뉴) ⚪ (Planned)
- [ ] Restaurant 및 Food 엔티티, JPA Repository 구현
- [ ] 다국어 번역 지원 테이블 (`restaurant_translation`, `menu_translation`) 스키마 연동
- [ ] 카테고리/음식점/메뉴 CRUD API 구현

---

### Phase 6: Order Service (주문) ⚪ (Planned)
- [ ] Order 및 OrderItem 엔티티 설계
- [ ] 주문 생성 및 주문 상태 관리 (`PENDING`, `ACCEPTED`, `PREPARING`, `DELIVERING`, `DELIVERED`, `CANCELLED`)
- [ ] 주문 상태 변경 시 이벤트 발생

---

### Phase 7: Payment Service (결제) ⚪ (Planned)
- [ ] Payment 엔티티 및 결제 상태 관리
- [ ] 결제 승인/취소 API 가상 Mock 연동
- [ ] 주문 연동 결제 처리 파이프라인

---

### Phase 8: Kafka 기반 이벤트 연동 ⚪ (Planned)
- [ ] Kafka Producer/Consumer 구성
- [ ] `order-events` (주문 생성 -> 결제 요청 -> 주문 상태 업데이트)
- [ ] 이벤트 실패 시 Retry 및 DLQ 처리

---

### Phase 9: Redis Caching & Session ⚪ (Planned)
- [ ] 메뉴/카테고리 캐싱 (조회 성능 최적화)
- [ ] Redis 분산 락(Redisson)을 통한 재고/주문 선점 처리

---

### Phase 10: Translation Service ⚪ (Planned)
- [ ] 별도 Translation Microservice 구축
- [ ] Food Catalog 비동기 번역 이벤트 소비 (Kafka Consumer)
- [ ] DB Translation 테이블 자동 저장

---

### Phase 11: AI Translation Integration ⚪ (Planned)
- [ ] OpenAI / DeepL API 연동
- [ ] UGC (리뷰) 실시간 번역 API 구현
- [ ] 번역 결과 Redis 7일 캐싱

---

### Phase 12: Real-time Communication (WebSocket/SSE) ⚪ (Planned)
- [ ] Spring WebSocket (STOMP) 메세징 브로커 설정
- [ ] 라이더/손님 간 실시간 위치 및 주문 상태 변동 알림

---

### Phase 13: Docker Compose 통합 ⚪ (Planned)
- [ ] 각 마이크로서비스 및 Nuxt 앱 Dockerfile 작성
- [ ] `docker-compose-prod.yml` 풀 스택 컨테이너화

---

### Phase 14: 배포 & 모니터링 ⚪ (Planned)
- [ ] Spring Actuator + Prometheus + Grafana 메트릭 수집
- [ ] Zipkin/Sleuth (Micrometer Tracing)를 이용한 분산 트레이싱
