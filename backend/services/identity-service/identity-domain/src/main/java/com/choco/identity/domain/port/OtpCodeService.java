package com.choco.identity.domain.port;

/**
 * Generates and checks one-time passwords.
 *
 * <p>A port rather than a static utility so the code generator can be made deterministic in tests,
 * and so the storage format of the OTP hash can change without touching the domain.
 */
public interface OtpCodeService {

    /** Cryptographically random numeric code of {@link com.choco.identity.domain.otp.OtpChallenge#codeLength()}. */
    String generateCode();

    /** Hashes a code for storage. Never returns the code itself. */
    String hashCode(String code);

    /** Constant-time comparison of a submitted code against a stored hash. */
    boolean matches(String submittedCode, String storedHash);
}
