package com.choco.identity.application.fake;

import com.choco.core.port.TransactionRunner;
import com.choco.identity.domain.customer.Customer;
import com.choco.identity.domain.otp.OtpChallenge;
import java.util.function.Supplier;

/**
 * Transaction runner with real rollback semantics over the in-memory fakes.
 *
 * <p>A pass-through runner would make every transaction test vacuous: the writes would land whether
 * or not the use case was correct, and the test would pass against the very bug it is meant to catch.
 * This one snapshots the stores before the unit of work and restores them if it throws, so a test can
 * assert that a partial failure really left nothing behind.
 *
 * <p>It is not a substitute for an integration test against Postgres, which is what proves the real
 * boundaries. It is a fast check that the use case opened a boundary at all.
 */
public class SnapshottingTransactionRunner implements TransactionRunner {

    private final InMemoryCustomerRepository customers;
    private final InMemoryOtpChallengeRepository challenges;
    private final InMemoryAddressRepository addresses;

    public SnapshottingTransactionRunner(InMemoryCustomerRepository customers,
                                         InMemoryOtpChallengeRepository challenges) {
        this(customers, challenges, null);
    }

    /** Address-aware variant, for tests that write addresses inside a unit of work. */
    public SnapshottingTransactionRunner(InMemoryCustomerRepository customers,
                                         InMemoryOtpChallengeRepository challenges,
                                         InMemoryAddressRepository addresses) {
        this.customers = customers;
        this.challenges = challenges;
        this.addresses = addresses;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return run(work);
    }

    @Override
    public <T> T executeInNewTransaction(Supplier<T> work) {
        // REQUIRES_NEW still commits independently on success, so the behaviour matches execute()
        // here; the difference only shows when an outer transaction rolls back, which the tests
        // model by checking what survived.
        return run(work);
    }

    private <T> T run(Supplier<T> work) {
        var customerSnapshot = customers.snapshot();
        var challengeSnapshot = challenges.snapshot();
        var addressSnapshot = addresses == null ? null : addresses.snapshot();
        try {
            return work.get();
        } catch (RuntimeException e) {
            customers.restore(customerSnapshot);
            challenges.restore(challengeSnapshot);
            if (addresses != null) {
                addresses.restore(addressSnapshot);
            }
            throw e;
        }
    }
}
