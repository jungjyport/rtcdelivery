package com.rtcdelivery.translation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationHistoryTest {

    @Test
    @DisplayName("원문 정규화: 앞뒤 공백 제거 및 연속 공백 1개 축약")
    void normalize_collapsesWhitespace() {
        String input = "  김치찌개    정식   ";
        assertThat(TranslationHistory.normalize(input)).isEqualTo("김치찌개 정식");
    }

    @Test
    @DisplayName("원문 정규화: null이면 빈 문자열 반환")
    void normalize_nullSafe() {
        assertThat(TranslationHistory.normalize(null)).isEqualTo("");
    }

    @Test
    @DisplayName("공백이 다르더라도 정규화 후 해시값이 일치한다")
    void calculateHash_sameAfterNormalization() {
        String text1 = "김치찌개";
        String text2 = "  김치찌개   ";

        String hash1 = TranslationHistory.calculateHash(text1);
        String hash2 = TranslationHistory.calculateHash(text2);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    @DisplayName("대소문자는 접지 않으므로 영문 대소문자는 다른 해시를 갖는다")
    void calculateHash_preservesCase() {
        String lower = TranslationHistory.calculateHash("ramen");
        String upper = TranslationHistory.calculateHash("Ramen");

        assertThat(lower).isNotEqualTo(upper);
    }
}
