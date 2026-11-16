package com.choco.identity.application.service;

import com.choco.core.domain.EmailAddress;
import com.choco.core.error.ValidationException;
import com.choco.core.port.Clock;
import com.choco.core.port.IdGenerator;
import com.choco.core.port.PasswordHasher;
import com.choco.core.port.PasswordPolicy;
import com.choco.core.error.ErrorCode;
import com.choco.identity.application.port.AuditLogPort;
import com.choco.identity.domain.admin.AdminAccount;
import com.choco.identity.domain.admin.AdminRole;
import com.choco.identity.domain.port.AdminRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Creates the first administrator from supplied configuration, exactly once.
 *
 * <p>There is no public "register an admin" endpoint and there never should be: an open admin
 * sign-up would be the whole authorisation model gone. The first account therefore has to come from
 * somewhere trusted, and that is the operator's configuration — an email and a password handed to
 * the process through the environment.
 *
 * <p>Idempotent and conservative. If any administrator already exists the method does nothing, so a
 * restart cannot resurrect a deliberately disabled account or overwrite a changed password. If no
 * credentials are configured it also does nothing, so a development run that never intends to use
 * the dashboard is not forced to invent a password. When credentials are configured it refuses a
 * password that fails the same policy as everywhere else, rather than seeding a weak account that
 * would be the easiest way into the system.
 */
public class AdminProvisioningService {

    private final AdminRepository adminRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final AuditLogPort auditLog;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public AdminProvisioningService(AdminRepository adminRepository,
                                    PasswordHasher passwordHasher,
                                    PasswordPolicy passwordPolicy,
                                    AuditLogPort auditLog,
                                    IdGenerator idGenerator,
                                    Clock clock) {
        this.adminRepository = adminRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.auditLog = auditLog;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * Ensures a bootstrap administrator exists, if one was configured.
     *
     * @param email    the configured address, or {@code null}/blank to skip
     * @param password the configured password, or {@code null}/blank to skip
     * @return the address of an administrator that exists after the call, or empty if none was
     *         configured and none already exists
     */
    public Optional<String> ensureBootstrapAdmin(String email, String password, String fullName) {
        if (isBlank(email) || isBlank(password)) {
            return Optional.empty();
        }

        if (adminRepository.count() > 0) {
            // Never overwrite. An operator changing the configured password must not silently reset a
            // live account, and a restart must not undo an intentional disable.
            auditLog.record("SYSTEM", null, "ADMIN_BOOTSTRAP_SKIPPED", "ADMIN", null, "SUCCESS",
                    "an administrator already exists", null);
            return Optional.empty();
        }

        List<String> violations = passwordPolicy.violations(password);
        if (!violations.isEmpty()) {
            throw new ValidationException(ErrorCode.WEAK_PASSWORD,
                    "The configured administrator password is not strong enough: "
                            + String.join("; ", violations));
        }

        EmailAddress address = EmailAddress.of(email);
        String name = isBlank(fullName) ? "Administrator" : fullName.trim();
        Instant now = clock.now();

        AdminAccount account = AdminAccount.provision(idGenerator.nextId(), name, address,
                passwordHasher.hash(password), AdminRole.SUPER_ADMIN, now);
        adminRepository.save(account);

        // The address is recorded; the password and its hash never are.
        auditLog.record("SYSTEM", null, "ADMIN_BOOTSTRAP_CREATED", "ADMIN", account.id().toString(),
                "SUCCESS", "email=" + address.value(), null);
        return Optional.of(address.value());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
