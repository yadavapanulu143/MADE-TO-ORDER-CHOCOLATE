package com.choco.core.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port for revoked access-token sessions.
 *
 * <p>A short-lived bearer token is self-contained: once it is signed it is valid until it expires, and
 * nothing in the request says whether the session that produced it still exists. That is fine for
 * ordinary expiry, but it makes <em>logout</em> a lie — revoking the refresh token stops the session
 * being renewed, yet the access token already in the client's hands keeps working for the rest of its
 * lifetime. For an administrator, whose token authorises refunds, catalogue edits and customer
 * exports, a logout that leaves a working credential behind is a real hole.
 *
 * <p>There are two scopes, because two different security events need to be expressed:
 * <ul>
 *   <li><b>A session.</b> Logout, and the rotation of a refresh token, retire one sign-in. The access
 *       token it minted must stop working at once.</li>
 *   <li><b>A subject, up to a moment.</b> A password change or reset retires <em>every</em> sign-in an
 *       account currently holds. Enumerating live sessions would race with a refresh happening at the
 *       same instant, so the rule is stated as "no token issued before this moment is valid". A fresh
 *       sign-in after the change is issued after the cutoff and works normally.</li>
 * </ul>
 *
 * <p>Only identifiers and a cutoff time are stored, never a token: a revocation list is a record of
 * what to reject, so a copy of it must not yield a usable credential.
 *
 * <p>Every entry expires on its own once no token it covers could still be presented, so the store
 * stays proportional to recent security events, not to the number of users.
 */
public interface TokenRevocationRegistry {

    /**
     * Revokes one session's access tokens until {@code until}.
     *
     * <p>{@code until} must be no earlier than the expiry of any access token the session could still
     * hold; a value already in the past is ignored, since such tokens are refused by the decoder's own
     * expiry check anyway.
     */
    void revokeSession(String sessionId, Instant until);

    /**
     * Revokes every access token issued to {@code subject} strictly before {@code cutoff}, retaining
     * the rule until {@code until}.
     *
     * <p>Used when an account's credentials change. A token issued at or after the cutoff is
     * unaffected, which is what lets the account sign in again immediately.
     */
    void revokeSubjectIssuedBefore(UUID subject, Instant cutoff, Instant until);

    /**
     * Whether the token identified by its session, subject and issue time has been revoked.
     *
     * <p>Implementations must <b>fail closed</b>: if the store cannot be consulted, this throws rather
     * than answering {@code false}. An unavailable store must never silently re-enable every session
     * that was logged out, which is the whole point of the control.
     */
    boolean isRevoked(String sessionId, UUID subject, Instant issuedAt);
}
