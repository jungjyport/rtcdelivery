package com.rtcdelivery.memberauth.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String issuer = "rtc-delivery";
    private long accessTokenValidity = 1800000L; // 30 min (ms)
    private long refreshTokenValidity = 604800000L; // 7 days (ms)
    private String privateKeyLocation = "classpath:keys/jwt-private.pem";
    private String publicKeyLocation = "classpath:keys/jwt-public.pem";
    private String privateKey;
    private String publicKey;
}
