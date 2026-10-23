package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.BorrowerFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BorrowerFileRepository extends JpaRepository<BorrowerFile, Long> {
        List<BorrowerFile> findByBorrowerId(Long borrowerId);

        // Only the latest borrower-level copy determines second-loan KYC eligibility.
        // A rejected newer document must not be bypassed by an old verified file.
        Optional<BorrowerFile> findFirstByBorrowerIdAndDocumentTypeAndLoanIsNullOrderByUploadedAtDescIdDesc(
                        Long borrowerId, com.patrick.fintech.loan_backend.model.DocumentType documentType);

        List<BorrowerFile> findByLoan_IdAndDocumentTypeOrderByUploadedAtDesc(
                        Long loanId, com.patrick.fintech.loan_backend.model.DocumentType documentType);

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
