-- RTC Delivery - MySQL 초기화 스크립트
-- 각 마이크로서비스용 데이터베이스 생성

CREATE DATABASE IF NOT EXISTS `rtc_food_catalog` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `rtc_member_auth` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `rtc_order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `rtc_payment` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 사용자에게 모든 DB 접근 권한 부여
GRANT ALL PRIVILEGES ON `rtc_food_catalog`.* TO 'rtcuser'@'%';
GRANT ALL PRIVILEGES ON `rtc_member_auth`.* TO 'rtcuser'@'%';
GRANT ALL PRIVILEGES ON `rtc_order`.* TO 'rtcuser'@'%';
GRANT ALL PRIVILEGES ON `rtc_payment`.* TO 'rtcuser'@'%';
FLUSH PRIVILEGES;
