-- ============================================================
-- Translation Service — 고유명사 사전(glossary) 시드 데이터
-- ============================================================
-- 완전일치로만 적용되며, 시드 메뉴 10건에 대한 일본어 고유명사 표기입니다.

INSERT IGNORE INTO glossary (id, source_text, source_locale, target_locale, translated_text, is_active, created_at, updated_at) VALUES
(1,  '김치찌개',     'ko', 'ja', 'キムチチゲ',       TRUE, NOW(), NOW()),
(2,  '된장찌개',     'ko', 'ja', 'テンジャンチゲ',     TRUE, NOW(), NOW()),
(3,  '계란말이',     'ko', 'ja', 'ケランマリ',       TRUE, NOW(), NOW()),
(4,  '오마카세 런치', 'ko', 'ja', 'おまかせランチ',     TRUE, NOW(), NOW()),
(5,  '연어 초밥',     'ko', 'ja', 'サーモン寿司',       TRUE, NOW(), NOW()),
(6,  '후라이드 치킨', 'ko', 'ja', 'フライドチキン',     TRUE, NOW(), NOW()),
(7,  '양념 치킨',     'ko', 'ja', 'ヤンニョムチキン',    TRUE, NOW(), NOW()),
(8,  '떡볶이',        'ko', 'ja', 'トッポッキ',         TRUE, NOW(), NOW()),
(9,  '순대',          'ko', 'ja', 'スンデ',             TRUE, NOW(), NOW()),
(10, '튀김 모둠',     'ko', 'ja', '揚げ物盛り合わせ',    TRUE, NOW(), NOW());
