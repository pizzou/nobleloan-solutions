package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CurrencyRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRateRepository
        extends JpaRepository<CurrencyRate, Long> {

    // ============================================================
    // EXACT CURRENCY PAIR
    // ============================================================

    /**
     * Finds the exchange rate for an exact currency pair.
     *
     * Example:
     * USD -> RWF
     */
    Optional<CurrencyRate> findByBaseCurrencyAndTargetCurrency(
            String baseCurrency,
            String targetCurrency
    );


    // ============================================================
    // RATES BY BASE CURRENCY
    // ============================================================

    /**
     * Returns all exchange rates originating from the given
     * base currency.
     *
     * Example:
     * USD -> RWF
     * USD -> EUR
     * USD -> GBP
     */
    List<CurrencyRate> findByBaseCurrency(
            String baseCurrency
    );
}