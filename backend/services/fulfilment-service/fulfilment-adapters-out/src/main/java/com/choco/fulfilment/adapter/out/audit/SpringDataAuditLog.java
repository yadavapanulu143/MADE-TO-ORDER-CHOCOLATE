package com.choco.fulfilment.adapter.out.audit;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAuditLog extends JpaRepository<AuditLogJpaEntity, Long> {
}
