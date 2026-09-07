package com.rtcdelivery.memberauth.dto.response;

import com.rtcdelivery.memberauth.domain.AuthProvider;
import com.rtcdelivery.memberauth.domain.Member;
import com.rtcdelivery.memberauth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "회원 정보 응답 DTO")
public class MemberResponse {

    @Schema(description = "회원 고유 ID", example = "1")
    private Long id;

    @Schema(description = "로그인 아이디", example = "hong_gildong")
    private String username;

    @Schema(description = "닉네임", example = "홍길동")
    private String nickname;

    @Schema(description = "이메일", example = "hong@example.com")
    private String email;

    @Schema(description = "권한", example = "ROLE_USER")
    private Role role;

    @Schema(description = "인증 제공자", example = "LOCAL")
    private AuthProvider authProvider;

    @Schema(description = "생성 일시")
    private LocalDateTime createdAt;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .username(member.getUsername())
                .nickname(member.getNickname())
                .email(member.getEmail())
                .role(member.getRole())
                .authProvider(member.getAuthProvider())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
