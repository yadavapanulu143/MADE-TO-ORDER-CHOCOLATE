package com.choco.reporting.adapter.out.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAuditLog extends JpaRepository<AuditLogJpaEntity, Long> {
}
