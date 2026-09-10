package com.rtcdelivery.memberauth.controller;

import com.rtcdelivery.memberauth.common.ApiResponse;
import com.rtcdelivery.memberauth.dto.request.RoleUpdateRequest;
import com.rtcdelivery.memberauth.dto.response.MemberResponse;
import com.rtcdelivery.memberauth.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Member", description = "회원 관리 API (관리자)")
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "회원 역할 변경",
            description = "관리자가 회원의 역할을 변경합니다. 점주 승격에 사용합니다. "
                    + "변경 시 해당 회원의 Refresh Token이 폐기되어 다음 갱신에서 재로그인이 필요합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{memberId}/role")
    public ResponseEntity<ApiResponse<MemberResponse>> changeRole(
            @PathVariable Long memberId,
            @Valid @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal Long actorMemberId) {

        MemberResponse response = memberService.changeRole(memberId, request.getRole(), actorMemberId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
