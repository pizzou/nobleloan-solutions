package com.patrick.fintech.loan_backend.repository;
import com.patrick.fintech.loan_backend.model.PaymentSettlement; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface PaymentSettlementRepository extends JpaRepository<PaymentSettlement,Long>{Optional<PaymentSettlement> findByOrganization_IdAndProviderAndProviderReference(Long org,String provider,String ref);List<PaymentSettlement> findByOrganization_IdAndStatusOrderBySettlementDateAsc(Long org,String status);}
