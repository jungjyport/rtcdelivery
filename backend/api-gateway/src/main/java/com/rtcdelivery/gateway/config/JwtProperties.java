package com.rtcdelivery.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String issuer = "rtc-delivery";
    private String publicKeyLocation = "classpath:keys/jwt-public.pem";
    private String publicKey;
}
