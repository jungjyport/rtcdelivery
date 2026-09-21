package com.rtcdelivery.translation.service;

import com.rtcdelivery.translation.config.GeminiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaGuard {

    private final StringRedisTemplate redisTemplate;
    private final GeminiProperties geminiProperties;

    @Value("${translation.ugc.daily-limit-per-user:20}")
    private int userDailyLimit;

    private static final DateTimeFormatter RPM_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter RPD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public boolean canCallGemini() {
        String rpmKey = getRpmKey();
        String rpdKey = getRpdKey();

        String rpmVal = redisTemplate.opsForValue().get(rpmKey);
        int rpm = rpmVal != null ? Integer.parseInt(rpmVal) : 0;
        if (rpm >= geminiProperties.getQuota().getRpm()) {
            log.warn("Local rate limit: RPM exceeded ({}/{})", rpm, geminiProperties.getQuota().getRpm());
            return false;
        }

        String rpdVal = redisTemplate.opsForValue().get(rpdKey);
        int rpd = rpdVal != null ? Integer.parseInt(rpdVal) : 0;
        if (rpd >= geminiProperties.getQuota().getRpd()) {
            log.warn("Local rate limit: RPD exceeded ({}/{})", rpd, geminiProperties.getQuota().getRpd());
            return false;
        }

        return true;
    }

    public void recordGeminiCall() {
        String rpmKey = getRpmKey();
        String rpdKey = getRpdKey();

        Long rpm = redisTemplate.opsForValue().increment(rpmKey);
        if (rpm != null && rpm == 1) {
            redisTemplate.expire(rpmKey, Duration.ofSeconds(120));
        }

        Long rpd = redisTemplate.opsForValue().increment(rpdKey);
        if (rpd != null && rpd == 1) {
            redisTemplate.expire(rpdKey, Duration.ofHours(48));
        }
    }

    public void forceConsumeDailyQuota() {
        String rpdKey = getRpdKey();
        log.warn("Force consuming daily Gemini quota due to 429: key={}", rpdKey);
        redisTemplate.opsForValue().set(rpdKey, String.valueOf(geminiProperties.getQuota().getRpd()));
        redisTemplate.expire(rpdKey, Duration.ofHours(48));
    }

    public int getRemainingDailyQuota() {
        String rpdKey = getRpdKey();
        String rpdVal = redisTemplate.opsForValue().get(rpdKey);
        int current = rpdVal != null ? Integer.parseInt(rpdVal) : 0;
        return Math.max(0, geminiProperties.getQuota().getRpd() - current);
    }

    public boolean checkAndRecordUserUgcQuota(Long userId) {
        if (userId == null) return false;
        String key = getUserUgcKey(userId);

        String currentVal = redisTemplate.opsForValue().get(key);
        int current = currentVal != null ? Integer.parseInt(currentVal) : 0;
        if (current >= userDailyLimit) {
            log.warn("User UGC daily quota exceeded: userId={}, current={}/{}", userId, current, userDailyLimit);
            return false;
        }

        Long next = redisTemplate.opsForValue().increment(key);
        if (next != null && next == 1) {
            redisTemplate.expire(key, Duration.ofHours(48));
        }
        return true;
    }

    private String getRpmKey() {
        ZoneId zone = ZoneId.of(geminiProperties.getQuota().getZone());
        String minuteStr = ZonedDateTime.now(zone).format(RPM_FORMATTER);
        return "gemini:rpm:" + minuteStr;
    }

    private String getRpdKey() {
        ZoneId zone = ZoneId.of(geminiProperties.getQuota().getZone());
        String dateStr = ZonedDateTime.now(zone).format(RPD_FORMATTER);
        return "gemini:rpd:" + dateStr;
    }

    private String getUserUgcKey(Long userId) {
        ZoneId zone = ZoneId.of(geminiProperties.getQuota().getZone());
        String dateStr = ZonedDateTime.now(zone).format(RPD_FORMATTER);
        return "ugc:quota:" + userId + ":" + dateStr;
    }
}
