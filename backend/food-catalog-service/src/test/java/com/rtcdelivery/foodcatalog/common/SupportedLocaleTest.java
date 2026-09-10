package com.rtcdelivery.foodcatalog.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedLocaleTest {

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("current_지원하는_locale이면_그대로_반환한다")
    void current_supportedLocale_returnsAsIs() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);

        assertThat(SupportedLocale.current()).isEqualTo("ja");
    }

    @Test
    @DisplayName("current_지원하지_않는_locale이면_기본값_ko로_떨어진다")
    void current_unsupportedLocale_fallsBackToDefault() {
        LocaleContextHolder.setLocale(Locale.FRENCH);

        assertThat(SupportedLocale.current()).isEqualTo("ko");
    }

    @Test
    @DisplayName("current_지역_변형이_붙어도_언어_코드로_해석한다")
    void current_localeWithRegion_resolvesLanguageCode() {
        LocaleContextHolder.setLocale(Locale.JAPAN);

        assertThat(SupportedLocale.current()).isEqualTo("ja");
    }

    @Test
    @DisplayName("isSupported_ko와_ja만_지원한다")
    void isSupported_onlyKoAndJa() {
        assertThat(SupportedLocale.isSupported("ko")).isTrue();
        assertThat(SupportedLocale.isSupported("ja")).isTrue();
        assertThat(SupportedLocale.isSupported("en")).isFalse();
        assertThat(SupportedLocale.isSupported(null)).isFalse();
    }
}
