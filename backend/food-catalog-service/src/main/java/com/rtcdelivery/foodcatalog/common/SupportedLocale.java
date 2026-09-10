package com.rtcdelivery.foodcatalog.common;

import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 지원 locale과 현재 요청의 locale 판정.
 *
 * <p>클라이언트는 {@code Accept-Language} 헤더로 locale을 전달하고, 누락되거나 지원하지 않는
 * 값이면 {@link #DEFAULT}로 동작한다 (docs/api-conventions.md §3).
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

    /**
     * 현재 요청의 locale 코드. {@code AcceptHeaderLocaleResolver}가 이미 지원 목록으로
     * 좁혀주지만, 직접 호출되는 경로를 위해 한 번 더 방어한다.
     */
    public static String current() {
        String language = LocaleContextHolder.getLocale().getLanguage();
        return isSupported(language) ? language : DEFAULT;
    }
}
