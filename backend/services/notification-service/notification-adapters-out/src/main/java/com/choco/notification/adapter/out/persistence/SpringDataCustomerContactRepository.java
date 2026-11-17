package com.choco.notification.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCustomerContactRepository extends JpaRepository<CustomerContactJpaEntity, UUID> {
}
