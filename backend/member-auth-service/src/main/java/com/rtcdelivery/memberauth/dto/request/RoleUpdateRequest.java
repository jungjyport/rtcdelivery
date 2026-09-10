package com.rtcdelivery.memberauth.dto.request;

import com.rtcdelivery.memberauth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Schema(description = "회원 역할 변경 요청 DTO")
public class RoleUpdateRequest {

    @NotNull(message = "역할은 필수입니다.")
    @Schema(description = "변경할 역할", example = "ROLE_OWNER",
            allowableValues = {"ROLE_USER", "ROLE_OWNER", "ROLE_ADMIN"})
    private Role role;
}
