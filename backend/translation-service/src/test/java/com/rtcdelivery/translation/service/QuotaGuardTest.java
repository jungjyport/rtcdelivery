package com.rtcdelivery.translation.service;

import com.rtcdelivery.translation.config.GeminiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuotaGuardTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private GeminiProperties geminiProperties;

    @InjectMocks
    private QuotaGuard quotaGuard;

    @BeforeEach
    void setUp() {
        geminiProperties = new GeminiProperties();
        geminiProperties.getQuota().setRpm(12);
        geminiProperties.getQuota().setRpd(400);
        geminiProperties.getQuota().setZone("America/Los_Angeles");

        ReflectionTestUtils.setField(quotaGuard, "geminiProperties", geminiProperties);
        ReflectionTestUtils.setField(quotaGuard, "userDailyLimit", 20);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("RPM과 RPD가 여유 있으면 canCallGemini는 true를 반환한다")
    void canCallGemini_withinLimits_returnsTrue() {
        given(valueOperations.get(anyString())).willReturn("5");

        assertThat(quotaGuard.canCallGemini()).isTrue();
    }

    @Test
    @DisplayName("RPM이 상한에 도달하면 canCallGemini는 false를 반환한다")
    void canCallGemini_rpmExceeded_returnsFalse() {
        given(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("gemini:rpm:")))
                .willReturn("12");

        assertThat(quotaGuard.canCallGemini()).isFalse();
    }

    @Test
    @DisplayName("RPD가 상한에 도달하면 canCallGemini는 false를 반환한다")
    void canCallGemini_rpdExceeded_returnsFalse() {
        given(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("gemini:rpm:")))
                .willReturn("0");
        given(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("gemini:rpd:")))
                .willReturn("400");

        assertThat(quotaGuard.canCallGemini()).isFalse();
    }

    @Test
    @DisplayName("429 발생 시 forceConsumeDailyQuota는 RPD를 상한(400)으로 채운다")
    void forceConsumeDailyQuota_setsToLimit() {
        quotaGuard.forceConsumeDailyQuota();

        verify(valueOperations).set(org.mockito.ArgumentMatchers.startsWith("gemini:rpd:"), eq("400"));
        verify(redisTemplate).expire(org.mockito.ArgumentMatchers.startsWith("gemini:rpd:"), any(Duration.class));
    }

    @Test
    @DisplayName("사용자 UGC 상한(20회) 미만일 때 checkAndRecordUserUgcQuota는 true를 반환한다")
    void checkAndRecordUserUgcQuota_underLimit_returnsTrue() {
        given(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("ugc:quota:100:")))
                .willReturn("10");
        given(valueOperations.increment(org.mockito.ArgumentMatchers.startsWith("ugc:quota:100:")))
                .willReturn(11L);

        boolean result = quotaGuard.checkAndRecordUserUgcQuota(100L);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("사용자 UGC 상한(20회) 도달 시 checkAndRecordUserUgcQuota는 false를 반환한다")
    void checkAndRecordUserUgcQuota_limitReached_returnsFalse() {
        given(valueOperations.get(org.mockito.ArgumentMatchers.startsWith("ugc:quota:100:")))
                .willReturn("20");

        boolean result = quotaGuard.checkAndRecordUserUgcQuota(100L);
        assertThat(result).isFalse();
    }
}
