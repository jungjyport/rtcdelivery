package com.rtcdelivery.translation.common;

import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 지원 locale과 현재 요청의 locale 판정.
 */
public final class SupportedLocale {

    public static final String DEFAULT = "ko";

    private static final Set<String> CODES = Set.of("ko", "ja");

    private SupportedLocale() {
    }

    public static List<Locale> asLocales() {
        return CODES.stream().sorted().map(Locale::of).toList();
    }

    public static boolean isSupported(String code) {
        return code != null && CODES.contains(code);
    }

    public static String current() {
        String language = LocaleContextHolder.getLocale().getLanguage();
        return isSupported(language) ? language : DEFAULT;
    }
}
