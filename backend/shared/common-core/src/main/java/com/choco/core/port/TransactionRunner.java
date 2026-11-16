package com.choco.core.port;

import java.util.function.Supplier;

/**
 * Outbound port that runs a unit of work atomically.
 *
 * <p>This lives beside the use case, not on the HTTP adapter, and that placement is deliberate. A
 * registration writes a customer, two OTP challenges, an outbox row and an audit row; if the process
 * dies between them the database is left holding a customer who cannot be verified. Binding the
 * boundary to the request handler would fix that for the web path only — and this service will also
 * receive commands from message consumers, which would then run with no transaction at all. Putting
 * the boundary with the use case means every caller, whatever the transport, gets the same guarantee.
 *
 * <p>Implementations must not swallow the exception: the port's whole purpose is that the work either
 * commits or leaves no trace, so a failing {@code work} must propagate and roll the unit back.
 */
public interface TransactionRunner {

    /** Runs {@code work} and returns its result, committing on success and rolling back on failure. */
    <T> T execute(Supplier<T> work);

    /**
     * Runs {@code work} in a transaction of its own, independent of any transaction the caller has
     * open.
     *
     * <p>Needed when a write must survive the caller's rollback. The clearest case is a failed OTP
     * attempt: the attempt counter is incremented and then an exception is thrown, so the business
     * operation fails — but if that increment rolled back with it, an attacker could retry forever
     * and the attempt cap would mean nothing. The counter has to be durable precisely because the
     * operation around it was not.
     */
    <T> T executeInNewTransaction(Supplier<T> work);

    /** Runs {@code work} for its side effects, committing on success and rolling back on failure. */
    default void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }

    /** Runs {@code work} in its own transaction, for side effects. */
    default void executeInNewTransaction(Runnable work) {
        executeInNewTransaction(() -> {
            work.run();
            return null;
        });
    }
}
