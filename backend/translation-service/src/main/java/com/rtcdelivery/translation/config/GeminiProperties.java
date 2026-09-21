package com.rtcdelivery.translation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String apiRevision = "2026-05-20";
    private String model = "gemini-3.5-flash-lite";
    private String thinkingLevel = "minimal";

    @Value("${gemini.api-key}")
    private String apiKey;

    private Timeout timeout = new Timeout();
    private Quota quota = new Quota();
    private Batch batch = new Batch();

    @Getter
    @Setter
    public static class Timeout {
        private Duration connect = Duration.ofSeconds(3);
        private Duration read = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class Quota {
        private int rpm = 12;
        private int rpd = 400;
        private String zone = "America/Los_Angeles";
    }

    @Getter
    @Setter
    public static class Batch {
        private int maxItems = 50;
        private int maxChars = 8000;
    }
}
