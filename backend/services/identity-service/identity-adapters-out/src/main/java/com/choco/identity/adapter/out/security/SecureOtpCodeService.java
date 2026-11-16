package com.choco.identity.adapter.out.security;

import com.choco.identity.domain.otp.OtpChallenge;
import com.choco.identity.domain.port.OtpCodeService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Generates and verifies one-time passwords.
 *
 * <p>The code is drawn from {@link SecureRandom}, not {@code Random}, because a predictable OTP is
 * as good as no OTP at all. Digits are drawn one at a time and the code is left-padded, so codes
 * such as {@code 000123} are possible; taking a uniform integer modulo a power of ten and formatting
 * it would bias the distribution.
 *
 * <p>Only a SHA-256 digest of the code is stored. A six-digit code has a small enough space that a
 * digest is not a barrier to an offline attacker, but it does stop a database dump from handing over
 * live codes directly. The real control against guessing is the attempt cap and expiry on
 * {@link OtpChallenge}, which is why they live in the domain.
 *
 * <p>Comparison is constant-time so that a timing side channel cannot be used to recover the code
 * digit by digit.
 */
@Component
class SecureOtpCodeService implements OtpCodeService {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateCode() {
        int length = OtpChallenge.codeLength();
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    @Override
    public String hashCode(String code) {
        return "sha256:" + HexFormat.of().formatHex(digest(code));
    }

    @Override
    public boolean matches(String submittedCode, String storedHash) {
        if (submittedCode == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hashCode(submittedCode).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
