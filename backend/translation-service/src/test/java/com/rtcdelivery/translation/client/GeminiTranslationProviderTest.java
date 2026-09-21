package com.rtcdelivery.translation.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtcdelivery.translation.config.GeminiProperties;
import com.rtcdelivery.translation.exception.BusinessException;
import com.rtcdelivery.translation.exception.ErrorCode;
import com.rtcdelivery.translation.service.TranslationProvider.ItemToTranslate;
import com.rtcdelivery.translation.service.TranslationProvider.TranslationBatchRequest;
import com.rtcdelivery.translation.service.TranslationProvider.TranslationBatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiTranslationProviderTest {

    private MockRestServiceServer mockServer;
    private GeminiTranslationProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        GeminiProperties properties = new GeminiProperties();
        properties.setBaseUrl("https://generativelanguage.googleapis.com/v1beta");
        properties.setApiRevision("2026-05-20");
        properties.setApiKey("test-api-key");
        properties.setModel("gemini-3.5-flash-lite");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Api-Revision", properties.getApiRevision())
                .defaultHeader("x-goog-api-key", properties.getApiKey());

        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        provider = new GeminiTranslationProvider(restClient, properties, objectMapper);
    }

    @Test
    @DisplayName("정상 응답: steps[]에서 model_output과 text를 추출하여 결과를 반환한다")
    void translate_success() {
        String responseJson = """
                {
                  "id": "interaction-123",
                  "model": "gemini-3.5-flash-lite",
                  "status": "completed",
                  "steps": [
                    {
                      "type": "model_output",
                      "content": [
                        {
                          "type": "text",
                          "text": "{\\"items\\":[{\\"ref\\":\\"MENU:1\\",\\"name\\":\\"キムチチゲ\\",\\"description\\":\\"熟成キムチの鍋\\"}]}"
                        }
                      ]
                    }
                  ],
                  "usage": {
                    "total_input_tokens": 15,
                    "total_output_tokens": 25,
                    "total_thought_tokens": 5,
                    "total_tokens": 45
                  }
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Api-Revision", "2026-05-20"))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        TranslationBatchRequest request = new TranslationBatchRequest(
                "ko", "ja",
                List.of(new ItemToTranslate("MENU:1", "김치찌개", "묵은지 찌개"))
        );

        TranslationBatchResult result = provider.translate(request);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).ref()).isEqualTo("MENU:1");
        assertThat(result.items().get(0).name()).isEqualTo("キムチチゲ");
        assertThat(result.items().get(0).description()).isEqualTo("熟成キムチの鍋");
        assertThat(result.inputTokens()).isEqualTo(15);
        assertThat(result.outputTokens()).isEqualTo(25);
    }

    @Test
    @DisplayName("429 응답 시 TRANSLATION_QUOTA_EXCEEDED 예외를 던진다")
    void translate_rateLimit429_throwsQuotaExceeded() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        TranslationBatchRequest request = new TranslationBatchRequest(
                "ko", "ja",
                List.of(new ItemToTranslate("MENU:1", "김치찌개", null))
        );

        assertThatThrownBy(() -> provider.translate(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSLATION_QUOTA_EXCEEDED);
    }

    @Test
    @DisplayName("401/403 응답 시 UNAUTHORIZED 예외를 던진다")
    void translate_authFailure_throwsUnauthorized() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        TranslationBatchRequest request = new TranslationBatchRequest(
                "ko", "ja",
                List.of(new ItemToTranslate("MENU:1", "김치찌개", null))
        );

        assertThatThrownBy(() -> provider.translate(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("ref 불일치 시 예외를 던진다")
    void translate_refMismatch_throwsException() {
        String responseJson = """
                {
                  "status": "completed",
                  "steps": [
                    {
                      "type": "model_output",
                      "content": [
                        {
                          "type": "text",
                          "text": "{\\"items\\":[{\\"ref\\":\\"WRONG_REF\\",\\"name\\":\\"キムチチゲ\\",\\"description\\":null}]}"
                        }
                      ]
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        TranslationBatchRequest request = new TranslationBatchRequest(
                "ko", "ja",
                List.of(new ItemToTranslate("MENU:1", "김치찌개", null))
        );

        assertThatThrownBy(() -> provider.translate(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSLATION_UNAVAILABLE);
    }
}
