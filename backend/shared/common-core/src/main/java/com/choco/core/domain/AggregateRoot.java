package com.choco.core.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for consistency boundaries. Only an aggregate root may be loaded and saved as a unit,
 * and it is the only place that records domain events.
 */
public abstract class AggregateRoot {

    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> snapshot = List.copyOf(domainEvents);
        domainEvents.clear();
        return snapshot;
    }

    public List<DomainEvent> peekDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
