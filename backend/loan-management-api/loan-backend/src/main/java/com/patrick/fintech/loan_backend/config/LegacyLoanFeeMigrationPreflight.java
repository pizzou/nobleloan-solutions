package com.patrick.fintech.loan_backend.config;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Compatibility preflight for V104 on databases with legacy loan rows.
 *
 * V87 introduced NOT VALID financial CHECK constraints. PostgreSQL still
 * enforces those constraints on updated rows, and V104 updates every loan to
 * set its new penalty_rate. Before V104 runs, this callback fills nullable
 * financial tracking fields conservatively so the penalty-rate backfill does
 * not fail merely because a legacy nullable fee field was never populated.
 *
 * It does not rewrite a non-null financial value. If existing values are
 * internally inconsistent, the existing database constraints still reject
 * the update and migration fails closed for reconciliation.
 *
 * Historical migration SQL is intentionally left unchanged to preserve its
 * Flyway checksum on deployments that have already applied it.
 */
@Component
public final class LegacyLoanFeeMigrationPreflight implements Callback {
    private static final Logger LOG =
            Logger.getLogger(LegacyLoanFeeMigrationPreflight.class.getName());

    private static final String TARGET_VERSION = "104";

    private static final String BACKFILL_SQL = """
            UPDATE loans
            SET principal_paid = COALESCE(
                        principal_paid,
                        GREATEST(COALESCE(amount, 0) - COALESCE(outstanding_balance, amount, 0), 0)),
                outstanding_balance = COALESCE(
                        outstanding_balance,
                        GREATEST(COALESCE(amount, 0) - COALESCE(principal_paid, 0), 0)),
                total_interest = COALESCE(total_interest, 0),
                interest_paid = COALESCE(interest_paid, 0),
                interest_outstanding = CASE
                    WHEN interest_outstanding IS NULL
                      OR total_interest IS NULL
                    THEN GREATEST(COALESCE(total_interest, 0) - COALESCE(interest_paid, 0), 0)
                    ELSE interest_outstanding
                END,
                management_fee = COALESCE(management_fee, 0),
                management_fee_paid = COALESCE(management_fee_paid, 0),
                management_fee_outstanding = CASE
                    WHEN management_fee_outstanding IS NULL
                      OR management_fee IS NULL
                    THEN GREATEST(COALESCE(management_fee, 0) - COALESCE(management_fee_paid, 0), 0)
                    ELSE management_fee_outstanding
                END,
                application_fee = COALESCE(application_fee, 0),
                application_fee_paid = COALESCE(
                    application_fee_paid,
                    CASE
                        WHEN status IN ('DISBURSED', 'ACTIVE', 'OVERDUE', 'DEFAULTED',
                                        'RESTRUCTURED', 'WRITTEN_OFF', 'PAID', 'CLOSED')
                        THEN COALESCE(application_fee, 0)
                        ELSE 0
                    END)
            WHERE principal_paid IS NULL
               OR outstanding_balance IS NULL
               OR total_interest IS NULL
               OR interest_paid IS NULL
               OR interest_outstanding IS NULL
               OR management_fee IS NULL
               OR management_fee_paid IS NULL
               OR management_fee_outstanding IS NULL
               OR application_fee IS NULL
               OR application_fee_paid IS NULL
            """;

    @Override
    public boolean supports(Event event, Context context) {
        if (event != Event.BEFORE_EACH_MIGRATE || context == null) {
            return false;
        }
        MigrationInfo migration = context.getMigrationInfo();
        return migration != null
                && migration.getVersion() != null
                && TARGET_VERSION.equals(migration.getVersion().getVersion());
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) throws SQLException {
        try (PreparedStatement statement =
                     context.getConnection().prepareStatement(BACKFILL_SQL)) {
            int updated = statement.executeUpdate();
            if (updated > 0) {
                LOG.warning("V104 compatibility preflight normalized nullable financial tracking fields on "
                        + updated + " legacy loan row(s); non-null financial values were preserved.");
            }
        }
    }

    @Override
    public String getCallbackName() {
        return "legacy-loan-fee-migration-preflight";
    }
}
