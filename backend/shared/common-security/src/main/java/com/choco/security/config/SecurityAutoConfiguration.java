package com.choco.security.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Baseline HTTP security shared by every service.
 *
 * <p>Design decisions, and why:
 * <ul>
 *   <li><b>Stateless.</b> The client authenticates with a short-lived bearer token. No HTTP session
 *       is created, so a stolen session cookie cannot be replayed and any replica can serve any
 *       request.</li>
 *   <li><b>CSRF disabled.</b> CSRF exploits ambient cookie credentials. Since the API accepts no
 *       cookies and requires an {@code Authorization} header, there is no ambient authority for a
 *       cross-site request to abuse; keeping CSRF tokens would add friction without a control.</li>
 *   <li><b>Role from the token, permissions from the token.</b> Services trust the signature and
 *       the issuer, never a header such as {@code X-User-Role}, which a client could forge.</li>
 *   <li><b>Method security enabled.</b> {@code @PreAuthorize} lets a use case state its own access
 *       rule next to the logic it protects.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAutoConfiguration {

    /** Endpoints every service exposes unauthenticated: liveness, docs and the OpenAPI contract. */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain chocoSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource,
            SecurityProperties securityProperties) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PUBLIC_PATHS).permitAll();
                    // Service-specific unauthenticated paths, supplied by configuration. Declared as
                    // an explicit list rather than a wildcard so widening public access is a visible
                    // change in the service's own configuration, not a hidden default.
                    if (!securityProperties.getPublicPaths().isEmpty()) {
                        auth.requestMatchers(securityProperties.getPublicPaths().toArray(String[]::new)).permitAll();
                    }
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> {
                        })
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)));

        return http.build();
    }

    /**
     * Builds authorities from both the coarse {@code role} claim and the fine-grained
     * {@code permissions} claim, so a controller can require either a role or a specific permission.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RoleAndPermissionAuthoritiesConverter());
        return converter;
    }

    /**
     * Validates token signatures against the identity-service JWKS endpoint.
     *
     * <p>Asymmetric keys (RS256) are used so that only the identity service holds the private key:
     * every other service can verify a token but none of them can mint one.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(@Value("${choco.security.jwk-set-uri}") String jwkSetUri,
                                 @Value("${choco.security.issuer:choco-identity}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> withIssuer =
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), new JwtIssuerValidator(issuer));
        decoder.setJwtValidator(withIssuer);
        return decoder;
    }

    /**
     * CORS is an allow-list, never {@code *}. A wildcard origin combined with credentials would let
     * any site call the API with the user's token; only the two known frontend origins are listed.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "corsConfigurationSource")
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${choco.cors.allowed-origins:http://localhost:5173,http://localhost:5174}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
                "X-Correlation-Id", "X-Razorpay-Signature"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id", "Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** Maps {@code role} to {@code ROLE_*} and {@code permissions} to bare authorities. */
    static class RoleAndPermissionAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Set<GrantedAuthority> authorities = new HashSet<>(defaultConverter.convert(jwt));

            String role = jwt.getClaimAsString("role");
            if (role != null && !role.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }

            List<String> permissions = jwt.getClaimAsStringList("permissions");
            if (permissions != null) {
                permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }
            return Set.copyOf(authorities);
        }
    }
}
