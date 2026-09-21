package com.rtcdelivery.translation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InteractionResponse(
        String id,
        String model,
        String status,
        List<Step> steps,
        Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(
            String type,
            List<Content> content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            String type,
            String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("total_input_tokens") Integer totalInputTokens,
            @JsonProperty("total_output_tokens") Integer totalOutputTokens,
            @JsonProperty("total_thought_tokens") Integer totalThoughtTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {}
}
