package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.BorrowerFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BorrowerFileRepository extends JpaRepository<BorrowerFile, Long> {
    List<BorrowerFile> findByBorrowerId(Long borrowerId);

    /**
     * Returns the latest applicant-owned document of a given type. The row is
     * locked so two concurrent repeat applications cannot both create a new
     * "current" document for the same borrower/type.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT f
            FROM BorrowerFile f
            WHERE f.borrower.id = :borrowerId
              AND f.documentType = :documentType
              AND f.uploadedByApplicant = true
            ORDER BY f.uploadedAt DESC, f.id DESC
            """)
    List<BorrowerFile> findLatestApplicantDocumentForUpdate(
            @Param("borrowerId") Long borrowerId,
            @Param("documentType") com.patrick.fintech.loan_backend.model.DocumentType documentType);
}
