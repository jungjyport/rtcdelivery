package com.rtcdelivery.memberauth.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "auth.cookie")
public class CookieProperties {

    private String name = "refreshToken";
    private String path = "/api/v1/auth";
    private boolean secure = false;
    private String sameSite = "Lax";
    private long maxAge = 604800L; // 7 days (seconds)
}
