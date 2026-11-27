package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Immutable
@Table(name = "historical_receipt_reallocations")
public class HistoricalReceiptReallocation {
    @Id
    @Column(name = "receipt_id")
    private Long receiptId;

    @Column(name = "journal_entry_id", nullable = false, unique = true)
    private Long journalEntryId;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal penalty;

    @Column(name = "management_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal managementFee;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal interest;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(name = "extension_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal extensionFee;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unapplied;

    @Column(name = "created_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private Instant createdAt;

    protected HistoricalReceiptReallocation() {
    }
}
