package com.patrick.fintech.loan_backend.repository;

import java.math.BigDecimal;

/**
 * Account-level ledger totals used by the BNR financial statement.
 *
 * Spring Data maps the native-query aliases to these projection getters.
 * The projection deliberately contains no entity relationships so the BNR
 * report does not hydrate the complete historical accounting ledger.
 */
public interface BnrLedgerAggregateProjection {

    Long getAccountId();

    BigDecimal getHistoricalDebit();

    BigDecimal getHistoricalCredit();

    BigDecimal getPeriodDebit();

    BigDecimal getPeriodCredit();
}
