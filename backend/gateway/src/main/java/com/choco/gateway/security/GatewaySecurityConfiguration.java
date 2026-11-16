package com.choco.gateway.security;

import com.choco.gateway.config.GatewayProperties;
import com.choco.gateway.routing.RoutingConfiguration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The gateway's edge security policy.
 *
 * <p>It replaces the shared {@code SecurityFilterChain} from {@code common-security} — that bean is
 * {@code @ConditionalOnMissingBean}, so defining one here wins — for two reasons:
 * <ul>
 *   <li>The public-path list must have a single source of truth. It lives in
 *       {@link GatewayProperties} and is read here, so the routing table and the security policy can
 *       never disagree about which paths are open.</li>
 *   <li>The gateway additionally permits the internal circuit-breaker fallback path, which is
 *       reached by an internal forward after authentication has already happened.</li>
 * </ul>
 *
 * <p>The {@code JwtDecoder} and {@code JwtAuthenticationConverter} are still taken from
 * {@code common-security}: token verification must be identical everywhere, and duplicating it here
 * would be the one place a subtle divergence could weaken every route at once. Only the service's
 * private key can mint a token; the gateway holds only the public JWKS.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewaySecurityConfiguration {

    private static final String[] ALWAYS_PUBLIC = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    SecurityFilterChain gatewaySecurityFilterChain(HttpSecurity http,
                                                   GatewayProperties properties,
                                                   JwtDecoder jwtDecoder,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   GatewayErrorResponder errorResponder) throws Exception {
        List<String> publicPaths = properties.getPublicPaths();

        http
                .csrf(AbstractHttpConfigurer::disable)
                // The gateway is the only component a browser talks to, so CORS is enforced here
                // once, with the allow-list from common-security. Wiring the same source keeps the
                // two frontends' origins defined in one place.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(ALWAYS_PUBLIC).permitAll();
                    if (!publicPaths.isEmpty()) {
                        auth.requestMatchers(publicPaths.toArray(String[]::new)).permitAll();
                    }
                    // The circuit-breaker fallback is an internal forward, never a client path.
                    auth.requestMatchers(RoutingConfiguration.FALLBACK_PREFIX + "**").permitAll();
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errorResponder))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorResponder)
                        .accessDeniedHandler(errorResponder))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)));

        return http.build();
    }
}
