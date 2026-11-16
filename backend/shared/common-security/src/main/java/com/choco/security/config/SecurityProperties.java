package com.choco.security.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Service-specific security settings, bound from {@code choco.security.*}.
 *
 * <p>A typed binding is used rather than {@code @Value("${...}")} because the public-path list is a
 * YAML sequence. {@code @Value} cannot bind a sequence: it resolves a single scalar, so a list
 * silently resolves to the empty default and every configured path stays protected. The failure is
 * quiet, which is the worst kind for a security setting.
 *
 * <p>The list defaults to empty. A service opts in explicitly, so widening public access is always a
 * visible edit in that service's own configuration rather than an inherited default.
 */
@ConfigurationProperties(prefix = "choco.security")
public class SecurityProperties {

    /** Issuer claim expected on bearer tokens. */
    private String issuer = "choco";

    /** Unauthenticated request paths for this service, on top of the always-public baseline. */
    private List<String> publicPaths = new ArrayList<>();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths == null ? new ArrayList<>() : publicPaths;
    }
}
