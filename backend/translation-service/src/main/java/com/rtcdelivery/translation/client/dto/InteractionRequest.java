package com.rtcdelivery.translation.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InteractionRequest(
        String model,
        boolean store,
        boolean stream,
        @JsonProperty("generation_config") GenerationConfig generationConfig,
        @JsonProperty("system_instruction") String systemInstruction,
        String input,
        @JsonProperty("response_format") ResponseFormat responseFormat
) {
    public record GenerationConfig(
            @JsonProperty("thinking_level") String thinkingLevel
    ) {}

    public record ResponseFormat(
            String type,
            @JsonProperty("mime_type") String mimeType,
            Map<String, Object> schema
    ) {}
}
