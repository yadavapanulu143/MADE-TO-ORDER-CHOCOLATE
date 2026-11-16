package com.choco.core.port;

import java.util.UUID;

/** Source of identifiers. Kept behind a port so tests can use a predictable sequence. */
@FunctionalInterface
public interface IdGenerator {

    UUID nextId();

    static IdGenerator random() {
        return UUID::randomUUID;
    }
}
