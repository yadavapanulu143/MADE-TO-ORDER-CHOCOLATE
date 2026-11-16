package com.choco.identity;

import com.choco.identity.application.service.AdminProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator on startup when one is configured.
 *
 * <p>Runs once, after the context is ready, and delegates the decision to
 * {@link AdminProvisioningService}, which is idempotent and refuses a weak password. It logs only
 * the outcome, never the password or its hash.
 */
@Component
class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminProvisioningService provisioningService;
    private final String email;
    private final String password;
    private final String fullName;

    AdminBootstrapRunner(AdminProvisioningService provisioningService,
                         @Value("${choco.admin.bootstrap.email:}") String email,
                         @Value("${choco.admin.bootstrap.password:}") String password,
                         @Value("${choco.admin.bootstrap.full-name:Administrator}") String fullName) {
        this.provisioningService = provisioningService;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) {
            // The normal case in development and in production: the account is created once, by hand,
            // and the configuration is then cleared so the password is not left in the environment.
            log.info("No administrator bootstrap credentials configured; skipping.");
            return;
        }
        provisioningService.ensureBootstrapAdmin(email, password, fullName)
                .ifPresent(created -> log.info("Bootstrap administrator created for {}. "
                        + "Remove the configured credentials now.", created));
    }
}
