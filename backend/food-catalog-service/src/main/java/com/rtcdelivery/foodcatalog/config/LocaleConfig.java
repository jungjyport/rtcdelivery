package com.rtcdelivery.foodcatalog.config;

import com.rtcdelivery.foodcatalog.common.SupportedLocale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

@Configuration
public class LocaleConfig {

    /**
     * {@code Accept-Language}를 지원 목록에 맞춰 해석한다. {@code ja-JP,ja;q=0.9,en;q=0.8}처럼
     * 품질값이 붙은 헤더도 처리되며, 매칭되는 언어가 없으면 기본값 {@code ko}를 쓴다.
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SupportedLocale.asLocales());
        resolver.setDefaultLocale(Locale.of(SupportedLocale.DEFAULT));
        return resolver;
    }
}
