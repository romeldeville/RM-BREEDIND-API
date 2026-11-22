package com.rmbreeding.api.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    List<PaymentEntity> findByTenantIdAndSaleIdOrderByPaidAtAsc(UUID tenantId, UUID saleId);
}
