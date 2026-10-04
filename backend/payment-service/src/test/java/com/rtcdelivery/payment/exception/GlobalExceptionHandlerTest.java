package com.rtcdelivery.payment.exception;

import com.rtcdelivery.payment.common.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("BusinessException 4xx 처리")
    void handleBusiness_4xx() {
        BusinessException ex = new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND.code());
    }

    @Test
    @DisplayName("BusinessException 5xx 처리")
    void handleBusiness_5xx() {
        BusinessException ex = new BusinessException(ErrorCode.PG_UNAVAILABLE);
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.PG_UNAVAILABLE.code());
    }

    @Test
    @DisplayName("MethodArgumentNotValidException 처리")
    void handleMethodArgumentNotValid() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "cardLast4", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleMethodArgumentNotValid(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.VALIDATION_ERROR.code());
        assertThat(response.getBody().getData()).containsEntry("cardLast4", "must not be blank");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException 처리")
    void handleNotReadable() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("malformed", new MockHttpInputMessage(new byte[0]));
        ResponseEntity<ApiResponse<Void>> response = handler.handleNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.MALFORMED_REQUEST_BODY.code());
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException 처리")
    void handleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.code());
    }

    @Test
    @DisplayName("AuthenticationException 처리")
    void handleAuthentication() {
        BadCredentialsException ex = new BadCredentialsException("bad creds");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthentication(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.UNAUTHORIZED.code());
    }

    @Test
    @DisplayName("AccessDeniedException 처리")
    void handleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("forbidden");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.FORBIDDEN.code());
    }

    @Test
    @DisplayName("DataIntegrityViolationException 처리")
    void handleDataIntegrity() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("unique constraint violated");
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.DATA_INTEGRITY_VIOLATION.code());
    }

    @Test
    @DisplayName("Unhandled Exception 처리")
    void handleUnexpected() {
        Exception ex = new RuntimeException("unknown crash");
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.code());
    }
}
