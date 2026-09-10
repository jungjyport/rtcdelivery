package com.rtcdelivery.foodcatalog.domain;

/**
 * API Gateway가 {@code X-User-Role} 헤더로 전달하는 역할 값.
 *
 * <p>회원 정보의 소유자는 member-auth-service다. 이 서비스는 헤더로 받은 문자열을 비교하기 위해
 * 같은 이름의 상수만 갖는다. member-auth의 {@code Role}에 값을 추가하면 여기에도 반영해야 한다.
 */
public enum Role {
    ROLE_USER,
    ROLE_OWNER,
    ROLE_ADMIN
}
