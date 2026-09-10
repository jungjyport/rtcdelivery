package com.rtcdelivery.memberauth.domain;

/**
 * 회원 역할. 한 회원은 하나의 역할만 갖는다.
 *
 * <p>계층({@code RoleHierarchy})은 사용하지 않는다. "로그인한 사용자 전체"를 뜻하는 자리에는
 * {@code isAuthenticated()}를 쓰고, 역할이 특정되는 자리에만 {@code hasAnyRole(...)}로 나열한다.
 * 점주가 일반 사용자 기능(주문 등)을 쓸 수 있어야 하는 요구는 전자로 해결된다.
 */
public enum Role {
    ROLE_USER,
    ROLE_OWNER,
    ROLE_ADMIN
}
