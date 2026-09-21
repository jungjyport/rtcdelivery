package com.rtcdelivery.translation.config;

import com.rtcdelivery.translation.common.SupportedLocale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SupportedLocale.asLocales());
        resolver.setDefaultLocale(Locale.of(SupportedLocale.DEFAULT));
        return resolver;
    }
}
