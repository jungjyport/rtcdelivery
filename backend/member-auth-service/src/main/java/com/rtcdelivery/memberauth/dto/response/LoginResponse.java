package com.rtcdelivery.memberauth.dto.response;

import com.rtcdelivery.memberauth.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "로그인 성공 응답 DTO")
public class LoginResponse {

    @Schema(description = "Access Token (JWT)", example = "eyJhbGciOiJSUzI1NiJ9...")
    private String accessToken;

    @Builder.Default
    @Schema(description = "토큰 타입", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "토큰 만료 시간 (초 단위)", example = "1800")
    private long expiresIn;

    @Schema(description = "사용자 프로필 정보")
    private MemberResponse user;

    public static LoginResponse of(String accessToken, long expiresIn, Member member) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .user(MemberResponse.from(member))
                .build();
    }
}
