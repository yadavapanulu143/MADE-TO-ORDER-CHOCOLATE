package com.choco.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import com.choco.core.error.NotFoundException;
import com.choco.core.error.ValidationException;
import com.choco.web.json.ApiError;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void maps_not_found_to_404() {
        ResponseEntity<ApiError> response = handler.handleDomain(
                new NotFoundException("Order not found: 1"), request());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.NOT_FOUND.name());
    }

    @Test
    void maps_forbidden_to_403() {
        ResponseEntity<ApiError> response = handler.handleDomain(
                new ForbiddenException("not yours"), request());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void includes_field_errors_for_validation_failures() {
        ResponseEntity<ApiError> response = handler.handleDomain(
                new ValidationException("bad input", Map.of("email", "must be valid")), request());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsEntry("email", "must be valid");
    }

    @Test
    void unexpected_exception_is_generic_500_without_leaking_message() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:postgresql://secret-host:5432"), request());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().detail()).doesNotContain("secret-host");
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.name());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders/1");
        request.setAttribute("correlationId", "test-correlation");
        return request;
    }
}
