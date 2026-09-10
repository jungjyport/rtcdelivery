package com.rtcdelivery.memberauth.service;

import com.rtcdelivery.memberauth.domain.Member;
import com.rtcdelivery.memberauth.domain.Role;
import com.rtcdelivery.memberauth.dto.response.MemberResponse;
import com.rtcdelivery.memberauth.exception.BusinessException;
import com.rtcdelivery.memberauth.exception.ErrorCode;
import com.rtcdelivery.memberauth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자에 의한 회원 관리. 인가 판정은 컨트롤러의 {@code @PreAuthorize}가 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final RefreshTokenService refreshTokenService;

    /**
     * 회원의 역할을 변경한다. 점주 승격이 주 용도다.
     *
     * <p>이미 발급된 Access Token은 만료될 때까지 옛 역할을 그대로 갖는다. 강등이 즉시
     * 반영되어야 하므로 Refresh Token을 폐기해 다음 갱신 시점에 재로그인을 강제한다.
     * 남은 Access Token 수명만큼의 노출 창은 감수한다.
     */
    @Transactional
    public MemberResponse changeRole(Long targetMemberId, Role newRole, Long actorMemberId) {
        if (targetMemberId.equals(actorMemberId)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }

        Member member = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Role previousRole = member.getRole();
        if (previousRole == newRole) {
            return MemberResponse.from(member);
        }

        member.changeRole(newRole);
        refreshTokenService.invalidateByUsername(member.getUsername());

        log.info("Role changed: memberId={}, {} -> {} (by memberId={})",
                targetMemberId, previousRole, newRole, actorMemberId);

        return MemberResponse.from(member);
    }
}
