package com.patrick.fintech.loan_backend.repository;
import com.patrick.fintech.loan_backend.model.WebhookReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;
public interface WebhookReceiptRepository extends JpaRepository<WebhookReceipt,Long> {
    Optional<WebhookReceipt> findByProviderAndEventKey(String provider,String eventKey);
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
