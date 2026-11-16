package com.choco.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorModelTest {

    @Test
    void error_codes_map_to_expected_http_status() {
        assertThat(ErrorCode.NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCode.FORBIDDEN.httpStatus()).isEqualTo(403);
        assertThat(ErrorCode.RATE_LIMITED.httpStatus()).isEqualTo(429);
        assertThat(ErrorCode.PINCODE_NOT_SERVICEABLE.httpStatus()).isEqualTo(422);
        assertThat(ErrorCode.PAYMENT_ALREADY_CAPTURED.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.INTERNAL_ERROR.httpStatus()).isEqualTo(500);
    }

    @Test
    void validation_exception_exposes_field_errors() {
        ValidationException ex = new ValidationException("invalid", java.util.Map.of("email", "required"));
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ex.fieldErrors()).containsEntry("email", "required");
    }

    @Test
    void not_found_factory_includes_entity_and_id() {
        assertThat(NotFoundException.of("Order", "abc").getMessage()).isEqualTo("Order not found: abc");
    }
}
