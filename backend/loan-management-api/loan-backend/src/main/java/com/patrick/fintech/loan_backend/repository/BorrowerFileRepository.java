package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.BorrowerFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;


public interface BorrowerFileRepository
        extends JpaRepository<BorrowerFile, Long> {

    // ============================================================
    // ALL FILES FOR BORROWER
    // ============================================================
    //
    // Kept for compatibility with existing services.
    //
    List<BorrowerFile> findByBorrowerId(
            Long borrowerId
    );


    // ============================================================
    // ORDERED FILES FOR BORROWER
    // ============================================================
    //
    // Useful when the frontend needs a predictable order.
    //
    List<BorrowerFile> findByBorrowerIdOrderByIdDesc(
            Long borrowerId
    );


    // ============================================================
    // PAGINATED FILES FOR BORROWER
    // ============================================================
    //
    // Prefer this for API endpoints if borrowers can have many
    // uploaded documents.
    //
    Page<BorrowerFile> findByBorrowerIdOrderByIdDesc(
            Long borrowerId,
            Pageable pageable
    );


    // ============================================================
    // COUNT FILES
    // ============================================================

    long countByBorrowerId(
            Long borrowerId
    );
}