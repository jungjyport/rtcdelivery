package com.rtcdelivery.memberauth.exception;

import com.rtcdelivery.memberauth.common.ApiResponse;
import com.rtcdelivery.memberauth.security.CookieUtils;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 모든 예외를 {@link ApiResponse} 형태의 에러 응답으로 변환한다.
 *
 * <p>응답 {@code message}에는 항상 {@link ErrorCode} 이름만 실린다. 사용자에게 보이는 문장은
 * 프론트엔드가 i18n으로 만든다. 상세 규약은 docs/error-handling.md 참조.
 *
 * <p>Security 필터 체인에서 발생하는 401/403은 이 클래스에 도달하지 않으므로
 * {@link RestAuthenticationEntryPoint} / {@link RestAccessDeniedHandler}가 같은 포맷으로 처리한다.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final CookieUtils cookieUtils;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("비즈니스 예외: {} ({})", errorCode.code(), e.getDetail(), e);
        }
        else {
            log.warn("비즈니스 예외: {} ({})", errorCode.code(), e.getDetail());
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(errorCode.getStatus());
        if (errorCode == ErrorCode.REFRESH_TOKEN_NOT_FOUND
                || errorCode == ErrorCode.REFRESH_TOKEN_INVALID
                || errorCode == ErrorCode.REFRESH_TOKEN_EXPIRED) {
            builder.header(HttpHeaders.SET_COOKIE, cookieUtils.createDeleteRefreshTokenCookie().toString());
        }

        return builder.body(ApiResponse.error(errorCode.getStatus().value(), errorCode.code()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {
        return validationFailure(e.getBindingResult());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBind(BindException e) {
        return validationFailure(e.getBindingResult());
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(Exception e) {
        log.warn("검증 실패: {}", e.getMessage());
        return toResponse(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(Exception e) {
        log.warn("잘못된 요청 파라미터: {}", e.getMessage());
        return toResponse(ErrorCode.INVALID_PARAMETER);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문 파싱 실패: {}", e.getMessage());
        return toResponse(ErrorCode.MALFORMED_REQUEST_BODY);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("지원하지 않는 메서드: {}", e.getMessage());
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
        log.warn("매핑되지 않은 경로: {}", e.getMessage());
        return toResponse(ErrorCode.ENDPOINT_NOT_FOUND);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
        log.warn("인증 실패: {}", e.getMessage());
        return toResponse(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("권한 없는 접근: {}", e.getMessage());
        return toResponse(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("데이터 무결성 위반", e);
        return toResponse(ErrorCode.DATA_INTEGRITY_VIOLATION);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 검증 실패의 필드별 상세는 {@code data}에 담는다. {@code message}는 항상 단일 에러 코드여야 하기 때문이다.
     * 같은 필드에 규칙이 여러 개 걸린 경우 첫 번째 메시지만 남긴다.
     */
    private ResponseEntity<ApiResponse<Map<String, String>>> validationFailure(BindingResult bindingResult) {
        Map<String, String> details = new LinkedHashMap<>();
        bindingResult.getFieldErrors()
                .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        bindingResult.getGlobalErrors()
                .forEach(error -> details.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        log.warn("검증 실패: {}", details);

        HttpStatus status = ErrorCode.VALIDATION_ERROR.getStatus();
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), ErrorCode.VALIDATION_ERROR.code(), details));
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode) {
        HttpStatus status = errorCode.getStatus();
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), errorCode.code()));
    }
}
