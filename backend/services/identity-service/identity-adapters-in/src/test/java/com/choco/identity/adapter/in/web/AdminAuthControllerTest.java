package com.choco.identity.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.choco.identity.application.command.AdminLoginCommand;
import com.choco.identity.application.dto.AdminAuthenticationResult;
import com.choco.identity.application.dto.AdminView;
import com.choco.identity.application.port.TokenPair;
import com.choco.identity.application.service.AdminAuthenticationService;
import com.choco.web.client.ClientIpResolver;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests the administrator authentication controller.
 *
 * <p>Focused on the contract the browser depends on: request validation, the shape of the response,
 * and that the resolved client address reaches the service (it keys the rate limiter, so losing it
 * would silently disable per-network throttling).
 */
@DisplayName("AdminAuthController")
class AdminAuthControllerTest {

    private AdminAuthenticationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AdminAuthenticationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminAuthController(service, new ClientIpResolver(List.of())))
                .build();
    }

    private static AdminAuthenticationResult result() {
        return new AdminAuthenticationResult(
                new AdminView(UUID.randomUUID(), "Asha Menon", "owner@choco.example", "SUPER_ADMIN",
                        Set.of("admin:manage"), true, Instant.parse("2026-03-01T09:00:00Z")),
                TokenPair.bearer("access-token", "refresh-token",
                        Instant.parse("2026-03-01T09:15:00Z"), Instant.parse("2026-03-31T09:00:00Z")));
    }

    @Test
    void a_valid_login_returns_the_admin_and_tokens() throws Exception {
        given(service.login(any(AdminLoginCommand.class))).willReturn(result());

        mockMvc.perform(post("/api/v1/auth/admin/login")
                        .contentType("application/json")
                        .content("{\"email\":\"owner@choco.example\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin.email").value("owner@choco.example"))
                .andExpect(jsonPath("$.admin.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.tokens.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokens.refreshToken").value("refresh-token"));
    }

    @Test
    void the_client_address_is_passed_to_the_service() throws Exception {
        given(service.login(any(AdminLoginCommand.class))).willReturn(result());

        mockMvc.perform(post("/api/v1/auth/admin/login")
                        .contentType("application/json")
                        .content("{\"email\":\"owner@choco.example\",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<AdminLoginCommand> captor = ArgumentCaptor.forClass(AdminLoginCommand.class);
        verify(service).login(captor.capture());
        // The command carries an address; which value it is depends on proxy headers, but it must not
        // be null, because the service keys its rate limiter on it.
        org.assertj.core.api.Assertions.assertThat(captor.getValue().ipAddress()).isNotNull();
    }

    @Test
    void a_missing_password_is_rejected_before_the_service_is_called() throws Exception {
        mockMvc.perform(post("/api/v1/auth/admin/login")
                        .contentType("application/json")
                        .content("{\"email\":\"owner@choco.example\"}"))
                .andExpect(status().isBadRequest());

        verify(service, org.mockito.Mockito.never()).login(any());
    }

    @Test
    void a_blank_email_is_rejected_before_the_service_is_called() throws Exception {
        mockMvc.perform(post("/api/v1/auth/admin/login")
                        .contentType("application/json")
                        .content("{\"email\":\"  \",\"password\":\"a-strong-password\"}"))
                .andExpect(status().isBadRequest());

        verify(service, org.mockito.Mockito.never()).login(any());
    }

    @Test
    void logout_returns_no_content() throws Exception {
        mockMvc.perform(post("/api/v1/auth/admin/logout")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isNoContent());

        verify(service).logout("refresh-token");
    }
}
