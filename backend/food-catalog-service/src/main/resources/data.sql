-- ============================================================
-- Food Catalog Service — 개발용 시드 데이터
-- ============================================================
-- 재기동 시 중복 삽입되지 않도록 PK를 명시하고 INSERT IGNORE를 쓴다.
-- 운영 프로파일에서는 spring.sql.init.mode=never로 꺼야 한다.
--
-- categories.code는 프론트엔드 i18n 키와의 계약이다.
-- frontend/nuxt-app/i18n/locales/{ko,ja}.json 의 category.* 와 일치해야 한다.

INSERT IGNORE INTO categories (id, code, name, image_url, display_order, is_active, created_at, updated_at) VALUES
(1,  'korean',    '한식',   NULL, 1,  TRUE, NOW(), NOW()),
(2,  'chinese',   '중식',   NULL, 2,  TRUE, NOW(), NOW()),
(3,  'japanese',  '일식',   NULL, 3,  TRUE, NOW(), NOW()),
(4,  'western',   '양식',   NULL, 4,  TRUE, NOW(), NOW()),
(5,  'chicken',   '치킨',   NULL, 5,  TRUE, NOW(), NOW()),
(6,  'pizza',     '피자',   NULL, 6,  TRUE, NOW(), NOW()),
(7,  'burger',    '버거',   NULL, 7,  TRUE, NOW(), NOW()),
(8,  'snack',     '분식',   NULL, 8,  TRUE, NOW(), NOW()),
(9,  'cafe',      '카페',   NULL, 9,  TRUE, NOW(), NOW()),
(10, 'dessert',   '디저트', NULL, 10, TRUE, NOW(), NOW()),
(11, 'lateNight', '야식',   NULL, 11, TRUE, NOW(), NOW()),
(12, 'healthy',   '건강식', NULL, 12, TRUE, NOW(), NOW());

-- owner_id는 member-auth-service의 members.id를 가리킨다 (FK 없음, Database-per-Service).
-- 로컬에서 점주 계정을 만든 뒤 실제 id에 맞춰 조정한다.
INSERT IGNORE INTO restaurants
(id, owner_id, category_id, name, description, address, phone_number, delivery_fee, min_order_amount, image_url, is_active, created_at, updated_at) VALUES
(1, 1, 1, '서울 김치찌개', '30년 전통 김치찌개 전문점입니다.', '서울시 강남구 테헤란로 1',   '02-1234-5678', 3000, 12000, NULL, TRUE, NOW(), NOW()),
(2, 1, 3, '스시 오마카세',  '제철 생선으로 준비하는 오마카세.',   '서울시 강남구 논현로 45',   '02-2345-6789', 4000, 30000, NULL, TRUE, NOW(), NOW()),
(3, 2, 5, '바삭 치킨',      '매일 신선한 기름으로 튀겨냅니다.',   '서울시 마포구 양화로 12',   '02-3456-7890', 2000, 16000, NULL, TRUE, NOW(), NOW()),
(4, 2, 8, '종로 분식',      '떡볶이와 순대의 정석.',              '서울시 종로구 종로 100',    '02-4567-8901',    0,  8000, NULL, TRUE, NOW(), NOW());

INSERT IGNORE INTO foods
(id, restaurant_id, name, description, price, image_url, is_sold_out, display_order, created_at, updated_at) VALUES
(1,  1, '김치찌개',     '돼지고기와 묵은지를 넣고 끓인 찌개',  9000,  NULL, FALSE, 1, NOW(), NOW()),
(2,  1, '된장찌개',     '구수한 된장에 두부와 애호박',         8500,  NULL, FALSE, 2, NOW(), NOW()),
(3,  1, '계란말이',     '부드러운 계란말이',                   7000,  NULL, TRUE,  3, NOW(), NOW()),
(4,  2, '오마카세 런치', '10피스 구성의 점심 오마카세',         55000, NULL, FALSE, 1, NOW(), NOW()),
(5,  2, '연어 초밥',     '노르웨이산 생연어 2피스',             6000,  NULL, FALSE, 2, NOW(), NOW()),
(6,  3, '후라이드 치킨', '겉은 바삭 속은 촉촉',                 18000, NULL, FALSE, 1, NOW(), NOW()),
(7,  3, '양념 치킨',     '달콤매콤한 수제 양념',                19000, NULL, FALSE, 2, NOW(), NOW()),
(8,  4, '떡볶이',        '쌀떡으로 만든 국물 떡볶이',           4500,  NULL, FALSE, 1, NOW(), NOW()),
(9,  4, '순대',          '찹쌀순대 한 접시',                    5000,  NULL, FALSE, 2, NOW(), NOW()),
(10, 4, '튀김 모둠',     '오징어 · 고구마 · 김말이',            5500,  NULL, FALSE, 3, NOW(), NOW());

-- 일부만 일본어 번역을 넣어 폴백 동작을 확인할 수 있게 한다.
-- 번역이 없는 음식점(3, 4)과 메뉴는 Accept-Language: ja 로 조회해도 한국어 원본이 내려간다.
INSERT IGNORE INTO restaurant_translation (id, restaurant_id, locale, name, description, created_at, updated_at) VALUES
(1, 1, 'ja', 'ソウルキムチチゲ', '30年伝統のキムチチゲ専門店です。', NOW(), NOW()),
(2, 2, 'ja', '寿司おまかせ',     '旬の魚でご用意するおまかせコース。', NOW(), NOW());

INSERT IGNORE INTO menu_translation (id, menu_id, locale, name, description, created_at, updated_at) VALUES
(1, 1, 'ja', 'キムチチゲ',   '豚肉と熟成キムチを煮込んだ鍋',   NOW(), NOW()),
(2, 2, 'ja', 'テンジャンチゲ', '香ばしい味噌に豆腐とズッキーニ', NOW(), NOW()),
(3, 4, 'ja', 'おまかせランチ', '10貫構成のランチおまかせ',       NOW(), NOW()),
(4, 5, 'ja', 'サーモン寿司',   'ノルウェー産生サーモン2貫',      NOW(), NOW());
