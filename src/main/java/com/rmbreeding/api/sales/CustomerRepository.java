package com.rmbreeding.api.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

    List<CustomerEntity> findByTenantIdAndArchivedAtIsNullOrderByName(UUID tenantId);

    List<CustomerEntity> findByTenantIdOrderByName(UUID tenantId);

    Optional<CustomerEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);
}
