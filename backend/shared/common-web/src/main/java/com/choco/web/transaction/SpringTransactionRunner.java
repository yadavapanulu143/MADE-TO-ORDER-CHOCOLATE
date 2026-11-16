package com.choco.web.transaction;

import com.choco.core.port.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adapts {@link TransactionRunner} to Spring's transaction manager.
 *
 * <p>{@link TransactionTemplate} is used rather than {@code @Transactional} because the use cases are
 * plain classes built by hand in a configuration class, not container-managed beans. An annotation
 * on them would do nothing — there is no proxy to honour it — and the failure mode is silent: the
 * code compiles, the transaction simply never opens, and the bug only shows up under a partial
 * failure. The template makes the boundary explicit and impossible to lose by accident.
 *
 * <p>Propagation is the default ({@code REQUIRED}), so nesting is safe: a use case that calls another
 * joins the outer transaction instead of opening a second one. That matters for audit writes, which
 * run in {@code REQUIRES_NEW} and must survive a rolled-back business transaction.
 */
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    public SpringTransactionRunner(TransactionTemplate template) {
        this.template = template;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return template.execute(status -> work.get());
    }

    @Override
    public <T> T executeInNewTransaction(Supplier<T> work) {
        TransactionTemplate independent = new TransactionTemplate(template.getTransactionManager());
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return independent.execute(status -> work.get());
    }
}
